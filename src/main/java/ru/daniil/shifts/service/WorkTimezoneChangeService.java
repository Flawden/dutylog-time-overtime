package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.model.ActualWorkInterval;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.repo.ActualWorkIntervalRepository;
import ru.daniil.shifts.repo.UserRepository;
import ru.daniil.shifts.service.exception.ApiException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Atomic historical Work Timezone mutation.
 *
 * A timezone term is not only a profile preference: changing historical
 * context can change the absolute identity and real elapsed duration of factual
 * work, and therefore its derived overtime consequences.
 *
 * The complete operation lives in one transaction:
 *
 * timezone term
 * -> Actual Work historical identities
 * -> derived compensation
 *
 * Any conflict rolls the whole operation back.
 */
@Service
public class WorkTimezoneChangeService {

    private final WorkTimezoneHistoryService timezoneHistory;
    private final ActualWorkIntervalRepository actualWork;
    private final ActualWorkIdentityService identity;
    private final WorkdayDerivedCompensationService derivedCompensation;
    private final UserRepository users;
    private final UserTimeService userTimeService;
    private final ShiftOccurrenceService shiftOccurrenceService;
    private final TaskService taskService;
    private final ShiftTypeService shiftTypeService;
    private final QuickScenarioService quickScenarioService;

    public WorkTimezoneChangeService(
            WorkTimezoneHistoryService timezoneHistory,
            ActualWorkIntervalRepository actualWork,
            ActualWorkIdentityService identity,
            WorkdayDerivedCompensationService derivedCompensation,
            UserRepository users,
            UserTimeService userTimeService,
            ShiftOccurrenceService shiftOccurrenceService,
            TaskService taskService,
            ShiftTypeService shiftTypeService,
            QuickScenarioService quickScenarioService
    ) {
        this.timezoneHistory = timezoneHistory;
        this.actualWork = actualWork;
        this.identity = identity;
        this.derivedCompensation = derivedCompensation;
        this.users = users;
        this.userTimeService = userTimeService;
        this.shiftOccurrenceService = shiftOccurrenceService;
        this.taskService = taskService;
        this.shiftTypeService = shiftTypeService;
        this.quickScenarioService = quickScenarioService;
    }

    /**
     * Insert or replace one effective timezone term and rebuild only the
     * historical range owned by that term:
     *
     * [effectiveFrom, nextEffectiveFrom)
     */
    @Transactional
    public ChangeResult upsertAndReconcile(
            AppUser user,
            LocalDateTime effectiveFrom,
            String timezone
    ) {
        if (user == null) {
            throw new IllegalArgumentException("user is required");
        }

        /*
         * AppUser.workTimezone is now only the current compatibility cache.
         * Capture its meaning before changing the effective-dated history.
         */
        String previousCurrentTimezone =
                userTimeService.workZone(user).getId();

        LocalDateTime currentLocalMoment =
                userTimeService.workNow(user);

        WorkTimezoneHistoryService.ChangeWindow window =
                timezoneHistory.upsert(
                        user,
                        effectiveFrom,
                        timezone
                );

        List<ActualWorkInterval> affected =
                affectedIntervals(
                        user,
                        window.effectiveFrom(),
                        window.effectiveToExclusive()
                );

        LocalDate reconcileFrom = null;
        LocalDate reconcileTo = null;

        for (ActualWorkInterval interval : affected) {
            ActualWorkIdentityService.Identity resolved =
                    identity.resolve(
                            user,
                            interval.getWorkDate(),
                            interval.getEndDate(),
                            interval.getStartTime(),
                            interval.getEndTime()
                    );

            int breakMinutes =
                    Math.max(0, interval.getBreakMinutes());

            if (breakMinutes >= resolved.elapsedMinutes()) {
                throw ApiException.conflict(
                        "ACTUAL_WORK_BREAK_INVALID_AFTER_CONTEXT_CHANGE",
                        "После изменения рабочего часового пояса фактический "
                                + "интервал " + interval.getId()
                                + " стал короче или равен своему перерыву. "
                                + "Исправь факт работы или границу часового пояса."
                );
            }

            interval.setSourceTimezone(
                    resolved.sourceTimezone()
            );
            interval.setStartInstant(
                    resolved.startInstant()
            );
            interval.setEndInstant(
                    resolved.endInstant()
            );
            interval.setWorkedMinutes(
                    resolved.elapsedMinutes() - breakMinutes
            );

            /*
             * TRUE means this identity was rebuilt from historical Temporal
             * Work Context rather than captured directly when the fact was
             * originally created/edited.
             */
            interval.setIdentityReconstructed(true);

            if (reconcileFrom == null
                    || interval.getWorkDate().isBefore(reconcileFrom)) {
                reconcileFrom = interval.getWorkDate();
            }

            if (reconcileTo == null
                    || interval.getEndDate().isAfter(reconcileTo)) {
                reconcileTo = interval.getEndDate();
            }
        }

        if (!affected.isEmpty()) {
            actualWork.saveAllAndFlush(affected);

            /*
             * This path intentionally bypasses only the ordinary period-open
             * mutation gate. Overtime allocation/use invariants remain active.
             *
             * If a derived credit would become smaller than minutes already
             * consumed from it, OvertimeService throws and this transaction
             * rolls back the timezone term AND every rebuilt identity.
             */
            derivedCompensation
                    .reconcileRangeHistoricalCorrection(
                            user,
                            reconcileFrom,
                            reconcileTo
                    );
        }

        /*
         * Historical corrections in a closed/middle window must never move
         * today's presentation state. Only the Work Context term that owns the
         * current local moment may update the legacy/current cache and rebase
         * current-oriented entities.
         *
         * These compatibility mutations remain inside the SAME transaction as
         * timezone history + Actual Work + derived overtime reconciliation.
         */
        if (ownsMoment(window, currentLocalMoment)
                && !previousCurrentTimezone.equals(window.timezone())) {

            shiftOccurrenceService.captureLegacyBeforeTimezoneChange(
                    user,
                    previousCurrentTimezone
            );

            taskService.rebaseForTimezoneChange(
                    user,
                    previousCurrentTimezone,
                    window.timezone()
            );

            shiftTypeService.rebaseForTimezoneChange(
                    user,
                    previousCurrentTimezone,
                    window.timezone()
            );

            quickScenarioService.rebaseForTimezoneChange(
                    user,
                    previousCurrentTimezone,
                    window.timezone()
            );

            user.setWorkTimezone(window.timezone());
            user.setDisplayTimezone(window.timezone());
            users.saveAndFlush(user);
        }

        return new ChangeResult(
                window.effectiveFrom(),
                window.effectiveToExclusive(),
                window.previousTimezone(),
                window.timezone(),
                affected.size(),
                reconcileFrom,
                reconcileTo
        );
    }

    private boolean ownsMoment(
            WorkTimezoneHistoryService.ChangeWindow window,
            LocalDateTime moment
    ) {
        if (moment.isBefore(window.effectiveFrom())) {
            return false;
        }

        return window.effectiveToExclusive() == null
                || moment.isBefore(window.effectiveToExclusive());
    }

    private List<ActualWorkInterval> affectedIntervals(
            AppUser user,
            LocalDateTime from,
            LocalDateTime toExclusive
    ) {
        List<ActualWorkInterval> candidates;

        if (toExclusive == null) {
            candidates = actualWork
                    .findByOwnerAndEndDateGreaterThanEqualOrderByWorkDateAscStartTimeAscIdAsc(
                            user,
                            from.toLocalDate()
                    );
        } else {
            candidates = actualWork.findOverlappingRange(
                    user,
                    from.toLocalDate(),
                    toExclusive.toLocalDate()
            );
        }

        return candidates.stream()
                .filter(interval ->
                        overlaps(
                                interval,
                                from,
                                toExclusive
                        )
                )
                .toList();
    }

    /**
     * Local wall-clock overlap with the effective-dated Work Context range.
     *
     * [actualStart, actualEnd)
     * intersects
     * [contextFrom, contextTo)
     */
    private boolean overlaps(
            ActualWorkInterval interval,
            LocalDateTime from,
            LocalDateTime toExclusive
    ) {
        LocalDateTime actualStart =
                interval.getWorkDate()
                        .atTime(interval.getStartTime());

        LocalDateTime actualEnd =
                interval.getEndDate()
                        .atTime(interval.getEndTime());

        if (!actualEnd.isAfter(from)) {
            return false;
        }

        return toExclusive == null
                || actualStart.isBefore(toExclusive);
    }

    public record ChangeResult(
            LocalDateTime effectiveFrom,
            LocalDateTime effectiveToExclusive,
            String previousTimezone,
            String timezone,
            int reconstructedIntervals,
            LocalDate reconcileFrom,
            LocalDate reconcileTo
    ) {}
}
