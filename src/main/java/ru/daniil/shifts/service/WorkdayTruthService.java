package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.dto.Dtos.ActualWorkIntervalDto;
import ru.daniil.shifts.dto.Dtos.ProductionCalendarDayDto;
import ru.daniil.shifts.dto.Dtos.TimeCompensationDayDto;
import ru.daniil.shifts.dto.Dtos.TimeCompensationSummaryDto;
import ru.daniil.shifts.dto.Dtos.WorkdayTruthDto;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.DayEntry;
import ru.daniil.shifts.model.ShiftType;
import ru.daniil.shifts.repo.DayEntryRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Human-facing truth for one workday. It joins schedule, required norm, explicit fact
 * and already-derived absence/overtime read models without duplicating their storage.
 */
@Service
public class WorkdayTruthService {
    private final DayEntryRepository days;
    private final WorkNormService workNorm;
    private final ProductionCalendarService productionCalendar;
    private final TimeCompensationService compensation;
    private final ActualWorkService actualWork;

    public WorkdayTruthService(DayEntryRepository days,
                               WorkNormService workNorm,
                               ProductionCalendarService productionCalendar,
                               TimeCompensationService compensation,
                               ActualWorkService actualWork) {
        this.days = days;
        this.workNorm = workNorm;
        this.productionCalendar = productionCalendar;
        this.compensation = compensation;
        this.actualWork = actualWork;
    }

    @Transactional(readOnly = true)
    public WorkdayTruthDto truth(AppUser user, LocalDate date) {
        DayEntry day = days.findByOwnerAndDate(user, date).orElse(null);
        ShiftType shift = day == null ? null : day.getShiftType();
        int baseNormMinutes = workNorm.basePlannedMinutes(day);
        ProductionCalendarDayDto production = productionCalendar.resolvedDay(user, date);
        int requiredNormMinutes = production.productionNormMinutes();

        TimeCompensationSummaryDto summary = compensation.summary(user, date, date);
        TimeCompensationDayDto row = summary.days().stream()
                .filter(item -> date.toString().equals(item.date()))
                .findFirst().orElse(null);
        List<ActualWorkIntervalDto> actual = actualWork.list(user, date, date);

        return new WorkdayTruthDto(
                date.toString(),
                shift == null ? null : shift.getName(),
                time(day == null ? null : day.getShiftSourceStartTime(), shift == null ? null : shift.getStartTime()),
                time(day == null ? null : day.getShiftSourceEndTime(), shift == null ? null : shift.getEndTime()),
                baseNormMinutes,
                requiredNormMinutes,
                production,
                !actual.isEmpty(),
                row == null ? 0 : row.workedMinutes(),
                row == null ? 0 : row.absenceMinutes(),
                row == null ? 0 : row.overtimeEarnedMinutes(),
                row == null ? 0 : row.overtimeUsedMinutes(),
                row == null ? (requiredNormMinutes > 0 ? "По обязательной норме" : "Рабочее обязательство отсутствует") : row.factLabel(),
                actual
        );
    }

    private String time(LocalTime snapshot, LocalTime fallback) {
        LocalTime value = snapshot != null ? snapshot : fallback;
        return value == null ? null : value.toString();
    }
}
