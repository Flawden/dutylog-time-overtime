package ru.daniil.shifts.service;

import org.junit.jupiter.api.Test;
import ru.daniil.shifts.model.PayrollEarningKind;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static ru.daniil.shifts.service.AverageEarningsLegalPolicy.AbsenceTreatment.EXCLUDE_PRESERVED_AVERAGE;
import static ru.daniil.shifts.service.AverageEarningsLegalPolicy.EarningTreatment.*;
import static ru.daniil.shifts.service.AverageEarningsLegalPolicy.LegalBasis.PP_540_P2;
import static ru.daniil.shifts.service.AverageEarningsLegalPolicy.LegalBasis.PP_540_P5_A;

class AverageEarningsParagraph5MoneyPolicyTest {

    private static final LocalDate EVENT = LocalDate.of(2026, 9, 10);
    private static final YearMonth FROM = YearMonth.of(2025, 9);

    @Test
    void noParagraph5ExclusionsIncludeUndatedOrdinaryMoneyWithoutInventingTimeProvenance() {
        var result = resolve(
                employedMonth(0, List.of(ordinary(100_000L, null, null, null, null)), 100_000L),
                List.of()
        );

        assertTrue(result.ready());
        assertEquals(100_000L, result.includedOrdinaryAmountMinor());
        assertEquals(0L, result.excludedParagraph5OrdinaryAmountMinor());
        assertEquals(AverageEarningsParagraph5MoneyPolicy.TemporalSource.NONE,
                result.decisions().get(0).temporalSource());
    }

    @Test
    void premiumSpecialAndAlreadyExcludedMoneyAreNeverAddedToOrdinaryParagraph5Bucket() {
        List<AverageEarningsNumeratorFactsService.EarningFact> earnings = List.of(
                ordinary(80_000L, null, null, null, null),
                earning(PayrollEarningKind.MONTHLY_BONUS, 30_000L, PREMIUM_SPECIAL_RULE, null, null, null, null),
                earning(PayrollEarningKind.VACATION_PAY, 20_000L, AverageEarningsLegalPolicy.EarningTreatment.EXCLUDE_PRESERVED_AVERAGE, null, null, null, null)
        );

        var result = resolve(employedMonth(0, earnings, 80_000L), List.of());

        assertTrue(result.ready());
        assertEquals(80_000L, result.includedOrdinaryAmountMinor());
        assertEquals(1, result.decisions().size());
    }

    @Test
    void explicitCoverageCompletelyOutsideParagraph5TimeIsIncluded() {
        var result = resolve(
                employedMonth(0, List.of(ordinary(70_000L, null, null,
                        LocalDate.of(2025, 9, 1), LocalDate.of(2025, 9, 10))), 70_000L),
                List.of(exclusion(LocalDate.of(2025, 9, 20), LocalDate.of(2025, 9, 22)))
        );

        assertTrue(result.ready());
        assertEquals(70_000L, result.includedOrdinaryAmountMinor());
        assertEquals(AverageEarningsParagraph5MoneyPolicy.TemporalSource.COVERAGE,
                result.decisions().get(0).temporalSource());
    }

    @Test
    void explicitCoverageCompletelyInsideParagraph5TimeIsExcludedWholeWithoutRatioArithmetic() {
        var result = resolve(
                employedMonth(0, List.of(ordinary(70_000L, null, null,
                        LocalDate.of(2025, 9, 20), LocalDate.of(2025, 9, 22))), 70_000L),
                List.of(exclusion(LocalDate.of(2025, 9, 18), LocalDate.of(2025, 9, 25)))
        );

        assertTrue(result.ready());
        assertEquals(0L, result.includedOrdinaryAmountMinor());
        assertEquals(70_000L, result.excludedParagraph5OrdinaryAmountMinor());
        assertEquals(AverageEarningsParagraph5MoneyPolicy.LineTreatment.EXCLUDE_PARAGRAPH_5,
                result.decisions().get(0).treatment());
    }

    @Test
    void earningPeriodIsExplicitFallbackWhenCoverageIsAbsentAndCanProveInclusion() {
        var result = resolve(
                employedMonth(0, List.of(ordinary(55_000L,
                        LocalDate.of(2025, 9, 2), LocalDate.of(2025, 9, 2), null, null)), 55_000L),
                List.of(exclusion(LocalDate.of(2025, 9, 8), LocalDate.of(2025, 9, 9)))
        );

        assertTrue(result.ready());
        assertEquals(AverageEarningsParagraph5MoneyPolicy.TemporalSource.EARNING_PERIOD,
                result.decisions().get(0).temporalSource());
    }

    @Test
    void earningPeriodFallbackCanProveWholeLineExclusion() {
        var result = resolve(
                employedMonth(0, List.of(ordinary(55_000L,
                        LocalDate.of(2025, 9, 8), LocalDate.of(2025, 9, 9), null, null)), 55_000L),
                List.of(exclusion(LocalDate.of(2025, 9, 8), LocalDate.of(2025, 9, 9)))
        );

        assertTrue(result.ready());
        assertEquals(55_000L, result.excludedParagraph5OrdinaryAmountMinor());
    }

    @Test
    void coverageIsStrongerThanDifferentEarningPeriodForParagraph5TimeAttribution() {
        var result = resolve(
                employedMonth(0, List.of(ordinary(40_000L,
                        LocalDate.of(2025, 9, 20), LocalDate.of(2025, 9, 22),
                        LocalDate.of(2025, 9, 1), LocalDate.of(2025, 9, 3))), 40_000L),
                List.of(exclusion(LocalDate.of(2025, 9, 20), LocalDate.of(2025, 9, 22)))
        );

        assertTrue(result.ready());
        assertEquals(40_000L, result.includedOrdinaryAmountMinor());
        assertEquals(AverageEarningsParagraph5MoneyPolicy.TemporalSource.COVERAGE,
                result.decisions().get(0).temporalSource());
    }

    @Test
    void paragraph5PresenceBlocksUndatedOrdinaryMoneyInsteadOfUsingPostingMonth() {
        var result = resolve(
                employedMonth(0, List.of(ordinary(90_000L, null, null, null, null)), 90_000L),
                List.of(exclusion(LocalDate.of(2025, 9, 5), LocalDate.of(2025, 9, 6)))
        );

        assertFalse(result.ready());
        assertEquals(AverageEarningsParagraph5MoneyPolicy.TIME_AUTHORITY_MISSING,
                result.blockingReason());
        assertEquals(FROM, result.blockingPeriod());
        assertTrue(result.decisions().isEmpty());
    }

    @Test
    void partialOverlapBlocksInsteadOfBacksolvingMoneyByDaysOrMinutes() {
        var result = resolve(
                employedMonth(0, List.of(ordinary(90_000L,
                        LocalDate.of(2025, 9, 1), LocalDate.of(2025, 9, 10), null, null)), 90_000L),
                List.of(exclusion(LocalDate.of(2025, 9, 5), LocalDate.of(2025, 9, 6)))
        );

        assertFalse(result.ready());
        assertEquals(AverageEarningsParagraph5MoneyPolicy.PARTIAL_OVERLAP_UNRESOLVED,
                result.blockingReason());
    }

    @Test
    void adjacentParagraph5PeriodsMergeAndCanProveWholeLineExcluded() {
        var result = resolve(
                employedMonth(0, List.of(ordinary(60_000L,
                        LocalDate.of(2025, 9, 5), LocalDate.of(2025, 9, 8), null, null)), 60_000L),
                List.of(
                        exclusion(LocalDate.of(2025, 9, 5), LocalDate.of(2025, 9, 6)),
                        exclusion(LocalDate.of(2025, 9, 7), LocalDate.of(2025, 9, 8))
                )
        );

        assertTrue(result.ready());
        assertEquals(60_000L, result.excludedParagraph5OrdinaryAmountMinor());
    }

    @Test
    void overlappingParagraph5PeriodsMergeWithoutDoubleCountingMoney() {
        var result = resolve(
                employedMonth(0, List.of(ordinary(60_000L,
                        LocalDate.of(2025, 9, 5), LocalDate.of(2025, 9, 8), null, null)), 60_000L),
                List.of(
                        exclusion(LocalDate.of(2025, 9, 4), LocalDate.of(2025, 9, 7)),
                        exclusion(LocalDate.of(2025, 9, 6), LocalDate.of(2025, 9, 10))
                )
        );

        assertTrue(result.ready());
        assertEquals(60_000L, result.excludedParagraph5OrdinaryAmountMinor());
    }

    @Test
    void separatedExclusionsWithGapDoNotPretendToCoverWholeEarningPeriod() {
        var result = resolve(
                employedMonth(0, List.of(ordinary(60_000L,
                        LocalDate.of(2025, 9, 5), LocalDate.of(2025, 9, 9), null, null)), 60_000L),
                List.of(
                        exclusion(LocalDate.of(2025, 9, 5), LocalDate.of(2025, 9, 6)),
                        exclusion(LocalDate.of(2025, 9, 8), LocalDate.of(2025, 9, 9))
                )
        );

        assertFalse(result.ready());
        assertEquals(AverageEarningsParagraph5MoneyPolicy.PARTIAL_OVERLAP_UNRESOLVED,
                result.blockingReason());
    }

    @Test
    void ordinaryBucketMismatchBlocksWithoutPartialMoney() {
        var result = resolve(
                employedMonth(0, List.of(ordinary(60_000L, null, null, null, null)), 61_000L),
                List.of()
        );

        assertFalse(result.ready());
        assertEquals(AverageEarningsParagraph5MoneyPolicy.ORDINARY_BUCKET_MISMATCH,
                result.blockingReason());
        assertEquals(0L, result.includedOrdinaryAmountMinor());
    }

    @Test
    void zeroOrdinaryBucketRemainsReadyEvenWhenParagraph5ExclusionsExist() {
        var result = resolve(employedMonth(0, List.of(), 0L),
                List.of(exclusion(LocalDate.of(2025, 9, 1), LocalDate.of(2025, 9, 30))));

        assertTrue(result.ready());
        assertEquals(0L, result.includedOrdinaryAmountMinor());
        assertTrue(result.decisions().isEmpty());
    }

    @Test
    void twelveCanonicalMonthsAreMandatory() {
        assertThrows(IllegalArgumentException.class, () ->
                AverageEarningsParagraph5MoneyPolicy.resolve(EVENT, List.of(), List.of()));
    }

    @Test
    void canonicalMonthOrderCannotBeRearranged() {
        List<AverageEarningsNumeratorFactsService.MonthFact> months = twelveZeroMonths();
        List<AverageEarningsNumeratorFactsService.MonthFact> broken = new ArrayList<>(months);
        var first = broken.get(0);
        broken.set(0, broken.get(1));
        broken.set(1, first);

        assertThrows(IllegalArgumentException.class, () ->
                AverageEarningsParagraph5MoneyPolicy.resolve(EVENT, broken, List.of()));
    }

    @Test
    void nullExclusionListIsRejectedAtPolicyBoundary() {
        assertThrows(NullPointerException.class, () ->
                AverageEarningsParagraph5MoneyPolicy.resolve(EVENT, twelveZeroMonths(), null));
    }

    @Test
    void lineDecisionRejectsDatedNoneTemporalSource() {
        assertThrows(IllegalArgumentException.class, () -> new AverageEarningsParagraph5MoneyPolicy.LineDecision(
                FROM,
                PayrollEarningKind.BASE_PAY,
                1L,
                AverageEarningsParagraph5MoneyPolicy.TemporalSource.NONE,
                LocalDate.of(2025, 9, 1),
                LocalDate.of(2025, 9, 1),
                AverageEarningsParagraph5MoneyPolicy.LineTreatment.INCLUDE
        ));
    }

    @Test
    void blockedResolutionCannotExposePartialMoney() {
        assertThrows(IllegalArgumentException.class, () -> new AverageEarningsParagraph5MoneyPolicy.Resolution(
                EVENT,
                FROM,
                YearMonth.of(2026, 8),
                false,
                "blocked",
                FROM,
                List.of(),
                1L,
                0L
        ));
    }

    private AverageEarningsParagraph5MoneyPolicy.Resolution resolve(
            AverageEarningsNumeratorFactsService.MonthFact first,
            List<AverageEarningsBonusP15ReferenceCompletenessService.Paragraph5Exclusion> exclusions
    ) {
        List<AverageEarningsNumeratorFactsService.MonthFact> months = twelveZeroMonths();
        months = new ArrayList<>(months);
        months.set(0, first);
        return AverageEarningsParagraph5MoneyPolicy.resolve(EVENT, months, exclusions);
    }

    private List<AverageEarningsNumeratorFactsService.MonthFact> twelveZeroMonths() {
        List<AverageEarningsNumeratorFactsService.MonthFact> months = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            months.add(AverageEarningsNumeratorFactsService.MonthFact.notEmployed(FROM.plusMonths(i)));
        }
        return months;
    }

    private AverageEarningsNumeratorFactsService.MonthFact employedMonth(
            int offset,
            List<AverageEarningsNumeratorFactsService.EarningFact> earnings,
            long ordinary
    ) {
        YearMonth month = FROM.plusMonths(offset);
        return AverageEarningsNumeratorFactsService.MonthFact.employed(
                month,
                List.of(new AverageEarningsNumeratorFactsService.EmploymentCoverageFact(
                        1L,
                        FROM.atDay(1),
                        null,
                        month.atDay(1),
                        month.atEndOfMonth()
                )),
                1,
                "RUB",
                earnings,
                ordinary,
                earnings.stream().filter(e -> e.treatment() == PREMIUM_SPECIAL_RULE)
                        .mapToLong(AverageEarningsNumeratorFactsService.EarningFact::amountMinor).sum(),
                earnings.stream().filter(e -> e.treatment() == AverageEarningsLegalPolicy.EarningTreatment.EXCLUDE_PRESERVED_AVERAGE)
                        .mapToLong(AverageEarningsNumeratorFactsService.EarningFact::amountMinor).sum()
        );
    }

    private AverageEarningsNumeratorFactsService.EarningFact ordinary(
            long amount,
            LocalDate earningFrom,
            LocalDate earningTo,
            LocalDate coverageFrom,
            LocalDate coverageTo
    ) {
        return earning(PayrollEarningKind.BASE_PAY, amount, ORDINARY_REMUNERATION,
                earningFrom, earningTo, coverageFrom, coverageTo);
    }

    private AverageEarningsNumeratorFactsService.EarningFact earning(
            PayrollEarningKind kind,
            long amount,
            AverageEarningsLegalPolicy.EarningTreatment treatment,
            LocalDate earningFrom,
            LocalDate earningTo,
            LocalDate coverageFrom,
            LocalDate coverageTo
    ) {
        return new AverageEarningsNumeratorFactsService.EarningFact(
                kind,
                kind.phase(),
                amount,
                null,
                earningFrom,
                earningTo,
                coverageFrom,
                coverageTo,
                treatment,
                treatment == ORDINARY_REMUNERATION ? PP_540_P2 :
                        treatment == PREMIUM_SPECIAL_RULE
                                ? AverageEarningsLegalPolicy.LegalBasis.PP_540_P2_AND_P15
                                : PP_540_P5_A
        );
    }

    private AverageEarningsBonusP15ReferenceCompletenessService.Paragraph5Exclusion exclusion(
            LocalDate from,
            LocalDate to
    ) {
        return new AverageEarningsBonusP15ReferenceCompletenessService.Paragraph5Exclusion(
                Math.abs(from.toEpochDay()) + 1,
                "VACATION",
                EXCLUDE_PRESERVED_AVERAGE,
                PP_540_P5_A,
                from,
                to,
                null
        );
    }
}
