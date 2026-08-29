package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.WorkTimeAccountingMode;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static ru.daniil.shifts.service.AverageEarningsBonusP15Formula.ReferenceWorkedTimeFact;
import static ru.daniil.shifts.service.AverageEarningsLegalPolicy.AbsenceDecision;
import static ru.daniil.shifts.service.AverageEarningsLegalPolicy.AbsenceTreatment;
import static ru.daniil.shifts.service.AverageEarningsLegalPolicy.LegalBasis;

/**
 * 8A4F3G — paragraph-15 reference-period completeness POLICY boundary.
 *
 * <p>F3F3 proves factual scheduled-work completeness. Paragraph 15 also needs
 * to know whether paragraph-5 time was excluded from the reference period.
 * This service combines those two independent authorities and produces the
 * boolean consumed by {@link AverageEarningsBonusP15Policy}. It deliberately
 * does not discover premiums and does not calculate premium money.</p>
 *
 * <p>A whole reference month explicitly proven upstream to require no Payroll
 * snapshot makes the canonical twelve-month period not fully worked. Such a
 * month contributes no invented schedule norm here. The downstream calculation
 * pipeline may therefore use this resolution for policy selection but must fail
 * closed if proportional premium money would require a denominator across that
 * unresolved no-Payroll month.</p>
 */
@Service
public class AverageEarningsBonusP15ReferenceCompletenessService {

    public static final String P5_ABSENCE_TREATMENT_UNRESOLVED =
            "PP_540_P15_REFERENCE_P5_ABSENCE_TREATMENT_UNRESOLVED";
    public static final String REFERENCE_FACT_WINDOW_MISMATCH =
            "PP_540_P15_REFERENCE_FACT_WINDOW_MISMATCH";

    private final AverageEarningsBonusP15ReferenceWorkedTimeFactService workedTime;
    private final AverageEarningsReferenceFactsService referenceFacts;

    public AverageEarningsBonusP15ReferenceCompletenessService(
            AverageEarningsBonusP15ReferenceWorkedTimeFactService workedTime,
            AverageEarningsReferenceFactsService referenceFacts
    ) {
        this.workedTime = Objects.requireNonNull(
                workedTime,
                "P15 reference worked-time authority is required"
        );
        this.referenceFacts = Objects.requireNonNull(
                referenceFacts,
                "Average-earnings reference facts authority is required"
        );
    }

    @Transactional(readOnly = true)
    public Resolution resolve(
            AppUser user,
            LocalDate eventDate,
            List<YearMonth> provenNoPayrollMonths
    ) {
        Objects.requireNonNull(user, "P15 reference completeness requires user");
        Objects.requireNonNull(eventDate, "P15 reference completeness requires event date");
        Objects.requireNonNull(
                provenNoPayrollMonths,
                "P15 reference completeness requires explicit no-Payroll proofs"
        );

        AverageEarningsLegalPolicy.requireRegime(eventDate);

        YearMonth eventMonth = YearMonth.from(eventDate);
        YearMonth referenceFrom = eventMonth.minusMonths(12);
        YearMonth referenceTo = eventMonth.minusMonths(1);

        AverageEarningsBonusP15ReferenceWorkedTimeFactService.Resolution worked =
                workedTime.resolve(user, eventDate, provenNoPayrollMonths);

        if (!worked.ready()) {
            return Resolution.blocked(
                    eventDate,
                    eventMonth,
                    referenceFrom,
                    referenceTo,
                    worked.blockingReason(),
                    worked.blockingPeriod(),
                    null
            );
        }

        if (!worked.eventDate().equals(eventDate)
                || !worked.eventMonth().equals(eventMonth)
                || !worked.referenceFrom().equals(referenceFrom)
                || !worked.referenceTo().equals(referenceTo)) {
            return Resolution.blocked(
                    eventDate,
                    eventMonth,
                    referenceFrom,
                    referenceTo,
                    REFERENCE_FACT_WINDOW_MISMATCH,
                    null,
                    null
            );
        }

        AverageEarningsReferenceFactsService.ReferenceFacts factual =
                referenceFacts.resolve(user, eventMonth);

        if (factual == null) {
            throw new IllegalStateException(
                    "Average earnings reference facts authority returned null"
            );
        }

        if (!factual.eventMonth().equals(eventMonth)
                || !factual.referenceFrom().equals(referenceFrom.atDay(1))
                || !factual.referenceTo().equals(referenceTo.atEndOfMonth())) {
            return Resolution.blocked(
                    eventDate,
                    eventMonth,
                    referenceFrom,
                    referenceTo,
                    REFERENCE_FACT_WINDOW_MISMATCH,
                    null,
                    null
            );
        }

        List<Paragraph5Exclusion> exclusions = new ArrayList<>();

        for (AverageEarningsReferenceFactsService.AbsenceFact fact : factual.absences()) {
            AbsenceDecision decision =
                    AverageEarningsLegalPolicy.classifyAbsence(eventDate, fact);

            if (!decision.resolved()) {
                return Resolution.blocked(
                        eventDate,
                        eventMonth,
                        referenceFrom,
                        referenceTo,
                        P5_ABSENCE_TREATMENT_UNRESOLVED,
                        fact.overlapFrom() == null
                                ? null
                                : YearMonth.from(fact.overlapFrom()),
                        fact.periodId()
                );
            }

            if (decision.treatment() == AbsenceTreatment.UNRESOLVED
                    || decision.basis() == LegalBasis.UNRESOLVED) {
                throw new IllegalStateException(
                        "Resolved paragraph-5 absence decision carries unresolved authority"
                );
            }

            exclusions.add(
                    new Paragraph5Exclusion(
                            fact.periodId(),
                            fact.systemCode(),
                            decision.treatment(),
                            decision.basis(),
                            fact.overlapFrom(),
                            fact.overlapTo(),
                            fact.knownMinutes()
                    )
            );
        }

        List<YearMonth> noPayrollMonths =
                worked.months().stream()
                        .filter(AverageEarningsBonusP15ReferenceWorkedTimeFactService.ResolvedMonth::noPayrollProven)
                        .map(AverageEarningsBonusP15ReferenceWorkedTimeFactService.ResolvedMonth::month)
                        .toList();

        boolean paragraph5ExcludedTimePresent = !exclusions.isEmpty();
        boolean proportionalNormAuthorityComplete = noPayrollMonths.isEmpty();
        boolean referencePeriodFullyWorked =
                worked.scheduleFullyWorked()
                        && !paragraph5ExcludedTimePresent
                        && noPayrollMonths.isEmpty();

        return Resolution.ready(
                eventDate,
                eventMonth,
                referenceFrom,
                referenceTo,
                worked.accountingMode(),
                worked.referenceWorkedTime(),
                worked.scheduleFullyWorked(),
                paragraph5ExcludedTimePresent,
                noPayrollMonths,
                proportionalNormAuthorityComplete,
                referencePeriodFullyWorked,
                exclusions
        );
    }

    public record Paragraph5Exclusion(
            long absencePeriodId,
            String systemCode,
            AbsenceTreatment treatment,
            LegalBasis basis,
            LocalDate overlapFrom,
            LocalDate overlapTo,
            Integer knownMinutes
    ) {
        public Paragraph5Exclusion {
            if (absencePeriodId <= 0L
                    || systemCode == null
                    || systemCode.isBlank()
                    || treatment == null
                    || treatment == AbsenceTreatment.UNRESOLVED
                    || basis == null
                    || basis == LegalBasis.UNRESOLVED
                    || overlapFrom == null
                    || overlapTo == null
                    || overlapTo.isBefore(overlapFrom)
                    || (knownMinutes != null && knownMinutes <= 0)) {
                throw new IllegalArgumentException(
                        "Paragraph-5 exclusion provenance is invalid"
                );
            }
        }
    }

    public record Resolution(
            LocalDate eventDate,
            YearMonth eventMonth,
            YearMonth referenceFrom,
            YearMonth referenceTo,
            boolean ready,
            String blockingReason,
            YearMonth blockingPeriod,
            Long blockingAbsencePeriodId,
            WorkTimeAccountingMode accountingMode,
            ReferenceWorkedTimeFact referenceWorkedTime,
            boolean scheduleFullyWorked,
            boolean paragraph5ExcludedTimePresent,
            List<YearMonth> noPayrollMonths,
            boolean proportionalNormAuthorityComplete,
            boolean referencePeriodFullyWorked,
            List<Paragraph5Exclusion> paragraph5Exclusions
    ) {
        public Resolution {
            Objects.requireNonNull(eventDate, "P15 completeness event date is required");
            Objects.requireNonNull(eventMonth, "P15 completeness event month is required");
            Objects.requireNonNull(referenceFrom, "P15 completeness reference start is required");
            Objects.requireNonNull(referenceTo, "P15 completeness reference end is required");
            noPayrollMonths = List.copyOf(Objects.requireNonNull(
                    noPayrollMonths,
                    "P15 completeness no-Payroll months are required"
            ));
            paragraph5Exclusions = List.copyOf(Objects.requireNonNull(
                    paragraph5Exclusions,
                    "P15 completeness paragraph-5 exclusions are required"
            ));

            if (!eventMonth.equals(YearMonth.from(eventDate))
                    || !referenceFrom.equals(eventMonth.minusMonths(12))
                    || !referenceTo.equals(eventMonth.minusMonths(1))) {
                throw new IllegalArgumentException(
                        "P15 completeness reference window is not canonical"
                );
            }
            if (ready == (blockingReason != null)) {
                throw new IllegalArgumentException(
                        "P15 completeness resolution state is invalid"
                );
            }

            if (ready) {
                if (blockingPeriod != null
                        || blockingAbsencePeriodId != null
                        || accountingMode == null
                        || referenceWorkedTime == null
                        || paragraph5ExcludedTimePresent != !paragraph5Exclusions.isEmpty()
                        || proportionalNormAuthorityComplete != noPayrollMonths.isEmpty()
                        || (referencePeriodFullyWorked
                            && (!scheduleFullyWorked
                                || paragraph5ExcludedTimePresent
                                || !noPayrollMonths.isEmpty()))) {
                    throw new IllegalArgumentException(
                            "Ready P15 completeness resolution is internally inconsistent"
                    );
                }
            } else {
                if (accountingMode != null
                        || referenceWorkedTime != null
                        || scheduleFullyWorked
                        || paragraph5ExcludedTimePresent
                        || !noPayrollMonths.isEmpty()
                        || proportionalNormAuthorityComplete
                        || referencePeriodFullyWorked
                        || !paragraph5Exclusions.isEmpty()) {
                    throw new IllegalArgumentException(
                            "Blocked P15 completeness cannot expose partial authority"
                    );
                }
            }
        }

        public static Resolution ready(
                LocalDate eventDate,
                YearMonth eventMonth,
                YearMonth referenceFrom,
                YearMonth referenceTo,
                WorkTimeAccountingMode accountingMode,
                ReferenceWorkedTimeFact referenceWorkedTime,
                boolean scheduleFullyWorked,
                boolean paragraph5ExcludedTimePresent,
                List<YearMonth> noPayrollMonths,
                boolean proportionalNormAuthorityComplete,
                boolean referencePeriodFullyWorked,
                List<Paragraph5Exclusion> paragraph5Exclusions
        ) {
            return new Resolution(
                    eventDate,
                    eventMonth,
                    referenceFrom,
                    referenceTo,
                    true,
                    null,
                    null,
                    null,
                    accountingMode,
                    referenceWorkedTime,
                    scheduleFullyWorked,
                    paragraph5ExcludedTimePresent,
                    noPayrollMonths,
                    proportionalNormAuthorityComplete,
                    referencePeriodFullyWorked,
                    paragraph5Exclusions
            );
        }

        public static Resolution blocked(
                LocalDate eventDate,
                YearMonth eventMonth,
                YearMonth referenceFrom,
                YearMonth referenceTo,
                String reason,
                YearMonth blockingPeriod,
                Long blockingAbsencePeriodId
        ) {
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException(
                        "P15 completeness blocker reason is required"
                );
            }
            if (blockingAbsencePeriodId != null && blockingAbsencePeriodId <= 0L) {
                throw new IllegalArgumentException(
                        "P15 completeness blocking absence identity is invalid"
                );
            }
            return new Resolution(
                    eventDate,
                    eventMonth,
                    referenceFrom,
                    referenceTo,
                    false,
                    reason,
                    blockingPeriod,
                    blockingAbsencePeriodId,
                    null,
                    null,
                    false,
                    false,
                    List.of(),
                    false,
                    false,
                    List.of()
            );
        }
    }
}
