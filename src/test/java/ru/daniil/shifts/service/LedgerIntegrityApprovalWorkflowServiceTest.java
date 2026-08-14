package ru.daniil.shifts.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.dto.Dtos.*;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.repo.UserRepository;
import ru.daniil.shifts.service.exception.ApiException;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class LedgerIntegrityApprovalWorkflowServiceTest {

    @Autowired UserRepository users;
    @Autowired VacationPlannerService planner;
    @Autowired OvertimeService overtime;
    @Autowired LedgerIntegrityService ledger;
    @Autowired ActualWorkService actualWork;
    @Autowired TimeCompensationService compensation;
    @Autowired DayEntryService dayEntries;
    @Autowired ShiftTypeService shiftTypes;

    AppUser owner;
    AbsenceTypeDto timeOff;
    AbsenceTypeDto unpaid;

    @BeforeEach
    void setUp() {
        owner = users.save(new AppUser("ledger-workflow-owner", "{noop}unused"));
        owner.setWorkTimezone("Europe/Chisinau");
        owner.setDisplayTimezone("Europe/Chisinau");
        users.save(owner);
        timeOff = planner.types(owner).stream()
                .filter(type -> "TIME_OFF".equals(type.systemCode())).findFirst().orElseThrow();
        unpaid = planner.types(owner).stream()
                .filter(type -> "UNPAID".equals(type.systemCode())).findFirst().orElseThrow();
    }

    @Test
    void draftReservePostAndCancelKeepOneReversibleOvertimeSource() {
        overtime.createCredit(owner, new OvertimeCreditCreateRequest(
                "2026-08-01", null, null, null, null, null, 8.0, "Банк для workflow"));

        AbsencePeriodDto draft = planner.createPeriod(owner, request("DRAFT"));
        assertNull(draft.linkedOvertimeUsageId());
        assertTrue(planner.occurrences(owner, LocalDate.parse("2026-08-06"), LocalDate.parse("2026-08-06")).isEmpty());
        assertEquals(8.0, overtime.account(owner).balanceHours(), 0.001);

        AbsencePeriodDto reserved = planner.updatePeriod(owner, draft.id(), status("SUBMITTED"));
        assertNotNull(reserved.linkedOvertimeUsageId());
        Long usageId = reserved.linkedOvertimeUsageId();
        OvertimeUsageDto reservedUsage = overtime.account(owner).usages().stream()
                .filter(item -> usageId.equals(item.id())).findFirst().orElseThrow();
        assertEquals("RESERVED", reservedUsage.postingState());
        assertTrue(reservedUsage.reserved());
        assertEquals(6.0, overtime.account(owner).balanceHours(), 0.001);

        AbsencePeriodDto posted = planner.updatePeriod(owner, draft.id(), status("APPROVED"));
        assertEquals(usageId, posted.linkedOvertimeUsageId());
        OvertimeUsageDto postedUsage = overtime.account(owner).usages().stream()
                .filter(item -> usageId.equals(item.id())).findFirst().orElseThrow();
        assertEquals("POSTED", postedUsage.postingState());
        assertFalse(postedUsage.reserved());

        AbsencePeriodDto cancelled = planner.updatePeriod(owner, draft.id(), status("CANCELLED"));
        assertNull(cancelled.linkedOvertimeUsageId());
        assertEquals(8.0, overtime.account(owner).balanceHours(), 0.001);
        assertTrue(planner.occurrences(owner, LocalDate.parse("2026-08-06"), LocalDate.parse("2026-08-06")).isEmpty());

        LedgerIntegrityDto integrity = ledger.inspect(owner, LocalDate.parse("2026-08-01"), LocalDate.parse("2026-08-31"));
        assertTrue(integrity.healthy());
        assertTrue(integrity.entries().stream().anyMatch(item -> "ABSENCE_RESERVATION".equals(item.entryKind())));
        assertTrue(integrity.entries().stream().anyMatch(item -> "ABSENCE_POSTING".equals(item.entryKind())));
        assertTrue(integrity.entries().stream().anyMatch(item -> "REVERSED".equals(item.postingState())));
    }

    @Test
    void completedUnpaidAbsenceKeepsPostedZeroMinuteAuditWithoutIntegrityFalsePositive() {
        AbsencePeriodDto period = planner.createPeriod(owner, new AbsencePeriodCreateRequest(
                unpaid.id(), "Без содержания", "2026-08-07", "2026-08-07",
                "COMPLETED", null, "FULL_DAY", null, null, "UNPAID"));

        LedgerIntegrityDto integrity = ledger.inspect(
                owner, LocalDate.parse("2026-08-01"), LocalDate.parse("2026-08-31"));

        assertTrue(integrity.healthy());
        assertTrue(integrity.entries().stream().anyMatch(item ->
                period.id().equals(item.sourceId())
                        && "ABSENCE_POSTING".equals(item.entryKind())
                        && "POSTED".equals(item.postingState())
                        && item.signedMinutes() == 0));
        assertFalse(integrity.issues().stream()
                .anyMatch(item -> "INACTIVE_ABSENCE_HAS_ACTIVE_AUDIT".equals(item.code())));
    }

    @Test
    void closedPeriodRejectsSilentMutationButAllowsAppendOnlyAdjustment() {
        AccountingPeriodDto closed = ledger.closePeriod(owner, "2026-08");
        assertEquals("CLOSED", closed.status());

        ApiException blocked = assertThrows(ApiException.class, () -> actualWork.create(owner,
                new ActualWorkIntervalRequest("2026-08-10", "08:00", "12:00", "После закрытия")));
        assertEquals("PERIOD_CLOSED", blocked.getCode());
        ApiException overtimeBlocked = assertThrows(ApiException.class, () -> overtime.createCredit(owner,
                new OvertimeCreditCreateRequest("2026-08-10", null, null, null, null, null, 2.0, "После закрытия")));
        assertEquals("PERIOD_CLOSED", overtimeBlocked.getCode());

        ShiftTypeDto dayShift = shiftTypes.list(owner).stream()
                .filter(item -> "Дневная".equals(item.name())).findFirst().orElseThrow();
        ApiException shiftBlocked = assertThrows(ApiException.class, () -> dayEntries.upsert(owner, "2026-08-10",
                new DayUpsertRequest(dayShift.id(), null, null, null, null)));
        assertEquals("PERIOD_CLOSED", shiftBlocked.getCode());
        DayDto noteOnly = dayEntries.upsert(owner, "2026-08-10",
                new DayUpsertRequest(null, "Примечание после закрытия", null, null, null));
        assertEquals("Примечание после закрытия", noteOnly.note());

        TimeLedgerEntryDto correction = ledger.addClosedPeriodAdjustment(owner,
                new LedgerAdjustmentRequest("2026-08", -120, "Поздно найденное отсутствие"));
        assertEquals("MANUAL_ADJUSTMENT", correction.entryKind());
        assertEquals(-120, correction.signedMinutes());

        AccountingPeriodDto open = ledger.reopenPeriod(owner, "2026-08");
        assertEquals("OPEN", open.status());
        ActualWorkIntervalDto interval = actualWork.create(owner,
                new ActualWorkIntervalRequest("2026-08-10", "08:00", "12:00", "Фактическая работа"));
        assertEquals(240, interval.workedMinutes());

        TimeCompensationSummaryDto summary = compensation.summary(
                owner, LocalDate.parse("2026-08-01"), LocalDate.parse("2026-08-31"));
        TimeCompensationDayDto day = summary.days().stream()
                .filter(item -> "2026-08-10".equals(item.date())).findFirst().orElseThrow();
        assertEquals("EXPLICIT", day.actualSource());
        assertEquals(240, day.workedMinutes());
        assertEquals(java.util.List.of(interval.id()), day.actualWorkIntervalIds());
        assertFalse(summary.periodClosed());
    }

    private AbsencePeriodCreateRequest request(String status) {
        return new AbsencePeriodCreateRequest(timeOff.id(), "Workflow отгул", "2026-08-06", "2026-08-06",
                status, null, "PARTIAL", "09:00", "11:00", "OVERTIME_BANK");
    }

    private AbsencePeriodUpdateRequest status(String value) {
        return new AbsencePeriodUpdateRequest(null, null, null, null, value, null,
                null, null, null, null, null, null, null);
    }
}
