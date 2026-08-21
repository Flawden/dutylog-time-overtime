package ru.daniil.shifts.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.util.ReflectionTestUtils;
import ru.daniil.shifts.dto.Dtos.ActualWorkIntervalRequest;
import ru.daniil.shifts.dto.Dtos.PayrollCompensationComponentCreateRequest;
import ru.daniil.shifts.dto.Dtos.PayrollCompensationComponentVersionRequest;
import ru.daniil.shifts.dto.Dtos.PayrollCompensationTermRequest;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.DayEntry;
import ru.daniil.shifts.model.ShiftType;
import ru.daniil.shifts.repo.DayEntryRepository;
import ru.daniil.shifts.repo.PayrollSnapshotComponentLineRepository;
import ru.daniil.shifts.repo.PayrollSnapshotRepository;
import ru.daniil.shifts.repo.ShiftTypeRepository;
import ru.daniil.shifts.repo.UserRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class PayrollCompensationComponentPayrollIntegrationTest {

    @Autowired
    UserRepository users;

    @Autowired
    ShiftTypeRepository shifts;

    @Autowired
    DayEntryRepository days;

    @Autowired
    ActualWorkService actualWork;

    @Autowired
    LedgerIntegrityService ledger;

    @Autowired
    PayrollService payroll;

    @Autowired
    CompensationComponentConfigurationService components;

    @Autowired
    PayrollSnapshotRepository payrollSnapshots;

    @Autowired
    PayrollSnapshotComponentLineRepository frozenLines;

    AppUser owner;

    @BeforeEach
    void setUp() {
        owner =
                users.saveAndFlush(
                        new AppUser(
                                "pc-" + UUID.randomUUID().toString().substring(0, 8),
                                "{noop}unused"
                        )
                );

        owner.setWorkTimezone(
                "Europe/Chisinau"
        );

        owner.setDisplayTimezone(
                "Europe/Chisinau"
        );

        users.saveAndFlush(
                owner
        );
    }

    @Test
    void earnedBaseComponentFlowsThroughPreviewTotalHashAndFrozenSnapshotLines() {
        prepareRegularDay();

        payroll.upsertCompensationTerm(
                owner,
                "2026-08",
                new PayrollCompensationTermRequest(
                        "HOURLY",
                        "rub",
                        100_000L,
                        null
                )
        );

        var created =
                components.create(
                        owner,
                        new PayrollCompensationComponentCreateRequest(
                                "2026-08",
                                percent(
                                        "Премия за выживание после ночной смены",
                                        400,
                                        true
                                )
                        )
                );

        var open =
                payroll.period(
                        owner,
                        "2026-08"
                );

        assertEquals(
                800_000L,
                open.preview()
                        .basePayMinor()
        );

        assertTrue(
                open.preview()
                        .compensationComponentCalculationReady()
        );

        assertEquals(
                1,
                open.preview()
                        .compensationComponentCount()
        );

        assertEquals(
                32_000L,
                open.preview()
                        .compensationComponentEarningsMinor()
        );

        assertEquals(
                0L,
                open.preview()
                        .additionsMinor()
        );

        assertEquals(
                832_000L,
                open.preview()
                        .totalPayMinor()
        );

        var previewLine =
                open.preview()
                        .compensationComponentLines()
                        .get(0);

        assertEquals(
                800_000L,
                previewLine.referenceBaseMinor()
        );

        assertEquals(
                32_000L,
                previewLine.amountMinor()
        );

        assertEquals(
                "Премия за выживание после ночной смены",
                previewLine.displayName()
        );

        ledger.closePeriod(
                owner,
                "2026-08"
        );

        var first =
                payroll.calculate(
                        owner,
                        "2026-08"
                );

        assertEquals(
                1,
                first.compensationComponentCount()
        );

        assertEquals(
                32_000L,
                first.compensationComponentEarningsMinor()
        );

        assertEquals(
                832_000L,
                first.totalPayMinor()
        );

        assertNotNull(
                first.compensationComponentFingerprint()
        );

        assertEquals(
                64,
                first.compensationComponentFingerprint()
                        .length()
        );

        assertEquals(
                1,
                first.compensationComponentLines()
                        .size()
        );

        String firstHash =
                first.calculationHash();

        components.upsertVersion(
                owner,
                created.componentId(),
                "2026-08",
                percent(
                        "Премия за выживание после ночной смены v2",
                        600,
                        true
                )
        );

        var second =
                payroll.calculate(
                        owner,
                        "2026-08"
                );

        assertEquals(
                48_000L,
                second.compensationComponentEarningsMinor()
        );

        assertEquals(
                848_000L,
                second.totalPayMinor()
        );

        assertNotEquals(
                firstHash,
                second.calculationHash()
        );

        var period =
                payroll.period(
                        owner,
                        "2026-08"
                );

        assertEquals(
                2,
                period.snapshots()
                        .size()
        );

        var latest =
                period.snapshots()
                        .get(0);

        var historical =
                period.snapshots()
                        .get(1);

        assertEquals(
                "Премия за выживание после ночной смены v2",
                latest.compensationComponentLines()
                        .get(0)
                        .displayName()
        );

        assertEquals(
                48_000L,
                latest.compensationComponentLines()
                        .get(0)
                        .amountMinor()
        );

        /*
         * Mutable config changed, but revision 1 still explains exactly the
         * 4% projection that created it.
         */
        assertEquals(
                "Премия за выживание после ночной смены",
                historical.compensationComponentLines()
                        .get(0)
                        .displayName()
        );

        assertEquals(
                400,
                historical.compensationComponentLines()
                        .get(0)
                        .rateBps()
        );

        assertEquals(
                32_000L,
                historical.compensationComponentLines()
                        .get(0)
                        .amountMinor()
        );
    }

    @Test
    void disabledComponentDoesNotLeakIntoMoneyOrManualAdditions() {
        prepareRegularDay();

        payroll.upsertCompensationTerm(
                owner,
                "2026-08",
                new PayrollCompensationTermRequest(
                        "HOURLY",
                        "rub",
                        100_000L,
                        null
                )
        );

        components.create(
                owner,
                new PayrollCompensationComponentCreateRequest(
                        "2026-08",
                        new PayrollCompensationComponentVersionRequest(
                                "Выключенный фикс",
                                "FIXED_AMOUNT",
                                null,
                                null,
                                50_000L,
                                "rub",
                                false
                        )
                )
        );

        var period =
                payroll.period(
                        owner,
                        "2026-08"
                );

        assertTrue(
                period.preview()
                        .compensationComponentCalculationReady()
        );

        assertEquals(
                0,
                period.preview()
                        .compensationComponentCount()
        );

        assertEquals(
                0L,
                period.preview()
                        .compensationComponentEarningsMinor()
        );

        assertNull(
                period.preview()
                        .compensationComponentFingerprint()
        );

        assertTrue(
                period.preview()
                        .compensationComponentLines()
                        .isEmpty()
        );

        assertEquals(
                0L,
                period.preview()
                        .additionsMinor()
        );

        assertEquals(
                800_000L,
                period.preview()
                        .totalPayMinor()
        );
    }


    @Test
    void snapshotReadFailsClosedWhenFrozenComponentCountDiverges() {
        calculateOneComponentSnapshot(
                "Count integrity component"
        );

        var snapshot =
                payrollSnapshots
                        .findFirstByOwnerAndPeriodMonthOrderByRevisionDesc(
                                owner,
                                LocalDate.of(
                                        2026,
                                        8,
                                        1
                                )
                        )
                        .orElseThrow();

        var lines =
                frozenLines
                        .findBySnapshotOrderByLineIndexAsc(
                                snapshot
                        );

        assertEquals(
                1,
                lines.size()
        );

        frozenLines.delete(
                lines.get(0)
        );

        frozenLines.flush();

        IllegalStateException error =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                payroll.period(
                                        owner,
                                        "2026-08"
                                )
                );

        assertTrue(
                error.getMessage()
                        .contains(
                                "line count"
                        )
        );
    }

    @Test
    void snapshotReadFailsClosedWhenFrozenComponentMoneyDiverges() {
        calculateOneComponentSnapshot(
                "Money integrity component"
        );

        var snapshot =
                payrollSnapshots
                        .findFirstByOwnerAndPeriodMonthOrderByRevisionDesc(
                                owner,
                                LocalDate.of(
                                        2026,
                                        8,
                                        1
                                )
                        )
                        .orElseThrow();

        var lines =
                frozenLines
                        .findBySnapshotOrderByLineIndexAsc(
                                snapshot
                        );

        assertEquals(
                1,
                lines.size()
        );

        var line =
                lines.get(0);

        ReflectionTestUtils.setField(
                line,
                "amountMinor",
                line.getAmountMinor() + 1L
        );

        frozenLines.saveAndFlush(
                line
        );

        IllegalStateException error =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                payroll.period(
                                        owner,
                                        "2026-08"
                                )
                );

        assertTrue(
                error.getMessage()
                        .contains(
                                "earnings"
                        )
        );
    }

    private void calculateOneComponentSnapshot(
            String name
    ) {
        prepareRegularDay();

        payroll.upsertCompensationTerm(
                owner,
                "2026-08",
                new PayrollCompensationTermRequest(
                        "HOURLY",
                        "rub",
                        100_000L,
                        null
                )
        );

        components.create(
                owner,
                new PayrollCompensationComponentCreateRequest(
                        "2026-08",
                        percent(
                                name,
                                400,
                                true
                        )
                )
        );

        ledger.closePeriod(
                owner,
                "2026-08"
        );

        payroll.calculate(
                owner,
                "2026-08"
        );
    }

    private void prepareRegularDay() {
        LocalDate workDate =
                LocalDate.of(
                        2026,
                        8,
                        3
                );

        ShiftType day =
                shifts.saveAndFlush(
                        new ShiftType(
                                owner,
                                "Payroll generic component regular day",
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
    }

    private PayrollCompensationComponentVersionRequest percent(
            String name,
            int rateBps,
            boolean enabled
    ) {
        return new PayrollCompensationComponentVersionRequest(
                name,
                "PERCENT_OF_BASE",
                "EARNED_BASE_PAY",
                rateBps,
                null,
                null,
                enabled
        );
    }
}
