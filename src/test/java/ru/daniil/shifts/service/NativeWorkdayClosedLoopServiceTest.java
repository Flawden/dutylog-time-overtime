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
import ru.daniil.shifts.repo.OvertimeCreditSliceRepository;
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
    @Autowired OvertimeCreditSliceRepository creditSlices;
    @Autowired ProductionCalendarService productionCalendar;
    @Autowired WorkdayTruthService workdayTruth;
    @Autowired TimeCompensationService timeCompensation;

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
    void payrollSourceKeepsBankedOvertimeOutOfHourlyBase() {
        actualWork.create(
                owner,
                new ActualWorkIntervalRequest(
                        date.toString(),
                        "08:30",
                        "18:00",
                        null
                )
        );

        var source =
                timeCompensation.payrollSource(
                        owner,
                        date,
                        date
                );

        assertEquals(
                540,
                source.workedMinutes()
        );

        assertEquals(
                540,
                source.payableMinutes()
        );

        assertEquals(
                480,
                source.hourlyBasePayableMinutes()
        );

        assertEquals(
                60,
                overtime.balanceMinutes(owner)
        );
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
    void holidayTenNetHoursWithEightHourOrdinaryThresholdCreditsOnlyLastTwoHours() {
        productionCalendar.upsertLocal(
                owner,
                date.toString(),
                new ProductionCalendarDayUpdateRequest(
                        "HOLIDAY",
                        "NORM_OVERRIDE",
                        0,
                        "HOLIDAY",
                        "Праздник с фактической работой"
                )
        );

        /*
         * Shift is 08:30-17:00 with a 30 minute break:
         * ordinary threshold = 480 minutes.
         *
         * Actual 08:30-19:00 with the same 30 minute break:
         * factual net work = 600 minutes.
         *
         * Production norm is independently overridden to zero.
         * Native Classification must therefore produce:
         * REGULAR=480, HOLIDAY=600, OVERTIME=120.
         */
        var saved = actualWork.create(
                owner,
                new ActualWorkIntervalRequest(
                        date.toString(),
                        "08:30",
                        "19:00",
                        30,
                        "10 часов net в праздник"
                )
        );

        assertEquals(600, saved.workedMinutes());

        var truth = workdayTruth.truth(
                owner,
                date
        );

        assertEquals(
                480,
                truth.baseNormMinutes()
        );

        assertEquals(
                0,
                truth.requiredNormMinutes(),
                "holiday production norm remains zero"
        );

        assertEquals(
                600,
                truth.actualMinutes()
        );

        assertEquals(
                120,
                truth.overtimeEarnedMinutes()
        );

        assertEquals(
                "HOLIDAY",
                truth.productionCalendar().payrollEffect()
        );

        assertEquals(
                120,
                overtime.balanceMinutes(owner)
        );

        var system =
                credits.findByOwnerAndWorkDateAndSourceKind(
                        owner,
                        date,
                        "SYSTEM_ACTUAL_WORK"
                ).orElseThrow();

        assertEquals(
                120,
                system.getCreditedMinutes()
        );

        assertEquals(
                8.0,
                system.getPlannedHours(),
                0.001,
                "derived credit keeps ordinary threshold, not production norm"
        );

        assertTrue(
                system.isSystemActualWorkDerived()
        );

        var provenance =
                creditSlices
                        .findByCreditOrderByOffsetStartMinutesAscIdAsc(
                                system
                        );

        assertEquals(
                1,
                provenance.size()
        );

        var overtimeSlice =
                provenance.get(0);

        assertEquals(
                0,
                overtimeSlice.getOffsetStartMinutes()
        );

        assertEquals(
                120,
                overtimeSlice.getMinutes()
        );

        assertEquals(
                saved.id(),
                overtimeSlice
                        .getSourceActualWorkInterval()
                        .getId()
        );

        assertTrue(
                overtimeSlice.isHoliday()
        );

        assertFalse(
                overtimeSlice.isNight()
        );

        assertEquals(
                480,
                overtimeSlice
                        .getOvertimeOrdinalStartMinutes()
        );

        assertEquals(
                LocalTime.of(17, 0),
                overtimeSlice
                        .getSourceStartAt()
                        .toLocalTime()
        );

        assertEquals(
                LocalTime.of(19, 0),
                overtimeSlice
                        .getSourceEndAt()
                        .toLocalTime()
        );

    }

    @Test
    void holidayWithoutOrdinaryScheduleCreditsAllActualMinutesAsOvertime() {
        LocalDate holiday =
                date.plusDays(2);

        /*
         * Deliberately do NOT create a DayEntry for this date.
         * Ordinary threshold must therefore remain zero.
         */
        productionCalendar.upsertLocal(
                owner,
                holiday.toString(),
                new ProductionCalendarDayUpdateRequest(
                        "HOLIDAY",
                        "NORM_OVERRIDE",
                        0,
                        "HOLIDAY",
                        "Праздник без обычной смены"
                )
        );

        var saved = actualWork.create(
                owner,
                new ActualWorkIntervalRequest(
                        holiday.toString(),
                        "10:00",
                        "14:00",
                        0,
                        "4 часа в праздник без обычной смены"
                )
        );

        assertEquals(
                240,
                saved.workedMinutes()
        );

        var truth =
                workdayTruth.truth(
                        owner,
                        holiday
                );

        assertEquals(
                0,
                truth.baseNormMinutes()
        );

        assertEquals(
                0,
                truth.requiredNormMinutes()
        );

        assertEquals(
                240,
                truth.actualMinutes()
        );

        assertEquals(
                240,
                truth.overtimeEarnedMinutes()
        );

        assertEquals(
                "HOLIDAY",
                truth.productionCalendar().payrollEffect()
        );

        assertEquals(
                240,
                overtime.balanceMinutes(owner)
        );

        var system =
                credits.findByOwnerAndWorkDateAndSourceKind(
                        owner,
                        holiday,
                        "SYSTEM_ACTUAL_WORK"
                ).orElseThrow();

        assertEquals(
                240,
                system.getCreditedMinutes()
        );

        assertEquals(
                0.0,
                system.getPlannedHours(),
                0.001
        );

        assertTrue(
                system.isSystemActualWorkDerived()
        );
    }



    @Test
    void overtimeProvenanceSplitsAtNightBoundaryWithoutSplittingTheBankCredit() {
        LocalDate nightDate =
                date.plusDays(3);

        DayEntry entry =
                new DayEntry(
                        owner,
                        nightDate
                );

        entry.setShiftType(day);
        days.saveAndFlush(entry);

        /*
         * Raw fact 12:30-23:00 = 630 minutes.
         * The 30 minute break is consumed from the earliest factual minutes,
         * therefore classified net work is 13:00-23:00 = 600 minutes.
         *
         * Ordinary threshold = 480:
         * 13:00-21:00 REGULAR
         * 21:00-22:00 OVERTIME
         * 22:00-23:00 OVERTIME + NIGHT
         */
        var saved =
                actualWork.create(
                        owner,
                        new ActualWorkIntervalRequest(
                                nightDate.toString(),
                                "12:30",
                                "23:00",
                                30,
                                "проверка provenance на границе ночи"
                        )
                );

        assertEquals(
                600,
                saved.workedMinutes()
        );

        var credit =
                credits
                        .findByOwnerAndWorkDateAndSourceKind(
                                owner,
                                nightDate,
                                "SYSTEM_ACTUAL_WORK"
                        )
                        .orElseThrow();

        assertEquals(
                120,
                credit.getCreditedMinutes()
        );

        var provenance =
                creditSlices
                        .findByCreditOrderByOffsetStartMinutesAscIdAsc(
                                credit
                        );

        assertEquals(
                2,
                provenance.size(),
                "same bank credit must retain two homogeneous classification slices"
        );

        var first =
                provenance.get(0);

        var second =
                provenance.get(1);

        assertEquals(
                0,
                first.getOffsetStartMinutes()
        );

        assertEquals(
                60,
                first.getMinutes()
        );

        assertFalse(first.isNight());
        assertFalse(first.isHoliday());

        assertEquals(
                480,
                first.getOvertimeOrdinalStartMinutes()
        );

        assertEquals(
                LocalTime.of(21, 0),
                first.getSourceStartAt().toLocalTime()
        );

        assertEquals(
                LocalTime.of(22, 0),
                first.getSourceEndAt().toLocalTime()
        );

        assertEquals(
                60,
                second.getOffsetStartMinutes()
        );

        assertEquals(
                60,
                second.getMinutes()
        );

        assertTrue(second.isNight());
        assertFalse(second.isHoliday());

        assertEquals(
                540,
                second.getOvertimeOrdinalStartMinutes()
        );

        assertEquals(
                LocalTime.of(22, 0),
                second.getSourceStartAt().toLocalTime()
        );

        assertEquals(
                LocalTime.of(23, 0),
                second.getSourceEndAt().toLocalTime()
        );

        assertEquals(
                saved.id(),
                first.getSourceActualWorkInterval().getId()
        );

        assertEquals(
                saved.id(),
                second.getSourceActualWorkInterval().getId()
        );

        assertEquals(
                120,
                provenance.stream()
                        .mapToInt(
                                slice -> slice.getMinutes()
                        )
                        .sum()
        );

        assertEquals(
                120,
                overtime.balanceMinutes(owner)
        );
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
