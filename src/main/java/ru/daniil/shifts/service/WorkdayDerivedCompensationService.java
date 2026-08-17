package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.model.ActualWorkInterval;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.DayEntry;
import ru.daniil.shifts.model.ProductionCalendarDay;
import ru.daniil.shifts.repo.ActualWorkIntervalRepository;
import ru.daniil.shifts.repo.DayEntryRepository;
import ru.daniil.shifts.repo.ProductionCalendarDayRepository;

import java.time.LocalDate;

/**
 * Reconciles consequences that are fully derivable from an explicit factual workday.
 * The fact remains the command/source of truth; the overtime credit is a replaceable
 * system projection, never a second piece of user-entered reality.
 */
@Service
public class WorkdayDerivedCompensationService {
    private static final String BASE = "BASE";
    private static final String LOCAL = "LOCAL_OVERRIDE";

    private final ActualWorkIntervalRepository actualWork;
    private final DayEntryRepository scheduleDays;
    private final ProductionCalendarDayRepository productionDays;
    private final WorkNormService workNorm;
    private final OvertimeService overtime;

    public WorkdayDerivedCompensationService(ActualWorkIntervalRepository actualWork,
                                             DayEntryRepository scheduleDays,
                                             ProductionCalendarDayRepository productionDays,
                                             WorkNormService workNorm,
                                             OvertimeService overtime) {
        this.actualWork = actualWork;
        this.scheduleDays = scheduleDays;
        this.productionDays = productionDays;
        this.workNorm = workNorm;
        this.overtime = overtime;
    }

    @Transactional
    public void reconcile(AppUser user, LocalDate date) {
        var intervals = actualWork.findByOwnerAndWorkDateOrderByStartTimeAscIdAsc(user, date);
        int actualMinutes = intervals.stream().mapToInt(ActualWorkInterval::getWorkedMinutes).sum();

        DayEntry schedule = scheduleDays.findByOwnerAndDate(user, date).orElse(null);
        int baseMinutes = workNorm.basePlannedMinutes(schedule);
        ProductionCalendarDay local = productionDays.findByOwnerAndDateAndLayer(user, date, LOCAL).orElse(null);
        ProductionCalendarDay base = productionDays.findByOwnerAndDateAndLayer(user, date, BASE).orElse(null);
        ProductionCalendarDay production = local != null ? local : base;

        int requiredMinutes = baseMinutes;
        if (production != null && "NORM_OVERRIDE".equals(production.getScheduleEffect())
                && production.getNormMinutesOverride() != null) {
            requiredMinutes = production.getNormMinutesOverride();
        }

        boolean holidayWork = production != null && "HOLIDAY".equals(production.getPayrollEffect());
        int targetMinutes = intervals.isEmpty() || holidayWork ? 0 : Math.max(0, actualMinutes - requiredMinutes);
        String reason = holidayWork
                ? "DutyLog · праздничная работа учитывается отдельно"
                : "DutyLog · фактическая работа " + actualMinutes + " мин при норме " + requiredMinutes + " мин";
        overtime.reconcileActualWorkCredit(user, date, targetMinutes, requiredMinutes, reason);
    }
}
