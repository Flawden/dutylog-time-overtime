package ru.daniil.shifts.service;

import org.junit.jupiter.api.Test;
import ru.daniil.shifts.model.PayrollEarningKind;
import ru.daniil.shifts.service.AverageEarningsParagraph7PreEventAccruedWageAuthority.BlockingSource;
import ru.daniil.shifts.service.AverageEarningsParagraph7PreEventSemanticWageFactService.SemanticWageFact;
import ru.daniil.shifts.service.AverageEarningsParagraph7PreEventSemanticWageFactService.SourceAuthority;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

class AverageEarningsParagraph7PreEventAccruedWageAuthorityTest {
    private static final LocalDate EVENT = LocalDate.of(2026, 8, 20);
    private static final LocalDate FROM = LocalDate.of(2026, 8, 1);

    @Test
    void nullHarmfulAuthorityIsRejected() {
        assertThrows(NullPointerException.class, () ->
                AverageEarningsParagraph7PreEventAccruedWageAuthority.resolve(
                        null,
                        mock(AverageEarningsParagraph7PreEventBonusP15Formula.Calculation.class)
                ));
    }

    @Test
    void nullBonusAuthorityIsRejected() {
        assertThrows(NullPointerException.class, () ->
                AverageEarningsParagraph7PreEventAccruedWageAuthority.resolve(
                        mock(AverageEarningsParagraph7PreEventHarmfulCompensationService.Resolution.class),
                        null
                ));
    }

    @Test
    void harmfulBlockerPropagatesWithoutPartialMoney() {
        var harmful = blockedHarmful("HARMFUL_BLOCK", "harmful blocked");
        var bonus = mock(AverageEarningsParagraph7PreEventBonusP15Formula.Calculation.class);

        var result = AverageEarningsParagraph7PreEventAccruedWageAuthority.resolve(harmful, bonus);

        assertFalse(result.ready());
        assertEquals("HARMFUL_BLOCK", result.blockingReason());
        assertEquals(BlockingSource.HARMFUL_AUTHORITY, result.blockingSource());
        assertEquals(0L, result.totalAccruedWageMinor());
        assertNull(result.currencyCode());
    }

    @Test
    void blankHarmfulBlockerNormalizesToUpstreamStateContradiction() {
        var harmful = blockedHarmful(" ", "harmful blocked");
        var bonus = mock(AverageEarningsParagraph7PreEventBonusP15Formula.Calculation.class);

        var result = AverageEarningsParagraph7PreEventAccruedWageAuthority.resolve(harmful, bonus);

        assertEquals(
                AverageEarningsParagraph7PreEventAccruedWageAuthority.UPSTREAM_STATE_CONTRADICTION,
                result.blockingReason()
        );
    }

    @Test
    void readyHarmfulCannotHideBlockedOrdinaryProvenance() {
        var bundle = bundle(0, 0, 0, List.of(), 0, null, false, true);
        doReturn(false).when(bundle.ordinary()).ready();

        var result = AverageEarningsParagraph7PreEventAccruedWageAuthority.resolve(
                bundle.harmful(), bundle.bonus());

        assertFalse(result.ready());
        assertEquals(
                AverageEarningsParagraph7PreEventAccruedWageAuthority.UPSTREAM_STATE_CONTRADICTION,
                result.blockingReason()
        );
    }

    @Test
    void readyHarmfulCannotHideBlockedSemanticProvenance() {
        var bundle = bundle(0, 0, 0, List.of(), 0, null, false, true);
        doReturn(false).when(bundle.semantic()).ready();

        var result = AverageEarningsParagraph7PreEventAccruedWageAuthority.resolve(
                bundle.harmful(), bundle.bonus());

        assertFalse(result.ready());
        assertEquals(
                AverageEarningsParagraph7PreEventAccruedWageAuthority.UPSTREAM_STATE_CONTRADICTION,
                result.blockingReason()
        );
    }

    @Test
    void readyHarmfulCannotHideBlockedBaseAuthority() {
        var bundle = bundle(0, 0, 0, List.of(), 0, null, false, true);
        doReturn(false).when(bundle.baseAuthority()).ready();

        var result = AverageEarningsParagraph7PreEventAccruedWageAuthority.resolve(
                bundle.harmful(), bundle.bonus());

        assertFalse(result.ready());
        assertEquals(
                AverageEarningsParagraph7PreEventAccruedWageAuthority.UPSTREAM_STATE_CONTRADICTION,
                result.blockingReason()
        );
    }

    @Test
    void harmfulChainWindowMismatchBlocksAggregation() {
        var bundle = bundle(100, 0, 0, List.of(), 0, "RUB", true, true);
        doReturn(EVENT.plusDays(1)).when(bundle.ordinary()).cutoffExclusive();

        var result = AverageEarningsParagraph7PreEventAccruedWageAuthority.resolve(
                bundle.harmful(), bundle.bonus());

        assertFalse(result.ready());
        assertEquals(
                AverageEarningsParagraph7PreEventAccruedWageAuthority.AUTHORITY_WINDOW_MISMATCH,
                result.blockingReason()
        );
    }

    @Test
    void bonusWindowMismatchBlocksAggregation() {
        var bundle = bundle(100, 0, 0, List.of(), 0, "RUB", true, true);
        doReturn(EVENT.plusDays(1)).when(bundle.bonus()).cutoffExclusive();

        var result = AverageEarningsParagraph7PreEventAccruedWageAuthority.resolve(
                bundle.harmful(), bundle.bonus());

        assertFalse(result.ready());
        assertEquals(
                AverageEarningsParagraph7PreEventAccruedWageAuthority.AUTHORITY_WINDOW_MISMATCH,
                result.blockingReason()
        );
    }

    @Test
    void bonusBlockerPropagatesBeforeAnyMoneyIsExposed() {
        var bundle = bundle(100, 20, 4, List.of(), 0, "RUB", true, false);
        doReturn("BONUS_BLOCK").when(bundle.bonus()).blockingReason();

        var result = AverageEarningsParagraph7PreEventAccruedWageAuthority.resolve(
                bundle.harmful(), bundle.bonus());

        assertFalse(result.ready());
        assertEquals("BONUS_BLOCK", result.blockingReason());
        assertEquals(BlockingSource.BONUS_AUTHORITY, result.blockingSource());
        assertEquals(0L, result.basePayAmountMinor());
        assertEquals(0L, result.totalAccruedWageMinor());
    }

    @Test
    void bonusMustOriginateFromSameSemanticAuthority() {
        var bundle = bundle(100, 0, 0, List.of(), 10, "RUB", true, true);
        var otherSemantic = mock(AverageEarningsParagraph7PreEventSemanticWageFactService.Resolution.class);
        doReturn(otherSemantic).when(bundle.preEventBonusFacts()).semanticFacts();

        var result = AverageEarningsParagraph7PreEventAccruedWageAuthority.resolve(
                bundle.harmful(), bundle.bonus());

        assertFalse(result.ready());
        assertEquals(
                AverageEarningsParagraph7PreEventAccruedWageAuthority.PROVENANCE_MISMATCH,
                result.blockingReason()
        );
    }

    @Test
    void noWorkAndNoMoneyIsReadyZeroWithoutCurrency() {
        var bundle = bundle(0, 0, 0, List.of(), 0, null, false, true);

        var result = AverageEarningsParagraph7PreEventAccruedWageAuthority.resolve(
                bundle.harmful(), bundle.bonus());

        assertTrue(result.ready());
        assertFalse(result.workedTimePresent());
        assertFalse(result.accruedWagePresent());
        assertEquals(0L, result.totalAccruedWageMinor());
        assertNull(result.currencyCode());
    }

    @Test
    void basePayMoneyReachesAccruedWageTotal() {
        var bundle = bundle(100, 0, 0, List.of(), 0, "RUB", true, true);

        var result = AverageEarningsParagraph7PreEventAccruedWageAuthority.resolve(
                bundle.harmful(), bundle.bonus());

        assertTrue(result.ready());
        assertEquals(100L, result.basePayAmountMinor());
        assertEquals(100L, result.totalAccruedWageMinor());
        assertEquals("RUB", result.currencyCode());
    }

    @Test
    void ordinaryPremiumMoneyReachesAccruedWageTotal() {
        var bundle = bundle(100, 25, 0, List.of(), 0, "RUB", true, true);

        var result = AverageEarningsParagraph7PreEventAccruedWageAuthority.resolve(
                bundle.harmful(), bundle.bonus());

        assertEquals(25L, result.ordinaryPremiumAmountMinor());
        assertEquals(125L, result.totalAccruedWageMinor());
    }

    @Test
    void harmfulMoneyReachesAccruedWageTotal() {
        var bundle = bundle(100, 0, 4, List.of(), 0, "RUB", true, true);

        var result = AverageEarningsParagraph7PreEventAccruedWageAuthority.resolve(
                bundle.harmful(), bundle.bonus());

        assertEquals(4L, result.harmfulAmountMinor());
        assertEquals(104L, result.totalAccruedWageMinor());
    }

    @Test
    void combinationFactsAreSummedAsExplicitAccruedMoney() {
        var facts = List.of(
                combination(1, 30, "RUB"),
                combination(2, 40, "RUB")
        );
        var bundle = bundle(100, 0, 0, facts, 0, "RUB", true, true);

        var result = AverageEarningsParagraph7PreEventAccruedWageAuthority.resolve(
                bundle.harmful(), bundle.bonus());

        assertEquals(70L, result.combinationAmountMinor());
        assertEquals(170L, result.totalAccruedWageMinor());
    }

    @Test
    void regionalFactsAreSummedAsExplicitAccruedMoney() {
        var facts = List.of(
                regional(1, 15, "RUB"),
                regional(2, 20, "RUB")
        );
        var bundle = bundle(100, 0, 0, facts, 0, "RUB", true, true);

        var result = AverageEarningsParagraph7PreEventAccruedWageAuthority.resolve(
                bundle.harmful(), bundle.bonus());

        assertEquals(35L, result.regionalCoefficientAmountMinor());
        assertEquals(135L, result.totalAccruedWageMinor());
    }

    @Test
    void rawBonusSemanticMoneyIsNeverAddedDirectly() {
        var facts = List.of(rawBonus(1, 999, "RUB"));
        var bundle = bundle(100, 0, 0, facts, 0, "RUB", true, true);

        var result = AverageEarningsParagraph7PreEventAccruedWageAuthority.resolve(
                bundle.harmful(), bundle.bonus());

        assertEquals(0L, result.bonusP15AmountMinor());
        assertEquals(100L, result.totalAccruedWageMinor());
    }

    @Test
    void processedP15BonusIsIncludedExactlyOnce() {
        var facts = List.of(rawBonus(1, 999, "RUB"));
        var bundle = bundle(100, 0, 0, facts, 50, "RUB", true, true);

        var result = AverageEarningsParagraph7PreEventAccruedWageAuthority.resolve(
                bundle.harmful(), bundle.bonus());

        assertEquals(50L, result.bonusP15AmountMinor());
        assertEquals(150L, result.totalAccruedWageMinor());
    }

    @Test
    void allSixMoneyBucketsAssembleOneExactNumerator() {
        var facts = List.of(
                combination(1, 30, "RUB"),
                combination(2, 40, "RUB"),
                regional(3, 15, "RUB"),
                rawBonus(4, 999, "RUB")
        );
        var bundle = bundle(100, 20, 4, facts, 50, "RUB", true, true);

        var result = AverageEarningsParagraph7PreEventAccruedWageAuthority.resolve(
                bundle.harmful(), bundle.bonus());

        assertTrue(result.ready());
        assertEquals(100L, result.basePayAmountMinor());
        assertEquals(20L, result.ordinaryPremiumAmountMinor());
        assertEquals(4L, result.harmfulAmountMinor());
        assertEquals(70L, result.combinationAmountMinor());
        assertEquals(15L, result.regionalCoefficientAmountMinor());
        assertEquals(50L, result.bonusP15AmountMinor());
        assertEquals(259L, result.totalAccruedWageMinor());
    }

    @Test
    void ordinaryPremiumCurrencyMustMatchCanonicalCurrency() {
        var bundle = bundle(100, 20, 0, List.of(), 0, "RUB", true, true);
        doReturn("USD").when(bundle.ordinary()).currencyCode();

        var result = AverageEarningsParagraph7PreEventAccruedWageAuthority.resolve(
                bundle.harmful(), bundle.bonus());

        assertFalse(result.ready());
        assertEquals(
                AverageEarningsParagraph7PreEventAccruedWageAuthority.CURRENCY_MISMATCH,
                result.blockingReason()
        );
    }

    @Test
    void harmfulCurrencyMustMatchCanonicalCurrency() {
        var bundle = bundle(100, 0, 4, List.of(), 0, "RUB", true, true);
        doReturn("USD").when(bundle.harmful()).currencyCode();

        var result = AverageEarningsParagraph7PreEventAccruedWageAuthority.resolve(
                bundle.harmful(), bundle.bonus());

        assertFalse(result.ready());
        assertEquals(
                AverageEarningsParagraph7PreEventAccruedWageAuthority.CURRENCY_MISMATCH,
                result.blockingReason()
        );
    }

    @Test
    void semanticMoneyCurrencyMustMatchCanonicalCurrency() {
        var facts = List.of(combination(1, 30, "USD"));
        var bundle = bundle(100, 0, 0, facts, 0, "RUB", true, true);

        var result = AverageEarningsParagraph7PreEventAccruedWageAuthority.resolve(
                bundle.harmful(), bundle.bonus());

        assertFalse(result.ready());
        assertEquals(
                AverageEarningsParagraph7PreEventAccruedWageAuthority.CURRENCY_MISMATCH,
                result.blockingReason()
        );
    }

    @Test
    void processedBonusCurrencyMustMatchCanonicalCurrency() {
        var bundle = bundle(100, 0, 0, List.of(), 10, "RUB", true, true);
        doReturn("USD").when(bundle.bonus()).currencyCode();

        var result = AverageEarningsParagraph7PreEventAccruedWageAuthority.resolve(
                bundle.harmful(), bundle.bonus());

        assertFalse(result.ready());
        assertEquals(
                AverageEarningsParagraph7PreEventAccruedWageAuthority.CURRENCY_MISMATCH,
                result.blockingReason()
        );
    }

    @Test
    void positiveMoneyWithoutAnyCurrencyBlocksFailClosed() {
        var bundle = bundle(100, 0, 0, List.of(), 0, null, false, true);

        var result = AverageEarningsParagraph7PreEventAccruedWageAuthority.resolve(
                bundle.harmful(), bundle.bonus());

        assertFalse(result.ready());
        assertEquals(
                AverageEarningsParagraph7PreEventAccruedWageAuthority.CURRENCY_MISMATCH,
                result.blockingReason()
        );
        assertEquals(0L, result.totalAccruedWageMinor());
    }

    @Test
    void totalOverflowBlocksInsteadOfWrapping() {
        var bundle = bundle(Long.MAX_VALUE, 0, 0, List.of(), 1, "RUB", true, true);

        var result = AverageEarningsParagraph7PreEventAccruedWageAuthority.resolve(
                bundle.harmful(), bundle.bonus());

        assertFalse(result.ready());
        assertEquals(
                AverageEarningsParagraph7PreEventAccruedWageAuthority.TOTAL_OVERFLOW,
                result.blockingReason()
        );
        assertEquals(0L, result.totalAccruedWageMinor());
    }

    @Test
    void workedTimeFactsRemainAvailableForLaterFallbackAndDenominatorLayers() {
        var bundle = bundle(100, 0, 0, List.of(), 0, "RUB", true, true);
        doReturn(3).when(bundle.workFacts()).workedDayCount();
        doReturn(1_440L).when(bundle.workFacts()).workedMinutes();

        var result = AverageEarningsParagraph7PreEventAccruedWageAuthority.resolve(
                bundle.harmful(), bundle.bonus());

        assertTrue(result.workedTimePresent());
        assertEquals(3, result.workedDayCount());
        assertEquals(1_440L, result.workedMinutes());
    }

    @Test
    void accruedWagePresenceIsIndependentFromWorkedTimePresence() {
        var bundle = bundle(0, 0, 0, List.of(), 10, "RUB", false, true);

        var result = AverageEarningsParagraph7PreEventAccruedWageAuthority.resolve(
                bundle.harmful(), bundle.bonus());

        assertTrue(result.ready());
        assertFalse(result.workedTimePresent());
        assertTrue(result.accruedWagePresent());
        assertEquals(10L, result.totalAccruedWageMinor());
    }

    @Test
    void readyResultRetainsBothExactUpstreamAuthorities() {
        var bundle = bundle(100, 0, 0, List.of(), 0, "RUB", true, true);

        var result = AverageEarningsParagraph7PreEventAccruedWageAuthority.resolve(
                bundle.harmful(), bundle.bonus());

        assertSame(bundle.harmful(), result.harmfulAuthority());
        assertSame(bundle.bonus(), result.bonusAuthority());
    }

    @Test
    void blockedResolutionCannotExposePartialMoney() {
        var harmful = blockedHarmful("BLOCK", "blocked");
        var bonus = mock(AverageEarningsParagraph7PreEventBonusP15Formula.Calculation.class);

        assertThrows(IllegalArgumentException.class, () ->
                new AverageEarningsParagraph7PreEventAccruedWageAuthority.Resolution(
                        EVENT,
                        FROM,
                        EVENT,
                        false,
                        "BLOCK",
                        "blocked",
                        BlockingSource.AGGREGATE_AUTHORITY,
                        harmful,
                        bonus,
                        "RUB",
                        1,
                        0,
                        0,
                        0,
                        0,
                        0,
                        1
                ));
    }

    @Test
    void readyResolutionRejectsComponentTotalContradiction() {
        var bundle = bundle(100, 0, 0, List.of(), 0, "RUB", true, true);

        assertThrows(IllegalArgumentException.class, () ->
                new AverageEarningsParagraph7PreEventAccruedWageAuthority.Resolution(
                        EVENT,
                        FROM,
                        EVENT,
                        true,
                        null,
                        null,
                        null,
                        bundle.harmful(),
                        bundle.bonus(),
                        "RUB",
                        100,
                        0,
                        0,
                        0,
                        0,
                        0,
                        99
                ));
    }

    private static Bundle bundle(
            long baseMinor,
            long ordinaryMinor,
            long harmfulMinor,
            List<SemanticWageFact> facts,
            long bonusMinor,
            String currency,
            boolean workedTimePresent,
            boolean bonusReady
    ) {
        var workFacts = mock(AverageEarningsParagraph7PreEventWorkFactService.Resolution.class);
        doReturn(workedTimePresent).when(workFacts).workedTimePresent();
        doReturn(workedTimePresent ? 1 : 0).when(workFacts).workedDayCount();
        doReturn(workedTimePresent ? 480L : 0L).when(workFacts).workedMinutes();

        var baseAuthority = mock(AverageEarningsParagraph7PreEventBasePayAuthorityService.Resolution.class);
        window(baseAuthority);
        doReturn(true).when(baseAuthority).ready();
        doReturn(workedTimePresent).when(baseAuthority).workedTimePresent();
        doReturn(workFacts).when(baseAuthority).workFacts();

        String baseCurrency = workedTimePresent ? currency : null;
        var basePay = mock(AverageEarningsParagraph7PreEventBasePayFormula.Calculation.class);
        doReturn(baseAuthority).when(basePay).authority();
        doReturn(baseMinor).when(basePay).basePayAmountMinor();
        doReturn(baseCurrency).when(basePay).currencyCode();

        var semantic = mock(AverageEarningsParagraph7PreEventSemanticWageFactService.Resolution.class);
        window(semantic);
        doReturn(true).when(semantic).ready();
        doReturn(basePay).when(semantic).basePay();
        doReturn(facts).when(semantic).observedFacts();

        var ordinary = mock(AverageEarningsParagraph7PreEventOrdinaryPremiumService.Resolution.class);
        window(ordinary);
        doReturn(true).when(ordinary).ready();
        doReturn(semantic).when(ordinary).semanticFacts();
        doReturn(ordinaryMinor).when(ordinary).ordinaryPremiumAmountMinor();
        doReturn(baseCurrency).when(ordinary).currencyCode();

        var harmful = mock(AverageEarningsParagraph7PreEventHarmfulCompensationService.Resolution.class);
        window(harmful);
        doReturn(true).when(harmful).ready();
        doReturn(ordinary).when(harmful).ordinaryPremium();
        doReturn(harmfulMinor).when(harmful).harmfulAmountMinor();
        doReturn(baseCurrency).when(harmful).currencyCode();

        var preEventBonusFacts = mock(AverageEarningsParagraph7PreEventBonusP15FactService.Resolution.class);
        doReturn(semantic).when(preEventBonusFacts).semanticFacts();
        var accrual = mock(AverageEarningsParagraph7PreEventBonusAccrualAuthorityService.Resolution.class);
        doReturn(preEventBonusFacts).when(accrual).preEventFacts();
        var policy = mock(AverageEarningsParagraph7PreEventBonusP15Policy.Resolution.class);
        doReturn(accrual).when(policy).accrualAuthority();
        var workTime = mock(AverageEarningsParagraph7PreEventBonusWorkTimeFactService.Resolution.class);
        doReturn(policy).when(workTime).policy();

        var bonus = mock(AverageEarningsParagraph7PreEventBonusP15Formula.Calculation.class);
        window(bonus);
        doReturn(bonusReady).when(bonus).ready();
        doReturn(workTime).when(bonus).workTimeAuthority();
        doReturn(bonusMinor).when(bonus).includedPremiumAmountMinor();
        doReturn(bonusMinor > 0 ? currency : null).when(bonus).currencyCode();

        return new Bundle(
                workFacts,
                baseAuthority,
                semantic,
                ordinary,
                harmful,
                preEventBonusFacts,
                bonus
        );
    }

    private static AverageEarningsParagraph7PreEventHarmfulCompensationService.Resolution blockedHarmful(
            String reason,
            String message
    ) {
        var harmful = mock(AverageEarningsParagraph7PreEventHarmfulCompensationService.Resolution.class);
        window(harmful);
        doReturn(false).when(harmful).ready();
        doReturn(reason).when(harmful).blockingReason();
        doReturn(message).when(harmful).blockingMessage();
        return harmful;
    }

    private static SemanticWageFact combination(long id, long amount, String currency) {
        return new SemanticWageFact(
                SourceAuthority.COMBINATION_EPISODE,
                PayrollEarningKind.COMBINATION,
                id,
                100 + id,
                FROM,
                EVENT.minusDays(1),
                amount,
                currency,
                480L,
                2_500
        );
    }

    private static SemanticWageFact regional(long id, long amount, String currency) {
        return new SemanticWageFact(
                SourceAuthority.REGIONAL_SOURCE,
                PayrollEarningKind.REGIONAL_COEFFICIENT,
                id,
                200 + id,
                FROM,
                EVENT.minusDays(1),
                amount,
                currency,
                null,
                null
        );
    }

    private static SemanticWageFact rawBonus(long id, long amount, String currency) {
        return new SemanticWageFact(
                SourceAuthority.BONUS_SOURCE,
                PayrollEarningKind.MONTHLY_BONUS,
                id,
                300 + id,
                FROM,
                EVENT.minusDays(1),
                amount,
                currency,
                null,
                null
        );
    }

    private static void window(
            AverageEarningsParagraph7PreEventBasePayAuthorityService.Resolution value
    ) {
        doReturn(EVENT).when(value).eventDate();
        doReturn(FROM).when(value).periodFrom();
        doReturn(EVENT).when(value).cutoffExclusive();
    }

    private static void window(
            AverageEarningsParagraph7PreEventSemanticWageFactService.Resolution value
    ) {
        doReturn(EVENT).when(value).eventDate();
        doReturn(FROM).when(value).periodFrom();
        doReturn(EVENT).when(value).cutoffExclusive();
    }

    private static void window(
            AverageEarningsParagraph7PreEventOrdinaryPremiumService.Resolution value
    ) {
        doReturn(EVENT).when(value).eventDate();
        doReturn(FROM).when(value).periodFrom();
        doReturn(EVENT).when(value).cutoffExclusive();
    }

    private static void window(
            AverageEarningsParagraph7PreEventHarmfulCompensationService.Resolution value
    ) {
        doReturn(EVENT).when(value).eventDate();
        doReturn(FROM).when(value).periodFrom();
        doReturn(EVENT).when(value).cutoffExclusive();
    }

    private static void window(
            AverageEarningsParagraph7PreEventBonusP15Formula.Calculation value
    ) {
        doReturn(EVENT).when(value).eventDate();
        doReturn(FROM).when(value).periodFrom();
        doReturn(EVENT).when(value).cutoffExclusive();
    }

    private record Bundle(
            AverageEarningsParagraph7PreEventWorkFactService.Resolution workFacts,
            AverageEarningsParagraph7PreEventBasePayAuthorityService.Resolution baseAuthority,
            AverageEarningsParagraph7PreEventSemanticWageFactService.Resolution semantic,
            AverageEarningsParagraph7PreEventOrdinaryPremiumService.Resolution ordinary,
            AverageEarningsParagraph7PreEventHarmfulCompensationService.Resolution harmful,
            AverageEarningsParagraph7PreEventBonusP15FactService.Resolution preEventBonusFacts,
            AverageEarningsParagraph7PreEventBonusP15Formula.Calculation bonus
    ) {
    }
}
