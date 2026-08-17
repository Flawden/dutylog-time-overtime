package ru.daniil.shifts.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.dto.Dtos.ActualWorkIntervalRequest;
import ru.daniil.shifts.dto.Dtos.OvertimeCreditCreateRequest;
import ru.daniil.shifts.dto.Dtos.OvertimeUsageCreateRequest;
import ru.daniil.shifts.dto.Dtos.ProductionCalendarDayUpdateRequest;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.DayEntry;
import ru.daniil.shifts.model.ShiftType;
import ru.daniil.shifts.repo.DayEntryRepository;
import ru.daniil.shifts.repo.OvertimeCreditRepository;
import ru.daniil.shifts.repo.ShiftTypeRepository;
import ru.daniil.shifts.repo.UserRepository;
import ru.daniil.shifts.service.exception.ApiException;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class NativeWorkdayClosedLoopServiceTest {

    @Autowired UserRepository users;
    @Autowired ShiftTypeRepository shifts;
    @Autowired DayEntryRepository days;
    @Autowired ActualWorkService actualWork;
    @Autowired OvertimeService overtime;
    @Autowired OvertimeCreditRepository credits;
    @Autowired ProductionCalendarService productionCalendar;
    @Autowired WorkdayTruthService workdayTruth;

    AppUser owner;
    ShiftType day;
    LocalDate date;

    @BeforeEach
    void setUp() {
        owner = users.save(new AppUser("native-workday-closed-loop-owner", "{noop}unused"));
        day = shifts.save(new ShiftType(owner, "День", 8.0, "#123456", false,
                LocalTime.of(8, 30), LocalTime.of(17, 0), 30, 8.0));
        date = LocalDate.parse("2026-08-18");
        DayEntry entry = new DayEntry(owner, date);
        entry.setShiftType(day);
        days.saveAndFlush(entry);
    }

    @Test
    void firstActualIntervalInheritsShiftBreakAndPostsOnlyNetOvertime() {
        var saved = actualWork.create(owner,
                new ActualWorkIntervalRequest(date.toString(), "08:30", "18:00", null));

        assertEquals(30, saved.breakMinutes());
        assertEquals(540, saved.workedMinutes());
        assertEquals(60, overtime.balanceMinutes(owner));

        var system = credits.findByOwnerAndWorkDateAndSourceKind(owner, date, "SYSTEM_ACTUAL_WORK").orElseThrow();
        assertEquals(60, system.getCreditedMinutes());
        assertTrue(system.isSystemActualWorkDerived());

        var truth = workdayTruth.truth(owner, date);
        assertEquals(30, truth.scheduledBreakMinutes());
        assertEquals(540, truth.actualMinutes());
        assertEquals(60, truth.overtimeEarnedMinutes());
    }

    @Test
    void editingFactReconcilesOneSystemCreditInsteadOfDuplicatingIt() {
        var saved = actualWork.create(owner,
                new ActualWorkIntervalRequest(date.toString(), "08:30", "18:00", null));

        actualWork.update(owner, saved.id(),
                new ActualWorkIntervalRequest(date.toString(), "08:30", "19:00", 30, "задержался"));

        assertEquals(120, overtime.balanceMinutes(owner));
        var rows = credits.findByOwnerAndWorkDateOrderByIdAsc(owner, date);
        assertEquals(1, rows.size());
        assertTrue(rows.get(0).isSystemActualWorkDerived());
        assertEquals(120, rows.get(0).getCreditedMinutes());
    }

    @Test
    void deletingFactRemovesUnusedDerivedCreditAndRestoresBank() {
        var saved = actualWork.create(owner,
                new ActualWorkIntervalRequest(date.toString(), "08:30", "18:00", null));
        assertEquals(60, overtime.balanceMinutes(owner));

        actualWork.delete(owner, saved.id());

        assertEquals(0, overtime.balanceMinutes(owner));
        assertTrue(credits.findByOwnerAndWorkDateOrderByIdAsc(owner, date).isEmpty());
        assertFalse(workdayTruth.truth(owner, date).explicitActual());
    }

    @Test
    void holidayActualWorkDoesNotBecomeOrdinaryTimeBankCredit() {
        productionCalendar.upsertLocal(owner, date.toString(),
                new ProductionCalendarDayUpdateRequest("HOLIDAY", "NORM_OVERRIDE", 0, "HOLIDAY", "Праздник"));

        actualWork.create(owner,
                new ActualWorkIntervalRequest(date.toString(), "08:30", "17:00", 30, "работал в праздник"));

        assertEquals(0, overtime.balanceMinutes(owner));
        assertTrue(credits.findByOwnerAndWorkDateOrderByIdAsc(owner, date).isEmpty());
        assertEquals("HOLIDAY", workdayTruth.truth(owner, date).productionCalendar().payrollEffect());
    }


    @Test
    void crossMidnightActualSplitsNetMinutesAcrossCalendarDatesAndBankCredits() {
        LocalDate next = date.plusDays(1);
        DayEntry nextEntry = new DayEntry(owner, next);
        nextEntry.setShiftType(day);
        days.saveAndFlush(nextEntry);

        var saved = actualWork.create(owner,
                new ActualWorkIntervalRequest(date.toString(), next.toString(), "08:30", "08:30", 30, "суточный факт"));

        assertEquals(next.toString(), saved.endDate());
        assertEquals(1410, saved.workedMinutes());
        assertEquals(450, overtime.balanceMinutes(owner));
        assertEquals(420, credits.findByOwnerAndWorkDateAndSourceKind(owner, date, "SYSTEM_ACTUAL_WORK").orElseThrow().getCreditedMinutes());
        assertEquals(30, credits.findByOwnerAndWorkDateAndSourceKind(owner, next, "SYSTEM_ACTUAL_WORK").orElseThrow().getCreditedMinutes());
        assertEquals(900, workdayTruth.truth(owner, date).actualMinutes());
        assertEquals(510, workdayTruth.truth(owner, next).actualMinutes());
        assertTrue(workdayTruth.truth(owner, next).explicitActual());
    }

    @Test
    void endBeforeStartWithoutExplicitEndDateInfersNextDay() {
        var saved = actualWork.create(owner,
                new ActualWorkIntervalRequest(date.toString(), "17:00", "08:00", 0, "ночной факт"));

        assertEquals(date.plusDays(1).toString(), saved.endDate());
        assertEquals(900, saved.workedMinutes());
        assertTrue(workdayTruth.truth(owner, date.plusDays(1)).explicitActual());
    }

    @Test
    void deletingCrossMidnightFactReconcilesAllAffectedDates() {
        LocalDate next = date.plusDays(1);
        DayEntry nextEntry = new DayEntry(owner, next);
        nextEntry.setShiftType(day);
        days.saveAndFlush(nextEntry);
        var saved = actualWork.create(owner,
                new ActualWorkIntervalRequest(date.toString(), next.toString(), "08:30", "08:30", 30, null));
        assertEquals(450, overtime.balanceMinutes(owner));

        actualWork.delete(owner, saved.id());

        assertEquals(0, overtime.balanceMinutes(owner));
        assertTrue(credits.findByOwnerAndWorkDateOrderByIdAsc(owner, date).isEmpty());
        assertTrue(credits.findByOwnerAndWorkDateOrderByIdAsc(owner, next).isEmpty());
        assertFalse(workdayTruth.truth(owner, next).explicitActual());
    }

    @Test
    void systemActualWorkCreditRowsAreReadOnlyAndExposeProvenance() {
        actualWork.create(owner,
                new ActualWorkIntervalRequest(date.toString(), "08:30", "18:00", null));

        var row = overtime.account(owner).credits().stream()
                .filter(item -> date.toString().equals(item.workedDate()))
                .findFirst().orElseThrow();
        assertEquals("SYSTEM_ACTUAL_WORK", row.sourceKind());
        assertFalse(row.editable());
    }

    @Test
    void usedDerivedCreditBlocksFactDeletionInsteadOfCorruptingFifo() {
        var saved = actualWork.create(owner,
                new ActualWorkIntervalRequest(date.toString(), "08:30", "19:00", 30, null));
        overtime.createUsage(owner, new OvertimeUsageCreateRequest("2026-08-20", 1.0, "отгул"));

        ApiException error = assertThrows(ApiException.class, () -> actualWork.delete(owner, saved.id()));
        assertEquals("DERIVED_OVERTIME_ALREADY_USED", error.getCode());
    }
}
