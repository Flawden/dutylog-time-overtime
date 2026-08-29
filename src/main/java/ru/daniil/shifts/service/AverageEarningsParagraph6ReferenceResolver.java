package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.model.AppUser;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Paragraph-6 reference-period authority resolver.
 *
 * <p>This layer does not calculate vacation money or choose paragraphs 7/8.
 * It proves whether the primary twelve-month period remains authoritative and,
 * when paragraph 6 applies, evaluates exactly one preceding equal twelve-month
 * period while preserving the real legal event date.</p>
 *
 * <p>The paragraph-6 trigger is factual, not arithmetic. A zero final average
 * is never used as evidence. The resolver reads raw factually accrued wage
 * candidates and authoritative actually-worked units from the existing
 * numerator/P15 factual chain.</p>
 */
@Service
public class AverageEarningsParagraph6ReferenceResolver {

    public static final String AUTHORITY_WINDOW_MISMATCH =
            "PP_540_P6_REFERENCE_AUTHORITY_WINDOW_MISMATCH";

    private final AverageEarningsNumeratorCalculationService numerator;

    public AverageEarningsParagraph6ReferenceResolver(
            AverageEarningsNumeratorCalculationService numerator
    ) {
        this.numerator = Objects.requireNonNull(
                numerator,
                "Paragraph-6 resolver requires numerator authority"
        );
    }

    @Transactional(readOnly = true)
    public Resolution resolve(
            AppUser user,
            LocalDate eventDate,
            YearMonth discoveryThroughMonth,
            List<YearMonth> provenNoPayrollMonths
    ) {
        Objects.requireNonNull(user, "Paragraph-6 resolver requires user");
        Objects.requireNonNull(eventDate, "Paragraph-6 resolver requires event date");
        Objects.requireNonNull(
                discoveryThroughMonth,
                "Paragraph-6 resolver requires discovery-through month"
        );
        provenNoPayrollMonths = List.copyOf(Objects.requireNonNull(
                provenNoPayrollMonths,
                "Paragraph-6 resolver requires no-Payroll proofs"
        ));

        AverageEarningsLegalPolicy.requireRegime(eventDate);

        AverageEarningsReferenceWindow primary =
                AverageEarningsReferenceWindow.primary(eventDate);

        AverageEarningsNumeratorCalculationService.Resolution primaryNumerator =
                Objects.requireNonNull(
                        numerator.calculate(
                                user,
                                eventDate,
                                discoveryThroughMonth,
                                provenNoPayrollMonths
                        ),
                        "Paragraph-6 primary numerator authority returned null"
                );

        if (!primaryNumerator.ready()) {
            return Resolution.blocked(
                    eventDate,
                    discoveryThroughMonth,
                    BlockingStage.PRIMARY_AUTHORITY,
                    primaryNumerator.blockingReason(),
                    primaryNumerator.blockingPeriod()
            );
        }

        if (!matches(primaryNumerator, eventDate, primary, discoveryThroughMonth)) {
            return Resolution.blocked(
                    eventDate,
                    discoveryThroughMonth,
                    BlockingStage.PRIMARY_AUTHORITY,
                    AUTHORITY_WINDOW_MISMATCH,
                    null
            );
        }

        PeriodEvidence primaryEvidence = evidence(primaryNumerator, primary);

        if (!primaryEvidence.requiresFallback()) {
            return Resolution.ready(
                    eventDate,
                    discoveryThroughMonth,
                    Selection.PRIMARY,
                    primaryEvidence,
                    primaryEvidence
            );
        }

        AverageEarningsReferenceWindow preceding = primary.precedingEqual();

        AverageEarningsNumeratorCalculationService.Resolution precedingNumerator =
                Objects.requireNonNull(
                        numerator.calculate(
                                user,
                                eventDate,
                                preceding,
                                discoveryThroughMonth,
                                provenNoPayrollMonths
                        ),
                        "Paragraph-6 preceding numerator authority returned null"
                );

        if (!precedingNumerator.ready()) {
            return Resolution.blocked(
                    eventDate,
                    discoveryThroughMonth,
                    BlockingStage.PARAGRAPH_6_AUTHORITY,
                    precedingNumerator.blockingReason(),
                    precedingNumerator.blockingPeriod()
            );
        }

        if (!matches(precedingNumerator, eventDate, preceding, discoveryThroughMonth)) {
            return Resolution.blocked(
                    eventDate,
                    discoveryThroughMonth,
                    BlockingStage.PARAGRAPH_6_AUTHORITY,
                    AUTHORITY_WINDOW_MISMATCH,
                    null
            );
        }

        PeriodEvidence precedingEvidence = evidence(precedingNumerator, preceding);

        Selection selection = precedingEvidence.requiresFallback()
                ? Selection.PARAGRAPH_6_EXHAUSTED
                : Selection.PARAGRAPH_6_PRECEDING;

        return Resolution.ready(
                eventDate,
                discoveryThroughMonth,
                selection,
                primaryEvidence,
                precedingEvidence
        );
    }

    private static boolean matches(
            AverageEarningsNumeratorCalculationService.Resolution resolution,
            LocalDate eventDate,
            AverageEarningsReferenceWindow window,
            YearMonth discoveryThroughMonth
    ) {
        return resolution.eventDate().equals(eventDate)
                && resolution.eventMonth().equals(window.eventMonth())
                && resolution.referenceFrom().equals(window.referenceFrom())
                && resolution.referenceTo().equals(window.referenceTo())
                && resolution.discoveryThroughMonth().equals(discoveryThroughMonth);
    }

    private static PeriodEvidence evidence(
            AverageEarningsNumeratorCalculationService.Resolution resolution,
            AverageEarningsReferenceWindow window
    ) {
        AverageEarningsNumeratorFactsService.Resolution facts =
                Objects.requireNonNull(
                        resolution.numeratorFacts(),
                        "Ready paragraph-6 numerator requires raw numerator facts"
                );

        AverageEarningsBonusP15ReferenceCompletenessService.Resolution completeness =
                Objects.requireNonNull(
                        Objects.requireNonNull(
                                resolution.p15(),
                                "Ready paragraph-6 numerator requires P15 authority"
                        ).referenceCompleteness(),
                        "Ready paragraph-6 numerator requires worked-time authority"
                );

        AverageEarningsBonusP15Formula.ReferenceWorkedTimeFact worked =
                Objects.requireNonNull(
                        completeness.referenceWorkedTime(),
                        "Ready paragraph-6 authority requires reference worked time"
                );

        boolean factuallyAccruedWagePresent =
                facts.ordinaryCandidateAmountMinor() > 0L
                        || facts.premiumSpecialAmountMinor() > 0L;

        boolean wholePeriodParagraph5Excluded = wholePeriodParagraph5Excluded(
                window,
                completeness.paragraph5Exclusions()
        );

        List<FallbackReason> reasons = new ArrayList<>();
        if (!factuallyAccruedWagePresent) {
            reasons.add(FallbackReason.NO_FACTUALLY_ACCRUED_WAGE);
        }
        if (worked.workedUnits() == 0L) {
            reasons.add(FallbackReason.NO_ACTUALLY_WORKED_TIME);
        }
        if (wholePeriodParagraph5Excluded) {
            reasons.add(FallbackReason.ENTIRE_REFERENCE_PERIOD_PARAGRAPH_5_EXCLUDED);
        }

        return new PeriodEvidence(
                window,
                factuallyAccruedWagePresent,
                worked.unit(),
                worked.workedUnits(),
                wholePeriodParagraph5Excluded,
                reasons
        );
    }

    private static boolean wholePeriodParagraph5Excluded(
            AverageEarningsReferenceWindow window,
            List<AverageEarningsBonusP15ReferenceCompletenessService.Paragraph5Exclusion> exclusions
    ) {
        if (exclusions.isEmpty()) {
            return false;
        }

        LocalDate targetFrom = window.referenceFromDate();
        LocalDate targetTo = window.referenceToDate();

        List<AverageEarningsBonusP15ReferenceCompletenessService.Paragraph5Exclusion> ordered =
                exclusions.stream()
                        .sorted(Comparator.comparing(
                                AverageEarningsBonusP15ReferenceCompletenessService
                                        .Paragraph5Exclusion::overlapFrom
                        ))
                        .toList();

        LocalDate coveredThrough = targetFrom.minusDays(1);
        for (AverageEarningsBonusP15ReferenceCompletenessService.Paragraph5Exclusion exclusion
                : ordered) {
            LocalDate from = exclusion.overlapFrom().isBefore(targetFrom)
                    ? targetFrom
                    : exclusion.overlapFrom();
            LocalDate to = exclusion.overlapTo().isAfter(targetTo)
                    ? targetTo
                    : exclusion.overlapTo();

            if (to.isBefore(targetFrom) || from.isAfter(targetTo)) {
                continue;
            }

            if (from.isAfter(coveredThrough.plusDays(1))) {
                return false;
            }

            if (to.isAfter(coveredThrough)) {
                coveredThrough = to;
            }

            if (!coveredThrough.isBefore(targetTo)) {
                return true;
            }
        }

        return false;
    }

    public enum Selection {
        PRIMARY,
        PARAGRAPH_6_PRECEDING,
        PARAGRAPH_6_EXHAUSTED
    }

    public enum FallbackReason {
        NO_FACTUALLY_ACCRUED_WAGE,
        NO_ACTUALLY_WORKED_TIME,
        ENTIRE_REFERENCE_PERIOD_PARAGRAPH_5_EXCLUDED
    }

    public enum BlockingStage {
        PRIMARY_AUTHORITY,
        PARAGRAPH_6_AUTHORITY
    }

    public record PeriodEvidence(
            AverageEarningsReferenceWindow window,
            boolean factuallyAccruedWagePresent,
            AverageEarningsBonusP15Formula.WorkMeasureUnit workMeasureUnit,
            long actuallyWorkedUnits,
            boolean wholePeriodParagraph5Excluded,
            List<FallbackReason> fallbackReasons
    ) {
        public PeriodEvidence {
            Objects.requireNonNull(window, "Paragraph-6 evidence window is required");
            Objects.requireNonNull(
                    workMeasureUnit,
                    "Paragraph-6 evidence work-measure unit is required"
            );
            fallbackReasons = List.copyOf(Objects.requireNonNull(
                    fallbackReasons,
                    "Paragraph-6 fallback reasons are required"
            ));
            if (actuallyWorkedUnits < 0L) {
                throw new IllegalArgumentException(
                        "Paragraph-6 actually-worked units must be non-negative"
                );
            }

            boolean noWage = fallbackReasons.contains(
                    FallbackReason.NO_FACTUALLY_ACCRUED_WAGE
            );
            boolean noWork = fallbackReasons.contains(
                    FallbackReason.NO_ACTUALLY_WORKED_TIME
            );
            boolean allP5 = fallbackReasons.contains(
                    FallbackReason.ENTIRE_REFERENCE_PERIOD_PARAGRAPH_5_EXCLUDED
            );

            if (noWage == factuallyAccruedWagePresent
                    || noWork != (actuallyWorkedUnits == 0L)
                    || allP5 != wholePeriodParagraph5Excluded) {
                throw new IllegalArgumentException(
                        "Paragraph-6 evidence reasons contradict factual evidence"
                );
            }
        }

        public boolean requiresFallback() {
            return !fallbackReasons.isEmpty();
        }
    }

    public record Resolution(
            LocalDate eventDate,
            YearMonth eventMonth,
            YearMonth discoveryThroughMonth,
            boolean ready,
            BlockingStage blockingStage,
            String blockingReason,
            YearMonth blockingPeriod,
            Selection selection,
            PeriodEvidence primaryEvidence,
            PeriodEvidence selectedEvidence
    ) {
        public Resolution {
            Objects.requireNonNull(eventDate, "Paragraph-6 resolution event date is required");
            Objects.requireNonNull(eventMonth, "Paragraph-6 resolution event month is required");
            Objects.requireNonNull(
                    discoveryThroughMonth,
                    "Paragraph-6 resolution discovery-through month is required"
            );
            if (!eventMonth.equals(YearMonth.from(eventDate))) {
                throw new IllegalArgumentException(
                        "Paragraph-6 resolution event month does not match legal event date"
                );
            }
            if (ready == (blockingReason != null)) {
                throw new IllegalArgumentException(
                        "Paragraph-6 resolution state is invalid"
                );
            }

            if (ready) {
                if (blockingStage != null
                        || blockingPeriod != null
                        || selection == null
                        || primaryEvidence == null
                        || selectedEvidence == null
                        || !primaryEvidence.window().eventMonth().equals(eventMonth)
                        || !selectedEvidence.window().eventMonth().equals(eventMonth)) {
                    throw new IllegalArgumentException(
                            "Ready paragraph-6 resolution is incomplete"
                    );
                }

                if (selection == Selection.PRIMARY) {
                    if (primaryEvidence.requiresFallback()
                            || selectedEvidence != primaryEvidence) {
                        throw new IllegalArgumentException(
                                "Primary paragraph-6 selection is inconsistent"
                        );
                    }
                } else {
                    AverageEarningsReferenceWindow expected =
                            AverageEarningsReferenceWindow.primary(eventDate).precedingEqual();
                    if (!primaryEvidence.requiresFallback()
                            || !selectedEvidence.window().equals(expected)
                            || (selection == Selection.PARAGRAPH_6_PRECEDING)
                                    == selectedEvidence.requiresFallback()) {
                        throw new IllegalArgumentException(
                                "Paragraph-6 preceding selection is inconsistent"
                        );
                    }
                }
            } else if (blockingStage == null
                    || selection != null
                    || primaryEvidence != null
                    || selectedEvidence != null) {
                throw new IllegalArgumentException(
                        "Blocked paragraph-6 resolution cannot expose partial selection"
                );
            }
        }

        public static Resolution ready(
                LocalDate eventDate,
                YearMonth discoveryThroughMonth,
                Selection selection,
                PeriodEvidence primaryEvidence,
                PeriodEvidence selectedEvidence
        ) {
            return new Resolution(
                    eventDate,
                    YearMonth.from(eventDate),
                    discoveryThroughMonth,
                    true,
                    null,
                    null,
                    null,
                    selection,
                    primaryEvidence,
                    selectedEvidence
            );
        }

        public static Resolution blocked(
                LocalDate eventDate,
                YearMonth discoveryThroughMonth,
                BlockingStage stage,
                String reason,
                YearMonth period
        ) {
            Objects.requireNonNull(stage, "Paragraph-6 blocker stage is required");
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException(
                        "Paragraph-6 blocker reason is required"
                );
            }
            return new Resolution(
                    eventDate,
                    YearMonth.from(eventDate),
                    discoveryThroughMonth,
                    false,
                    stage,
                    reason,
                    period,
                    null,
                    null,
                    null
            );
        }
    }
}
