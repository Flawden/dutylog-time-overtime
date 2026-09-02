package ru.daniil.shifts.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.dto.Dtos.ActualWorkBreakWindowRequest;
import ru.daniil.shifts.dto.Dtos.ActualWorkIntervalRequest;
import ru.daniil.shifts.model.ActualWorkInterval;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.DayEntry;
import ru.daniil.shifts.model.ProductionCalendarDay;
import ru.daniil.shifts.model.ShiftType;
import ru.daniil.shifts.repo.ActualWorkIntervalRepository;
import ru.daniil.shifts.repo.DayEntryRepository;
import ru.daniil.shifts.repo.ProductionCalendarDayRepository;
import ru.daniil.shifts.repo.ShiftTypeRepository;
import ru.daniil.shifts.repo.UserRepository;
import ru.daniil.shifts.service.OrdinaryWorkPremiumSourceService.OrdinaryPremiumSource;
import ru.daniil.shifts.service.OrdinaryWorkPremiumSourceService.SourcePiece;
import ru.daniil.shifts.service.TimeCompensationService.PayrollSourceSnapshot;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 8A4F3U4 — Payroll paid-interval regression matrix.
 *
 * One factual paid-minute truth must flow through:
 * Actual Work explicit break subtraction -> Native Pay Classification ->
 * ordinary NIGHT/HOLIDAY source -> Payroll time source.
 *
 * Gross presence is never allowed to resurrect an unpaid break downstream.
 */
@SpringBootTest
@Transactional
class PayrollPaidIntervalRegressionMatrixTest {

    @Autowired UserRepository users;
    @Autowired ShiftTypeRepository shifts;
    @Autowired DayEntryRepository days;
    @Autowired ProductionCalendarDayRepository productionDays;
    @Autowired ActualWorkIntervalRepository intervals;
    @Autowired ActualWorkService actualWork;
    @Autowired ActualWorkDayAllocationService allocation;
    @Autowired PayClassificationService classification;
    @Autowired OrdinaryWorkPremiumSourceService ordinaryPremiumSource;
    @Autowired TimeCompensationService timeCompensation;
    @Autowired OvertimeService overtime;

    @Test
    void crossMidnightBreakFeedsOnePaidMinuteTruthThroughClassificationAndPayrollSource() {
        LocalDate first = LocalDate.of(2026, 9, 7);
        LocalDate second = first.plusDays(1);
        AppUser owner = owner("u4-paid-minute-truth");
        assign(owner, first, "U4 exact night", LocalTime.of(20, 0), LocalTime.of(8, 0), 60, 11.0);
        markHoliday(owner, second);

        ActualWorkInterval interval = explicit(
                owner,
                first,
                second,
                LocalTime.of(20, 0),
                LocalTime.of(8, 0),
                60,
                first.atTime(23, 30),
                second.atTime(0, 30),
                "U4 cross-midnight explicit break"
        );

        var paid = allocation.netSegments(interval);
        assertEquals(2, paid.size());
        assertEquals(210, paid.get(0).minutes());
        assertEquals(450, paid.get(1).minutes());
        assertEquals(660, paid.stream().mapToInt(ActualWorkDayAllocationService.NetWorkSegment::minutes).sum());

        var firstClassified = classification.classify(owner, first);
        var secondClassified = classification.classify(owner, second);
        assertEquals(210, firstClassified.workedMinutes());
        assertEquals(450, secondClassified.workedMinutes());
        assertEquals(660, firstClassified.regularMinutes() + secondClassified.regularMinutes());
        assertEquals(0, firstClassified.overtimeMinutes() + secondClassified.overtimeMinutes());
        assertEquals(90, firstClassified.nightMinutes());
        assertEquals(330, secondClassified.nightMinutes());
        assertEquals(450, secondClassified.holidayMinutes());

        OrdinaryPremiumSource firstPremium = ordinaryPremiumSource.project(owner, first);
        OrdinaryPremiumSource secondPremium = ordinaryPremiumSource.project(owner, second);
        assertTrue(firstPremium.ready());
        assertTrue(secondPremium.ready());
        assertEquals(210, firstPremium.canonicalOrdinaryMinutes());
        assertEquals(450, secondPremium.canonicalOrdinaryMinutes());
        assertEquals(90, dimensionMinutes(firstPremium, true, null));
        assertEquals(330, dimensionMinutes(secondPremium, true, null));
        assertEquals(450, dimensionMinutes(secondPremium, null, true));

        PayrollSourceSnapshot payroll = timeCompensation.payrollSource(owner, first, second);
        assertEquals(660, payroll.workedMinutes());
        assertEquals(660, payroll.payableMinutes());
        assertEquals(660, payroll.hourlyBasePayableMinutes());
        assertEquals(0, overtime.balanceMinutes(owner));
    }

    @Test
    void breakAcrossMidnightNeverReappearsAsOvertimeOrPayrollBase() {
        LocalDate first = LocalDate.of(2026, 9, 9);
        LocalDate second = first.plusDays(1);
        AppUser owner = owner("u4-overtime-break-boundary");
        assign(owner, first, "U4 overtime night", LocalTime.of(20, 0), LocalTime.of(8, 0), 60, 11.0);

        ActualWorkInterval interval = explicit(
                owner,
                first,
                second,
                LocalTime.of(18, 0),
                LocalTime.of(8, 0),
                60,
                first.atTime(23, 30),
                second.atTime(0, 30),
                "U4 overtime with explicit midnight break"
        );

        assertEquals(330, allocation.netMinutesOnDate(interval, first));
        assertEquals(450, allocation.netMinutesOnDate(interval, second));

        var firstClassified = classification.classify(owner, first);
        var secondClassified = classification.classify(owner, second);
        assertEquals(330, firstClassified.regularMinutes());
        assertEquals(0, firstClassified.overtimeMinutes());
        assertEquals(330, secondClassified.regularMinutes());
        assertEquals(120, secondClassified.overtimeMinutes());
        assertEquals(780, firstClassified.workedMinutes() + secondClassified.workedMinutes());
        assertEquals(660, firstClassified.regularMinutes() + secondClassified.regularMinutes());
        assertEquals(120, overtime.balanceMinutes(owner));

        OrdinaryPremiumSource firstPremium = ordinaryPremiumSource.project(owner, first);
        OrdinaryPremiumSource secondPremium = ordinaryPremiumSource.project(owner, second);
        assertEquals(330, firstPremium.canonicalOrdinaryMinutes());
        assertEquals(330, secondPremium.canonicalOrdinaryMinutes());
        assertEquals(660, firstPremium.canonicalOrdinaryMinutes() + secondPremium.canonicalOrdinaryMinutes());
        assertTrue(firstPremium.pieces().stream().allMatch(piece -> !piece.consumedSlice().overtime()));
        assertTrue(secondPremium.pieces().stream().allMatch(piece -> !piece.consumedSlice().overtime()));

        PayrollSourceSnapshot payroll = timeCompensation.payrollSource(owner, first, second);
        assertEquals(780, payroll.workedMinutes());
        assertEquals(780, payroll.payableMinutes());
        assertEquals(660, payroll.hourlyBasePayableMinutes(),
                "Payroll ordinary base must contain only classifier REGULAR minutes");
        assertEquals(120, payroll.payableMinutes() - payroll.hourlyBasePayableMinutes(),
                "Bank-first overtime must stay outside ordinary Payroll base");
    }

    @Test
    void fullNightUnpaidBreakCannotReceiveNightOrHolidayPremiumDimensions() {
        LocalDate first = LocalDate.of(2026, 9, 14);
        LocalDate second = first.plusDays(1);
        AppUser owner = owner("u4-full-night-unpaid");
        assign(owner, first, "U4 full night unpaid", LocalTime.of(20, 0), LocalTime.of(8, 0), 480, 4.0);
        markHoliday(owner, first);
        markHoliday(owner, second);

        ActualWorkInterval interval = explicit(
                owner,
                first,
                second,
                LocalTime.of(20, 0),
                LocalTime.of(8, 0),
                480,
                first.atTime(22, 0),
                second.atTime(6, 0),
                "U4 entire NIGHT window is unpaid"
        );

        var paid = allocation.netSegments(interval);
        assertEquals(2, paid.size());
        assertEquals(120, paid.get(0).minutes());
        assertEquals(120, paid.get(1).minutes());
        assertEquals(240, paid.stream().mapToInt(ActualWorkDayAllocationService.NetWorkSegment::minutes).sum());

        var firstClassified = classification.classify(owner, first);
        var secondClassified = classification.classify(owner, second);
        assertEquals(0, firstClassified.nightMinutes());
        assertEquals(0, secondClassified.nightMinutes());
        assertEquals(120, firstClassified.holidayMinutes());
        assertEquals(120, secondClassified.holidayMinutes());
        assertEquals(240, firstClassified.holidayMinutes() + secondClassified.holidayMinutes(),
                "HOLIDAY applies only to paid factual minutes, never gross presence");

        OrdinaryPremiumSource firstPremium = ordinaryPremiumSource.project(owner, first);
        OrdinaryPremiumSource secondPremium = ordinaryPremiumSource.project(owner, second);
        assertEquals(120, firstPremium.canonicalOrdinaryMinutes());
        assertEquals(120, secondPremium.canonicalOrdinaryMinutes());
        assertEquals(0, dimensionMinutes(firstPremium, true, null));
        assertEquals(0, dimensionMinutes(secondPremium, true, null));
        assertEquals(120, dimensionMinutes(firstPremium, null, true));
        assertEquals(120, dimensionMinutes(secondPremium, null, true));

        PayrollSourceSnapshot payroll = timeCompensation.payrollSource(owner, first, second);
        assertEquals(240, payroll.workedMinutes());
        assertEquals(240, payroll.payableMinutes());
        assertEquals(240, payroll.hourlyBasePayableMinutes());
        assertEquals(0, overtime.balanceMinutes(owner));
    }

    private int dimensionMinutes(
            OrdinaryPremiumSource source,
            Boolean night,
            Boolean holiday
    ) {
        return source.pieces()
                .stream()
                .filter(piece -> night == null || piece.night() == night)
                .filter(piece -> holiday == null || piece.holiday() == holiday)
                .mapToInt(SourcePiece::minutes)
                .sum();
    }

    private AppUser owner(String username) {
        return users.saveAndFlush(new AppUser(username, "{noop}unused"));
    }

    private void assign(
            AppUser owner,
            LocalDate date,
            String name,
            LocalTime start,
            LocalTime end,
            int breakMinutes,
            double plannedHours
    ) {
        ShiftType shift = shifts.saveAndFlush(
                new ShiftType(
                        owner,
                        name,
                        plannedHours,
                        "#123456",
                        false,
                        start,
                        end,
                        breakMinutes,
                        plannedHours
                )
        );
        DayEntry entry = new DayEntry(owner, date);
        entry.setShiftType(shift);
        days.saveAndFlush(entry);
    }

    private void markHoliday(AppUser owner, LocalDate date) {
        ProductionCalendarDay holiday = new ProductionCalendarDay(owner, date, "LOCAL_OVERRIDE");
        holiday.update(
                "HOLIDAY",
                "NONE",
                null,
                "HOLIDAY",
                "U4 regression holiday",
                "CUSTOM",
                "u4-paid-interval-matrix"
        );
        productionDays.saveAndFlush(holiday);
    }

    private ActualWorkInterval explicit(
            AppUser owner,
            LocalDate workDate,
            LocalDate endDate,
            LocalTime start,
            LocalTime end,
            int breakMinutes,
            LocalDateTime breakStart,
            LocalDateTime breakEnd,
            String note
    ) {
        actualWork.create(
                owner,
                new ActualWorkIntervalRequest(
                        workDate.toString(),
                        endDate.toString(),
                        start.toString(),
                        end.toString(),
                        breakMinutes,
                        List.of(
                                new ActualWorkBreakWindowRequest(
                                        0,
                                        breakStart.toString(),
                                        breakEnd.toString()
                                )
                        ),
                        note
                )
        );
        return intervals.findOverlappingRange(owner, workDate, endDate)
                .stream()
                .filter(item -> workDate.equals(item.getWorkDate()))
                .findFirst()
                .orElseThrow();
    }
}
