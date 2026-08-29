package ru.daniil.shifts.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Paragraph-5 money attribution policy for ordinary average-earnings money.
 *
 * <p>Government Resolution No. 540 requires excluding both the time listed in
 * paragraph 5 and amounts accrued for that time. This policy deliberately does
 * not invent a day/minute ratio for partially overlapping money. Exact temporal
 * provenance can prove a whole earning line included or excluded; ambiguous
 * partial overlap blocks the numerator.</p>
 *
 * <p>Coverage provenance is the strongest source because it explicitly states
 * the period whose time the earning pays/preserves. If coverage is absent, an
 * explicit earning period may be used. Posting month is never substituted for
 * either source.</p>
 */
public final class AverageEarningsParagraph5MoneyPolicy {

    public static final String TIME_AUTHORITY_MISSING =
            "PP_540_P5_ORDINARY_EARNING_TIME_AUTHORITY_MISSING";
    public static final String PARTIAL_OVERLAP_UNRESOLVED =
            "PP_540_P5_ORDINARY_EARNING_PARTIAL_OVERLAP_UNRESOLVED";
    public static final String ORDINARY_BUCKET_MISMATCH =
            "PP_540_P5_ORDINARY_EARNING_BUCKET_MISMATCH";

    private AverageEarningsParagraph5MoneyPolicy() {
    }

    public static Resolution resolve(
            LocalDate eventDate,
            List<AverageEarningsNumeratorFactsService.MonthFact> months,
            List<AverageEarningsBonusP15ReferenceCompletenessService.Paragraph5Exclusion> exclusions
    ) {
        return resolve(
                eventDate,
                AverageEarningsReferenceWindow.primary(eventDate),
                months,
                exclusions
        );
    }

    public static Resolution resolve(
            LocalDate eventDate,
            AverageEarningsReferenceWindow referenceWindow,
            List<AverageEarningsNumeratorFactsService.MonthFact> months,
            List<AverageEarningsBonusP15ReferenceCompletenessService.Paragraph5Exclusion> exclusions
    ) {
        Objects.requireNonNull(eventDate, "Paragraph-5 money policy requires event date");
        Objects.requireNonNull(
                referenceWindow,
                "Paragraph-5 money policy requires reference window"
        ).requireEventDate(eventDate);
        AverageEarningsLegalPolicy.requireRegime(eventDate);

        months = List.copyOf(Objects.requireNonNull(
                months,
                "Paragraph-5 money policy requires numerator months"
        ));
        exclusions = List.copyOf(Objects.requireNonNull(
                exclusions,
                "Paragraph-5 money policy requires exclusion facts"
        ));

        YearMonth referenceFrom = referenceWindow.referenceFrom();
        YearMonth referenceTo = referenceWindow.referenceTo();

        if (months.size() != 12) {
            throw new IllegalArgumentException(
                    "Paragraph-5 money policy requires twelve numerator months"
            );
        }

        long expectedOrdinary = 0L;
        for (int index = 0; index < months.size(); index++) {
            AverageEarningsNumeratorFactsService.MonthFact month =
                    Objects.requireNonNull(months.get(index), "Numerator month is required");
            YearMonth expected = referenceFrom.plusMonths(index);
            if (!expected.equals(month.period())) {
                throw new IllegalArgumentException(
                        "Paragraph-5 numerator month order is not canonical"
                );
            }
            expectedOrdinary = Math.addExact(
                    expectedOrdinary,
                    month.ordinaryCandidateAmountMinor()
            );
        }

        List<DateRange> mergedExclusions = mergeExclusions(exclusions);
        List<LineDecision> decisions = new ArrayList<>();
        long lineOrdinary = 0L;
        long included = 0L;
        long excluded = 0L;

        for (AverageEarningsNumeratorFactsService.MonthFact month : months) {
            for (AverageEarningsNumeratorFactsService.EarningFact earning : month.earnings()) {
                if (earning.treatment()
                        != AverageEarningsLegalPolicy.EarningTreatment.ORDINARY_REMUNERATION) {
                    continue;
                }

                lineOrdinary = Math.addExact(lineOrdinary, earning.amountMinor());

                if (mergedExclusions.isEmpty()) {
                    included = Math.addExact(included, earning.amountMinor());
                    decisions.add(LineDecision.included(
                            month.period(),
                            earning,
                            TemporalSource.NONE,
                            null,
                            null
                    ));
                    continue;
                }

                TemporalAuthority authority = temporalAuthority(earning);
                if (authority == null) {
                    return Resolution.blocked(
                            eventDate,
                            referenceFrom,
                            referenceTo,
                            TIME_AUTHORITY_MISSING,
                            month.period()
                    );
                }

                boolean overlaps = overlapsAny(
                        authority.from(),
                        authority.to(),
                        mergedExclusions
                );

                if (!overlaps) {
                    included = Math.addExact(included, earning.amountMinor());
                    decisions.add(LineDecision.included(
                            month.period(),
                            earning,
                            authority.source(),
                            authority.from(),
                            authority.to()
                    ));
                    continue;
                }

                if (fullyCovered(
                        authority.from(),
                        authority.to(),
                        mergedExclusions
                )) {
                    excluded = Math.addExact(excluded, earning.amountMinor());
                    decisions.add(LineDecision.excluded(
                            month.period(),
                            earning,
                            authority.source(),
                            authority.from(),
                            authority.to()
                    ));
                    continue;
                }

                return Resolution.blocked(
                        eventDate,
                        referenceFrom,
                        referenceTo,
                        PARTIAL_OVERLAP_UNRESOLVED,
                        month.period()
                );
            }
        }

        if (lineOrdinary != expectedOrdinary) {
            return Resolution.blocked(
                    eventDate,
                    referenceFrom,
                    referenceTo,
                    ORDINARY_BUCKET_MISMATCH,
                    null
            );
        }

        if (Math.addExact(included, excluded) != expectedOrdinary) {
            throw new IllegalStateException(
                    "Paragraph-5 ordinary money allocation lost numerator money"
            );
        }

        return Resolution.ready(
                eventDate,
                referenceFrom,
                referenceTo,
                decisions,
                included,
                excluded
        );
    }

    private static TemporalAuthority temporalAuthority(
            AverageEarningsNumeratorFactsService.EarningFact earning
    ) {
        if (earning.coverageFrom() != null) {
            return new TemporalAuthority(
                    TemporalSource.COVERAGE,
                    earning.coverageFrom(),
                    earning.coverageTo()
            );
        }

        if (earning.earningPeriodFrom() != null) {
            return new TemporalAuthority(
                    TemporalSource.EARNING_PERIOD,
                    earning.earningPeriodFrom(),
                    earning.earningPeriodTo()
            );
        }

        return null;
    }

    private static List<DateRange> mergeExclusions(
            List<AverageEarningsBonusP15ReferenceCompletenessService.Paragraph5Exclusion> exclusions
    ) {
        List<DateRange> ranges = exclusions.stream()
                .map(exclusion -> {
                    Objects.requireNonNull(exclusion, "Paragraph-5 exclusion is required");
                    return new DateRange(exclusion.overlapFrom(), exclusion.overlapTo());
                })
                .sorted(Comparator.comparing(DateRange::from))
                .toList();

        if (ranges.isEmpty()) {
            return List.of();
        }

        List<DateRange> merged = new ArrayList<>();
        DateRange current = ranges.get(0);

        for (int index = 1; index < ranges.size(); index++) {
            DateRange next = ranges.get(index);
            if (!next.from().isAfter(current.to().plusDays(1))) {
                LocalDate newTo = next.to().isAfter(current.to())
                        ? next.to()
                        : current.to();
                current = new DateRange(current.from(), newTo);
            } else {
                merged.add(current);
                current = next;
            }
        }

        merged.add(current);
        return List.copyOf(merged);
    }

    private static boolean overlapsAny(
            LocalDate from,
            LocalDate to,
            List<DateRange> exclusions
    ) {
        for (DateRange exclusion : exclusions) {
            if (!to.isBefore(exclusion.from())
                    && !from.isAfter(exclusion.to())) {
                return true;
            }
        }
        return false;
    }

    private static boolean fullyCovered(
            LocalDate from,
            LocalDate to,
            List<DateRange> exclusions
    ) {
        LocalDate cursor = from;

        for (DateRange exclusion : exclusions) {
            if (exclusion.to().isBefore(cursor)) {
                continue;
            }
            if (exclusion.from().isAfter(cursor)) {
                return false;
            }

            if (!exclusion.to().isBefore(cursor)) {
                cursor = exclusion.to().plusDays(1);
                if (cursor.isAfter(to)) {
                    return true;
                }
            }
        }

        return cursor.isAfter(to);
    }

    public enum TemporalSource {
        NONE,
        COVERAGE,
        EARNING_PERIOD
    }

    public enum LineTreatment {
        INCLUDE,
        EXCLUDE_PARAGRAPH_5
    }

    public record LineDecision(
            YearMonth postingMonth,
            ru.daniil.shifts.model.PayrollEarningKind earningKind,
            long factualAmountMinor,
            TemporalSource temporalSource,
            LocalDate sourceFrom,
            LocalDate sourceTo,
            LineTreatment treatment
    ) {
        public LineDecision {
            Objects.requireNonNull(postingMonth, "Paragraph-5 line posting month is required");
            Objects.requireNonNull(earningKind, "Paragraph-5 line earning kind is required");
            Objects.requireNonNull(temporalSource, "Paragraph-5 temporal source is required");
            Objects.requireNonNull(treatment, "Paragraph-5 line treatment is required");
            if (factualAmountMinor < 0L) {
                throw new IllegalArgumentException(
                        "Paragraph-5 line amount must be non-negative"
                );
            }
            if ((sourceFrom == null) != (sourceTo == null)) {
                throw new IllegalArgumentException(
                        "Paragraph-5 line source period must be complete or absent"
                );
            }
            if (sourceFrom != null && sourceTo.isBefore(sourceFrom)) {
                throw new IllegalArgumentException(
                        "Paragraph-5 line source period is reversed"
                );
            }
            if (temporalSource == TemporalSource.NONE && sourceFrom != null) {
                throw new IllegalArgumentException(
                        "Paragraph-5 NONE temporal source cannot expose dates"
                );
            }
            if (temporalSource != TemporalSource.NONE && sourceFrom == null) {
                throw new IllegalArgumentException(
                        "Paragraph-5 dated temporal source requires dates"
                );
            }
        }

        private static LineDecision included(
                YearMonth month,
                AverageEarningsNumeratorFactsService.EarningFact earning,
                TemporalSource source,
                LocalDate from,
                LocalDate to
        ) {
            return new LineDecision(
                    month,
                    earning.kind(),
                    earning.amountMinor(),
                    source,
                    from,
                    to,
                    LineTreatment.INCLUDE
            );
        }

        private static LineDecision excluded(
                YearMonth month,
                AverageEarningsNumeratorFactsService.EarningFact earning,
                TemporalSource source,
                LocalDate from,
                LocalDate to
        ) {
            return new LineDecision(
                    month,
                    earning.kind(),
                    earning.amountMinor(),
                    source,
                    from,
                    to,
                    LineTreatment.EXCLUDE_PARAGRAPH_5
            );
        }
    }

    public record Resolution(
            LocalDate eventDate,
            YearMonth referenceFrom,
            YearMonth referenceTo,
            boolean ready,
            String blockingReason,
            YearMonth blockingPeriod,
            List<LineDecision> decisions,
            long includedOrdinaryAmountMinor,
            long excludedParagraph5OrdinaryAmountMinor
    ) {
        public Resolution {
            Objects.requireNonNull(eventDate, "Paragraph-5 money event date is required");
            Objects.requireNonNull(referenceFrom, "Paragraph-5 money reference start is required");
            Objects.requireNonNull(referenceTo, "Paragraph-5 money reference end is required");
            decisions = List.copyOf(Objects.requireNonNull(
                    decisions,
                    "Paragraph-5 line decisions are required"
            ));
            new AverageEarningsReferenceWindow(
                    YearMonth.from(eventDate),
                    referenceFrom,
                    referenceTo
            );
            if (ready == (blockingReason != null)) {
                throw new IllegalArgumentException(
                        "Paragraph-5 money resolution state is invalid"
                );
            }
            if (includedOrdinaryAmountMinor < 0L
                    || excludedParagraph5OrdinaryAmountMinor < 0L) {
                throw new IllegalArgumentException(
                        "Paragraph-5 money totals must be non-negative"
                );
            }
            if (!ready && (!decisions.isEmpty()
                    || includedOrdinaryAmountMinor != 0L
                    || excludedParagraph5OrdinaryAmountMinor != 0L)) {
                throw new IllegalArgumentException(
                        "Blocked paragraph-5 money resolution cannot expose partial money"
                );
            }
            if (ready && blockingPeriod != null) {
                throw new IllegalArgumentException(
                        "Ready paragraph-5 money resolution cannot expose blocker period"
                );
            }
        }

        public static Resolution ready(
                LocalDate eventDate,
                YearMonth referenceFrom,
                YearMonth referenceTo,
                List<LineDecision> decisions,
                long included,
                long excluded
        ) {
            return new Resolution(
                    eventDate,
                    referenceFrom,
                    referenceTo,
                    true,
                    null,
                    null,
                    decisions,
                    included,
                    excluded
            );
        }

        public static Resolution blocked(
                LocalDate eventDate,
                YearMonth referenceFrom,
                YearMonth referenceTo,
                String reason,
                YearMonth period
        ) {
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException(
                        "Paragraph-5 money blocker is required"
                );
            }
            return new Resolution(
                    eventDate,
                    referenceFrom,
                    referenceTo,
                    false,
                    reason,
                    period,
                    List.of(),
                    0L,
                    0L
            );
        }
    }

    private record TemporalAuthority(
            TemporalSource source,
            LocalDate from,
            LocalDate to
    ) {
        private TemporalAuthority {
            Objects.requireNonNull(source);
            Objects.requireNonNull(from);
            Objects.requireNonNull(to);
        }
    }

    private record DateRange(
            LocalDate from,
            LocalDate to
    ) {
        private DateRange {
            Objects.requireNonNull(from);
            Objects.requireNonNull(to);
            if (to.isBefore(from)) {
                throw new IllegalArgumentException("Paragraph-5 exclusion range is reversed");
            }
        }
    }
}
