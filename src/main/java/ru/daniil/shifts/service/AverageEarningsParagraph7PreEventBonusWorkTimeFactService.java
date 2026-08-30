package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.WorkTimeAccountingMode;
import ru.daniil.shifts.service.PayrollP15ScheduledWorkFreezeService.RangeFact;
import ru.daniil.shifts.service.PayrollP15ScheduledWorkFreezeService.RangeResult;
import ru.daniil.shifts.service.TimeCompensationService.PayrollSourceSnapshot;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;

import static ru.daniil.shifts.service.AverageEarningsParagraph7PreEventBonusP15Policy.IncompletePreEventTreatment.NO_ADJUSTMENT_ALREADY_ACCRUED_FOR_ACTUAL_PRE_EVENT_TIME;

/**
 * Paragraph-7 pre-event paragraph-15 scheduled/worked-time FACT authority.
 *
 * <p>The legal basis is the exact half-open range {@code [eventMonthStart,eventDate)}.
 * J3B6B2 decides whether an included bonus needs a pre-event work-time decision;
 * this layer proves that decision from the canonical posted-only Payroll source and
 * the same plan/actual relation engine used by the immutable P15 monthly freeze.</p>
 *
 * <p>SUMMARIZED accounting uses planned-and-worked minutes / scheduled minutes.
 * DAILY accounting uses scheduled days and blocks on a partially worked scheduled
 * day instead of rounding it to either zero or one. Worked-outside-plan minutes are
 * preserved as provenance but never improve the paragraph-15 coefficient.</p>
 *
 * <p>This is FACT authority only. It does not divide bonus money, does not round
 * monetary amounts, does not assemble the paragraph-7 wage numerator and does not
 * select paragraph 8.</p>
 */
@Service
public class AverageEarningsParagraph7PreEventBonusWorkTimeFactService {
    public static final String POLICY_WINDOW_MISMATCH =
            "PP_540_P7_P15_WORK_TIME_POLICY_WINDOW_MISMATCH";
    public static final String SOURCE_WINDOW_MISMATCH =
            "PP_540_P7_P15_WORK_TIME_SOURCE_WINDOW_MISMATCH";
    public static final String RANGE_WINDOW_MISMATCH =
            "PP_540_P7_P15_WORK_TIME_RANGE_WINDOW_MISMATCH";
    public static final String MIXED_ACCOUNTING_MODE =
            "PP_540_P7_P15_WORK_TIME_MIXED_ACCOUNTING_MODE";
    public static final String DAILY_PARTIAL_DAY_UNRESOLVED =
            "PP_540_P7_P15_WORK_TIME_DAILY_PARTIAL_DAY_UNRESOLVED";
    public static final String PRE_EVENT_NORM_ZERO =
            "PP_540_P7_P15_WORK_TIME_PRE_EVENT_NORM_ZERO";
    public static final String UNIT_OVERFLOW =
            "PP_540_P7_P15_WORK_TIME_UNIT_OVERFLOW";

    private final TimeCompensationService timeCompensation;
    private final PayrollP15ScheduledWorkFreezeService scheduledWork;

    public AverageEarningsParagraph7PreEventBonusWorkTimeFactService(
            TimeCompensationService timeCompensation,
            PayrollP15ScheduledWorkFreezeService scheduledWork
    ) {
        this.timeCompensation = Objects.requireNonNull(
                timeCompensation,
                "Paragraph-7 bonus work-time FACT requires canonical Payroll source"
        );
        this.scheduledWork = Objects.requireNonNull(
                scheduledWork,
                "Paragraph-7 bonus work-time FACT requires canonical P15 relation authority"
        );
    }

    @Transactional(readOnly = true)
    public Resolution resolve(
            AppUser user,
            AverageEarningsParagraph7PreEventBonusP15Policy.Resolution policy
    ) {
        Objects.requireNonNull(user, "Paragraph-7 bonus work-time FACT requires user");
        Objects.requireNonNull(policy, "Paragraph-7 bonus work-time FACT requires B6B2 policy");

        LocalDate eventDate = Objects.requireNonNull(
                policy.eventDate(),
                "Paragraph-7 bonus work-time FACT requires event date"
        );
        AverageEarningsLegalPolicy.requireRegime(eventDate);
        LocalDate periodFrom = YearMonth.from(eventDate).atDay(1);
        LocalDate cutoffExclusive = eventDate;

        if (!periodFrom.equals(policy.periodFrom())
                || !cutoffExclusive.equals(policy.cutoffExclusive())) {
            return Resolution.blocked(
                    eventDate,
                    periodFrom,
                    policy,
                    POLICY_WINDOW_MISMATCH,
                    null
            );
        }
        if (!policy.ready()) {
            return Resolution.blocked(
                    eventDate,
                    periodFrom,
                    policy,
                    Objects.requireNonNull(
                            policy.blockingReason(),
                            "Blocked B6B2 policy requires blocker"
                    ),
                    null
            );
        }

        boolean workTimeRequired = policy.decisions().stream()
                .filter(AverageEarningsParagraph7PreEventBonusP15Policy.Decision::included)
                .anyMatch(decision -> decision.incompletePreEventTreatment()
                        != NO_ADJUSTMENT_ALREADY_ACCRUED_FOR_ACTUAL_PRE_EVENT_TIME);
        if (!workTimeRequired) {
            return Resolution.readyWithoutWorkTime(policy);
        }
        if (eventDate.equals(periodFrom)) {
            return Resolution.blocked(
                    eventDate,
                    periodFrom,
                    policy,
                    PRE_EVENT_NORM_ZERO,
                    null
            );
        }

        LocalDate periodTo = eventDate.minusDays(1);
        PayrollSourceSnapshot source = Objects.requireNonNull(
                timeCompensation.payrollSource(user, periodFrom, periodTo),
                "Paragraph-7 bonus work-time Payroll source returned null"
        );
        if (!periodFrom.equals(source.from()) || !periodTo.equals(source.to())) {
            return Resolution.blocked(
                    eventDate,
                    periodFrom,
                    policy,
                    SOURCE_WINDOW_MISMATCH,
                    null
            );
        }

        RangeResult range = Objects.requireNonNull(
                scheduledWork.deriveRange(user, source),
                "Paragraph-7 bonus work-time range authority returned null"
        );
        if (!periodFrom.equals(range.from()) || !periodTo.equals(range.to())) {
            return Resolution.blocked(
                    eventDate,
                    periodFrom,
                    policy,
                    RANGE_WINDOW_MISMATCH,
                    null
            );
        }
        if (!range.ready()) {
            return Resolution.blocked(
                    eventDate,
                    periodFrom,
                    policy,
                    Objects.requireNonNull(
                            range.blockingReason(),
                            "Blocked scheduled-work range requires blocker"
                    ),
                    range.blockingDate()
            );
        }

        WorkTimeAccountingMode resolvedMode = null;
        long workedUnits = 0L;
        long normUnits = 0L;
        List<RangeFact> facts = List.copyOf(range.facts());
        for (RangeFact fact : facts) {
            Objects.requireNonNull(fact, "Paragraph-7 scheduled-work range cannot contain null FACT");
            if (fact.date().isBefore(periodFrom) || fact.date().isAfter(periodTo)) {
                return Resolution.blocked(
                        eventDate,
                        periodFrom,
                        policy,
                        RANGE_WINDOW_MISMATCH,
                        fact.date()
                );
            }
            if (resolvedMode == null) {
                resolvedMode = fact.accountingMode();
            } else if (resolvedMode != fact.accountingMode()) {
                return Resolution.blocked(
                        eventDate,
                        periodFrom,
                        policy,
                        MIXED_ACCOUNTING_MODE,
                        fact.date()
                );
            }

            try {
                if (fact.accountingMode() == WorkTimeAccountingMode.SUMMARIZED) {
                    normUnits = Math.addExact(normUnits, fact.scheduleMinutes());
                    workedUnits = Math.addExact(workedUnits, fact.plannedAndWorkedMinutes());
                } else if (fact.accountingMode() == WorkTimeAccountingMode.DAILY) {
                    if (fact.scheduleMinutes() == 0) {
                        continue;
                    }
                    normUnits = Math.addExact(normUnits, 1L);
                    if (fact.plannedAndWorkedMinutes() == fact.scheduleMinutes()) {
                        workedUnits = Math.addExact(workedUnits, 1L);
                    } else if (fact.plannedAndWorkedMinutes() != 0) {
                        return Resolution.blocked(
                                eventDate,
                                periodFrom,
                                policy,
                                DAILY_PARTIAL_DAY_UNRESOLVED + ":" + fact.date(),
                                fact.date()
                        );
                    }
                } else {
                    return Resolution.blocked(
                            eventDate,
                            periodFrom,
                            policy,
                            MIXED_ACCOUNTING_MODE,
                            fact.date()
                    );
                }
            } catch (ArithmeticException overflow) {
                return Resolution.blocked(
                        eventDate,
                        periodFrom,
                        policy,
                        UNIT_OVERFLOW,
                        fact.date()
                );
            }
        }

        if (resolvedMode == null || normUnits <= 0L) {
            return Resolution.blocked(
                    eventDate,
                    periodFrom,
                    policy,
                    PRE_EVENT_NORM_ZERO,
                    null
            );
        }
        WorkMeasureUnit unit = resolvedMode == WorkTimeAccountingMode.DAILY
                ? WorkMeasureUnit.WORKING_DAYS
                : WorkMeasureUnit.WORKING_MINUTES;
        return Resolution.ready(
                policy,
                unit,
                workedUnits,
                normUnits,
                workedUnits == normUnits,
                facts
        );
    }

    public enum WorkMeasureUnit {
        WORKING_DAYS,
        WORKING_MINUTES
    }

    public record Resolution(
            LocalDate eventDate,
            LocalDate periodFrom,
            LocalDate cutoffExclusive,
            boolean ready,
            String blockingReason,
            LocalDate blockingDate,
            boolean workTimeRequired,
            WorkMeasureUnit unit,
            long workedUnits,
            long normUnits,
            boolean scheduleFullyWorked,
            AverageEarningsParagraph7PreEventBonusP15Policy.Resolution policy,
            List<RangeFact> rangeFacts
    ) {
        public Resolution {
            Objects.requireNonNull(eventDate, "Paragraph-7 bonus work-time event date is required");
            Objects.requireNonNull(periodFrom, "Paragraph-7 bonus work-time period start is required");
            Objects.requireNonNull(cutoffExclusive, "Paragraph-7 bonus work-time cutoff is required");
            Objects.requireNonNull(policy, "Paragraph-7 bonus work-time policy provenance is required");
            rangeFacts = List.copyOf(Objects.requireNonNull(
                    rangeFacts,
                    "Paragraph-7 bonus work-time range FACTs are required"
            ));
            if (!periodFrom.equals(YearMonth.from(eventDate).atDay(1))
                    || !cutoffExclusive.equals(eventDate)) {
                throw new IllegalArgumentException("Paragraph-7 bonus work-time window is invalid");
            }
            if (ready == (blockingReason != null)) {
                throw new IllegalArgumentException("Paragraph-7 bonus work-time state is invalid");
            }
            if (!ready) {
                if (workTimeRequired
                        || unit != null
                        || workedUnits != 0L
                        || normUnits != 0L
                        || scheduleFullyWorked
                        || !rangeFacts.isEmpty()) {
                    throw new IllegalArgumentException(
                            "Blocked paragraph-7 bonus work-time authority cannot expose partial FACTs"
                    );
                }
            } else if (!workTimeRequired) {
                if (unit != null
                        || workedUnits != 0L
                        || normUnits != 0L
                        || scheduleFullyWorked
                        || !rangeFacts.isEmpty()) {
                    throw new IllegalArgumentException(
                            "Unused paragraph-7 bonus work-time authority cannot invent ratio FACTs"
                    );
                }
            } else if (unit == null
                    || normUnits <= 0L
                    || workedUnits < 0L
                    || workedUnits > normUnits
                    || scheduleFullyWorked != (workedUnits == normUnits)) {
                throw new IllegalArgumentException(
                        "Ready paragraph-7 bonus work-time ratio authority is invalid"
                );
            }
        }

        static Resolution readyWithoutWorkTime(
                AverageEarningsParagraph7PreEventBonusP15Policy.Resolution policy
        ) {
            return new Resolution(
                    policy.eventDate(),
                    policy.periodFrom(),
                    policy.cutoffExclusive(),
                    true,
                    null,
                    null,
                    false,
                    null,
                    0L,
                    0L,
                    false,
                    policy,
                    List.of()
            );
        }

        static Resolution ready(
                AverageEarningsParagraph7PreEventBonusP15Policy.Resolution policy,
                WorkMeasureUnit unit,
                long workedUnits,
                long normUnits,
                boolean scheduleFullyWorked,
                List<RangeFact> facts
        ) {
            return new Resolution(
                    policy.eventDate(),
                    policy.periodFrom(),
                    policy.cutoffExclusive(),
                    true,
                    null,
                    null,
                    true,
                    unit,
                    workedUnits,
                    normUnits,
                    scheduleFullyWorked,
                    policy,
                    facts
            );
        }

        static Resolution blocked(
                LocalDate eventDate,
                LocalDate periodFrom,
                AverageEarningsParagraph7PreEventBonusP15Policy.Resolution policy,
                String reason,
                LocalDate blockingDate
        ) {
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("Paragraph-7 bonus work-time blocker is required");
            }
            return new Resolution(
                    eventDate,
                    periodFrom,
                    eventDate,
                    false,
                    reason,
                    blockingDate,
                    false,
                    null,
                    0L,
                    0L,
                    false,
                    policy,
                    List.of()
            );
        }
    }
}
