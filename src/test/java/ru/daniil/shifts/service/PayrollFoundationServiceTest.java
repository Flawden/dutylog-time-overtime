package ru.daniil.shifts.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.dto.Dtos.*;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.DayEntry;
import ru.daniil.shifts.model.ShiftType;
import ru.daniil.shifts.repo.DayEntryRepository;
import ru.daniil.shifts.repo.ShiftTypeRepository;
import ru.daniil.shifts.repo.UserRepository;
import ru.daniil.shifts.service.exception.ApiException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class PayrollFoundationServiceTest {

    @Autowired UserRepository users;
    @Autowired ShiftTypeRepository shifts;
    @Autowired DayEntryRepository days;
    @Autowired ActualWorkService actualWork;
    @Autowired OvertimeService overtime;
    @Autowired OvertimeSettlementService settlements;
    @Autowired LedgerIntegrityService ledger;
    @Autowired PayrollService payroll;

    AppUser owner;

    @BeforeEach
    void setUp() {
        owner = users.save(new AppUser("payroll-foundation-owner", "{noop}unused"));
        owner.setWorkTimezone("Europe/Chisinau");
        owner.setDisplayTimezone("Europe/Chisinau");
        users.save(owner);
    }

    @Test
    void closedHealthyPeriodCreatesImmutableVersionedMoneySnapshots() {
        LocalDate workDate =
                LocalDate.of(
                        2026,
                        8,
                        3
                );

        ShiftType day =
                shifts.save(
                        new ShiftType(
                                owner,
                                "Payroll regular day",
                                8.0,
                                "#123456",
                                false,
                                LocalTime.of(
                                        8,
                                        30
                                ),
                                LocalTime.of(
                                        17,
                                        0
                                ),
                                30,
                                8.0
                        )
                );

        DayEntry entry =
                new DayEntry(
                        owner,
                        workDate
                );

        entry.setShiftType(
                day
        );

        days.saveAndFlush(
                entry
        );

        actualWork.create(
                owner,
                new ActualWorkIntervalRequest(
                        workDate.toString(),
                        "08:30",
                        "17:00",
                        null
                )
        );

        /*
         * This fixture represents 8h of REGULAR factual work.
         * It must not create a Time Bank credit.
         */
        assertEquals(
                0,
                overtime.balanceMinutes(
                        owner
                )
        );

        payroll.upsertCompensationTerm(owner, "2026-08", new PayrollCompensationTermRequest("HOURLY", "rub", 100_000L, null));

        PayrollPeriodDto open = payroll.period(owner, "2026-08");
        assertFalse(open.periodClosed());
        assertFalse(open.canCalculate());
        assertEquals("PERIOD_OPEN", open.blockingReason());
        assertEquals(480, open.preview().workedMinutes());
        assertEquals(480, open.preview().hourlyBasePayableMinutes());
        assertEquals(800_000L, open.preview().basePayMinor());
        assertTrue(open.preview().settlementPricingReady());
        assertEquals(0, open.preview().settlementCount());
        assertEquals(0L, open.preview().settlementPayMinor());

        assertTrue(
                open.preview().ordinaryPremiumPricingReady()
        );
        assertFalse(
                open.preview().ordinaryPremiumPricingIdentityRequired()
        );
        assertEquals(
                0L,
                open.preview().ordinaryPremiumPayMinor()
        );

        ledger.closePeriod(owner, "2026-08");
        PayrollSnapshotDto first = payroll.calculate(owner, "2026-08");
        assertEquals(1, first.revision());
        assertEquals("RUB", first.currencyCode());
        assertEquals(480, first.workedMinutes());
        assertEquals(0, first.paidAbsenceMinutes());
        assertEquals(480, first.payableMinutes());
        assertEquals(480, first.hourlyBasePayableMinutes());
        assertEquals(800_000L, first.basePayMinor());
        assertEquals(0, first.settlementCount());
        assertEquals(0, first.settlementMinutes());
        assertEquals(0L, first.settlementBasePayMinor());
        assertEquals(0L, first.settlementPremiumPayMinor());
        assertEquals(0L, first.settlementPayMinor());
        assertNull(first.settlementPricingFingerprint());
        assertEquals(800_000L, first.totalPayMinor());
        assertEquals(64, first.calculationHash().length());
        assertNull(first.supersededById());

        payroll.addAdjustment(owner, new PayrollAdjustmentRequest(
                "2026-08", "ADDITION", 50_000L, "Премия", "Ручное начисление"));
        payroll.addAdjustment(owner, new PayrollAdjustmentRequest(
                "2026-08", "DEDUCTION", 10_000L, "Удержание", null));
        PayrollSnapshotDto second = payroll.calculate(owner, "2026-08");
        assertEquals(2, second.revision());
        assertEquals(480, second.hourlyBasePayableMinutes());
        assertEquals(0, second.settlementCount());
        assertEquals(0L, second.settlementPayMinor());
        assertEquals(50_000L, second.additionsMinor());
        assertEquals(10_000L, second.deductionsMinor());
        assertEquals(840_000L, second.totalPayMinor());
        assertNotEquals(first.calculationHash(), second.calculationHash());

        PayrollPeriodDto result = payroll.period(owner, "2026-08");
        assertTrue(result.periodClosed());
        assertTrue(result.integrityHealthy());
        assertTrue(result.canCalculate());
        assertNull(result.blockingReason());
        assertEquals(second.id(), result.latestSnapshot().id());
        assertEquals(2, result.snapshots().size());
        PayrollSnapshotDto older = result.snapshots().stream()
                .filter(item -> item.revision() == 1).findFirst().orElseThrow();
        assertEquals(second.id(), older.supersededById());
    }

    @Test
    void unpriceableSettlementBlocksFinalPayrollWithoutBreakingPeriodRead() {
        overtime.createCredit(
                owner,
                new OvertimeCreditCreateRequest(
                        "2026-08-03",
                        null,
                        null,
                        null,
                        null,
                        null,
                        2.0,
                        "Manual legacy-style source"
                )
        );

        settlements.create(
                owner,
                LocalDate.of(
                        2026,
                        8,
                        5
                ),
                60,
                "К оплате"
        );

        payroll.upsertCompensationTerm(
                owner,
                "2026-08",
                new PayrollCompensationTermRequest(
                        "HOURLY",
                        "RUB",
                        100_000L,
                        null
                )
        );

        /*
         * GET remains readable even though manual credit provenance cannot be
         * priced automatically.
         */
        PayrollPeriodDto open =
                payroll.period(
                        owner,
                        "2026-08"
                );

        assertFalse(
                open.preview()
                        .settlementPricingReady()
        );

        assertEquals(
                "PAY_PRICING_PROVENANCE_REQUIRED",
                open.preview()
                        .settlementPricingBlockingReason()
        );

        assertEquals(
                "PERIOD_OPEN",
                open.blockingReason()
        );

        ledger.closePeriod(
                owner,
                "2026-08"
        );

        PayrollPeriodDto closed =
                payroll.period(
                        owner,
                        "2026-08"
                );

        assertFalse(
                closed.canCalculate()
        );

        assertEquals(
                "PAY_PRICING_PROVENANCE_REQUIRED",
                closed.blockingReason()
        );

        ApiException blocked =
                assertThrows(
                        ApiException.class,
                        () ->
                                payroll.calculate(
                                        owner,
                                        "2026-08"
                                )
                );

        assertEquals(
                "PAY_PRICING_PROVENANCE_REQUIRED",
                blocked.getCode()
        );
    }

    @Test
    void openPeriodCannotBeCalculatedOrReceiveMoneyAdjustments() {
        payroll.upsertCompensationTerm(owner, "2000-01",
                new PayrollCompensationTermRequest("HOURLY", "EUR", 1_500L, null));
        payroll.updateSettings(owner, new PayrollSettingsUpdateRequest("EUR", 2_500L));

        assertEquals(1_500L, payroll.period(owner, "2000-01").effectiveCompensation().hourlyRateMinor());
        YearMonth currentMonth = YearMonth.now(ZoneId.of(owner.getWorkTimezone()));
        assertEquals(2_500L, payroll.period(owner, currentMonth.toString()).effectiveCompensation().hourlyRateMinor());

        ApiException calculateBlocked = assertThrows(ApiException.class,
                () -> payroll.calculate(owner, "2026-09"));
        assertEquals("PERIOD_NOT_CLOSED", calculateBlocked.getCode());

        ApiException adjustmentBlocked = assertThrows(ApiException.class,
                () -> payroll.addAdjustment(owner, new PayrollAdjustmentRequest(
                        "2026-09", "ADDITION", 1_000L, "Премия", null)));
        assertEquals("PERIOD_NOT_CLOSED", adjustmentBlocked.getCode());
    }
}
