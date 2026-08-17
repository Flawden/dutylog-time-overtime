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
import java.util.List;

/**
 * Reconciles consequences that are fully derivable from explicit factual work.
 * The fact remains the command/source of truth; per-date overtime credits are
 * replaceable system projections, never another piece of user-entered reality.
 */
@Service
public class WorkdayDerivedCompensationService {
    private static final String BASE = "BASE";
    private static final String LOCAL = "LOCAL_OVERRIDE";

    private final ActualWorkIntervalRepository actualWork;
    private final ActualWorkDayAllocationService allocation;
    private final DayEntryRepository scheduleDays;
    private final ProductionCalendarDayRepository productionDays;
    private final WorkNormService workNorm;
    private final OvertimeService overtime;

    public WorkdayDerivedCompensationService(ActualWorkIntervalRepository actualWork,
                                             ActualWorkDayAllocationService allocation,
                                             DayEntryRepository scheduleDays,
                                             ProductionCalendarDayRepository productionDays,
                                             WorkNormService workNorm,
                                             OvertimeService overtime) {
        this.actualWork = actualWork;
        this.allocation = allocation;
        this.scheduleDays = scheduleDays;
        this.productionDays = productionDays;
        this.workNorm = workNorm;
        this.overtime = overtime;
    }

    @Transactional
    public void reconcile(AppUser user, LocalDate date) {
        reconcileRange(user, date, date);
    }

    @Transactional
    public void reconcileRange(AppUser user, LocalDate from, LocalDate to) {
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            reconcileDate(user, date);
        }
    }

    private void reconcileDate(AppUser user, LocalDate date) {
        List<ActualWorkInterval> intervals = actualWork.findOverlappingRange(user, date, date);
        int actualMinutes = intervals.stream().mapToInt(item -> allocation.netMinutesOnDate(item, date)).sum();

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
