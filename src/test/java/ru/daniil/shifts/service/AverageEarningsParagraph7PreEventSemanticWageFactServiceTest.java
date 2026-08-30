package ru.daniil.shifts.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.PayrollEarningKind;
import ru.daniil.shifts.service.PayrollBonusSourceFactService.BonusFact;
import ru.daniil.shifts.service.PayrollCombinationEpisodeFactService.EpisodeFact;
import ru.daniil.shifts.service.PayrollRegionalCoefficientSourceFactService.SourceFact;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AverageEarningsParagraph7PreEventSemanticWageFactServiceTest {
    private static final LocalDate EVENT = LocalDate.of(2026, 8, 20);
    private static final LocalDate FROM = LocalDate.of(2026, 8, 1);
    private static final YearMonth MONTH = YearMonth.of(2026, 8);

    @Mock PayrollCombinationEpisodeFactService combination;
    @Mock PayrollRegionalCoefficientSourceFactService regional;
    @Mock PayrollBonusSourceFactService bonuses;
    @Mock AppUser user;

    private AverageEarningsParagraph7PreEventSemanticWageFactService service;

    @BeforeEach
    void setUp() {
        service = new AverageEarningsParagraph7PreEventSemanticWageFactService(
                combination,
                regional,
                bonuses
        );
    }

    @Test
    void nullUserRejected() {
        assertThrows(NullPointerException.class, () -> service.resolve(null, basePay(true, 10L)));
    }

    @Test
    void nullBasePayRejected() {
        assertThrows(NullPointerException.class, () -> service.resolve(user, null));
    }

    @Test
    void noWorkedTimeReturnsEmptyWithoutReadingSemanticSources() {
        var result = service.resolve(user, basePay(false, 0L));
        assertTrue(result.ready());
        assertFalse(result.hasObservedFacts());
        assertTrue(result.observedFacts().isEmpty());
        verifyNoInteractions(combination, regional, bonuses);
    }

    @Test
    void queriesOnlyLegalEventMonth() {
        readyEmptySources();
        service.resolve(user, basePay(true, 10L));
        verify(combination).resolveMonth(user, MONTH);
        verify(regional).resolveMonth(user, MONTH);
        verify(bonuses).resolveMonth(user, MONTH);
    }

    @Test
    void preEventCombinationFactIsPreservedExactly() {
        when(combination.resolveMonth(user, MONTH)).thenReturn(List.of(
                combination(11L, 41L, 2, 10, 2_400L, 400_000L, "RUB")
        ));
        emptyRegionalAndBonus();

        var result = service.resolve(user, basePay(true, 10L));
        assertTrue(result.ready());
        assertTrue(result.hasObservedFacts());
        var fact = result.observedFacts().get(0);
        assertEquals(PayrollEarningKind.COMBINATION, fact.earningKind());
        assertEquals(400_000L, fact.amountMinor());
        assertEquals(2_400L, fact.qualifiedMinutes());
        assertEquals(2_500, fact.agreedRateBps());
        assertEquals(AverageEarningsParagraph7PreEventSemanticWageFactService.SourceAuthority.COMBINATION_EPISODE, fact.sourceAuthority());
    }

    @Test
    void preEventRegionalFactIsPreservedExactly() {
        when(combination.resolveMonth(user, MONTH)).thenReturn(List.of());
        when(regional.resolveMonth(user, MONTH)).thenReturn(List.of(
                regional(12L, 42L, 1, 19, 150_000L, "RUB")
        ));
        when(bonuses.resolveMonth(user, MONTH)).thenReturn(List.of());

        var fact = service.resolve(user, basePay(true, 10L)).observedFacts().get(0);
        assertEquals(PayrollEarningKind.REGIONAL_COEFFICIENT, fact.earningKind());
        assertEquals(150_000L, fact.amountMinor());
        assertNull(fact.qualifiedMinutes());
        assertNull(fact.agreedRateBps());
    }

    @Test
    void preEventMonthlyBonusFactIsPreservedExactly() {
        when(combination.resolveMonth(user, MONTH)).thenReturn(List.of());
        when(regional.resolveMonth(user, MONTH)).thenReturn(List.of());
        when(bonuses.resolveMonth(user, MONTH)).thenReturn(List.of(
                bonus(13L, 43L, PayrollEarningKind.MONTHLY_BONUS, 1, 15, 300_000L, "RUB")
        ));

        var fact = service.resolve(user, basePay(true, 10L)).observedFacts().get(0);
        assertEquals(PayrollEarningKind.MONTHLY_BONUS, fact.earningKind());
        assertEquals(300_000L, fact.amountMinor());
    }

    @Test
    void preEventOneTimeBonusFactIsPreservedExactly() {
        when(combination.resolveMonth(user, MONTH)).thenReturn(List.of());
        when(regional.resolveMonth(user, MONTH)).thenReturn(List.of());
        when(bonuses.resolveMonth(user, MONTH)).thenReturn(List.of(
                bonus(14L, 44L, PayrollEarningKind.ONE_TIME_BONUS, 7, 7, 75_000L, "RUB")
        ));

        var fact = service.resolve(user, basePay(true, 10L)).observedFacts().get(0);
        assertEquals(PayrollEarningKind.ONE_TIME_BONUS, fact.earningKind());
        assertEquals(LocalDate.of(2026, 8, 7), fact.periodFrom());
        assertEquals(LocalDate.of(2026, 8, 7), fact.periodTo());
    }

    @Test
    void fullyFutureFactsAreIgnoredWithoutInventingPreEventMoney() {
        when(combination.resolveMonth(user, MONTH)).thenReturn(List.of(
                combination(11L, 41L, 21, 25, 1_200L, 200_000L, "RUB")
        ));
        when(regional.resolveMonth(user, MONTH)).thenReturn(List.of(
                regional(12L, 42L, 22, 30, 50_000L, "RUB")
        ));
        when(bonuses.resolveMonth(user, MONTH)).thenReturn(List.of(
                bonus(13L, 43L, PayrollEarningKind.MONTHLY_BONUS, 20, 31, 300_000L, "RUB")
        ));

        var result = service.resolve(user, basePay(true, 10L));
        assertTrue(result.ready());
        assertTrue(result.observedFacts().isEmpty());
    }

    @Test
    void factStartingOnEventDateIsFutureAndIgnored() {
        when(combination.resolveMonth(user, MONTH)).thenReturn(List.of(
                combination(11L, 41L, 20, 20, 60L, 10_000L, "RUB")
        ));
        emptyRegionalAndBonus();

        var result = service.resolve(user, basePay(true, 10L));
        assertTrue(result.ready());
        assertTrue(result.observedFacts().isEmpty());
    }

    @Test
    void combinationPeriodCrossingEventBlocksInsteadOfProratingMoney() {
        when(combination.resolveMonth(user, MONTH)).thenReturn(List.of(
                combination(11L, 41L, 10, 20, 2_400L, 400_000L, "RUB")
        ));
        emptyRegionalAndBonus();

        var result = service.resolve(user, basePay(true, 10L));
        assertFalse(result.ready());
        assertEquals(AverageEarningsParagraph7PreEventSemanticWageFactService.SOURCE_PERIOD_CROSSES_EVENT, result.blockingReason());
        assertTrue(result.observedFacts().isEmpty());
    }

    @Test
    void regionalPeriodCrossingEventBlocksInsteadOfProratingMoney() {
        when(combination.resolveMonth(user, MONTH)).thenReturn(List.of());
        when(regional.resolveMonth(user, MONTH)).thenReturn(List.of(
                regional(12L, 42L, 1, 31, 150_000L, "RUB")
        ));
        when(bonuses.resolveMonth(user, MONTH)).thenReturn(List.of());

        var result = service.resolve(user, basePay(true, 10L));
        assertFalse(result.ready());
        assertEquals(AverageEarningsParagraph7PreEventSemanticWageFactService.SOURCE_PERIOD_CROSSES_EVENT, result.blockingReason());
    }

    @Test
    void bonusPeriodCrossingEventBlocksInsteadOfProratingMoney() {
        when(combination.resolveMonth(user, MONTH)).thenReturn(List.of());
        when(regional.resolveMonth(user, MONTH)).thenReturn(List.of());
        when(bonuses.resolveMonth(user, MONTH)).thenReturn(List.of(
                bonus(13L, 43L, PayrollEarningKind.MONTHLY_BONUS, 1, 31, 300_000L, "RUB")
        ));

        var result = service.resolve(user, basePay(true, 10L));
        assertFalse(result.ready());
        assertEquals(AverageEarningsParagraph7PreEventSemanticWageFactService.SOURCE_PERIOD_CROSSES_EVENT, result.blockingReason());
    }

    @Test
    void preEventCurrencyMismatchBlocksFailClosed() {
        when(combination.resolveMonth(user, MONTH)).thenReturn(List.of(
                combination(11L, 41L, 2, 10, 2_400L, 400_000L, "USD")
        ));
        emptyRegionalAndBonus();

        var result = service.resolve(user, basePay(true, 10L));
        assertFalse(result.ready());
        assertEquals(AverageEarningsParagraph7PreEventSemanticWageFactService.SOURCE_CURRENCY_MISMATCH, result.blockingReason());
        assertTrue(result.observedFacts().isEmpty());
    }

    @Test
    void futureCurrencyMismatchDoesNotPollutePreEventAuthority() {
        when(combination.resolveMonth(user, MONTH)).thenReturn(List.of(
                combination(11L, 41L, 21, 25, 1_200L, 200_000L, "USD")
        ));
        emptyRegionalAndBonus();

        var result = service.resolve(user, basePay(true, 10L));
        assertTrue(result.ready());
        assertTrue(result.observedFacts().isEmpty());
    }

    @Test
    void oneAmbiguousFactClearsEarlierAcceptedFacts() {
        when(combination.resolveMonth(user, MONTH)).thenReturn(List.of(
                combination(11L, 41L, 2, 10, 2_400L, 400_000L, "RUB")
        ));
        when(regional.resolveMonth(user, MONTH)).thenReturn(List.of(
                regional(12L, 42L, 1, 31, 150_000L, "RUB")
        ));
        when(bonuses.resolveMonth(user, MONTH)).thenReturn(List.of());

        var result = service.resolve(user, basePay(true, 10L));
        assertFalse(result.ready());
        assertTrue(result.observedFacts().isEmpty());
    }

    @Test
    void observedFactsAreDeterministicallyOrderedAcrossSourceAuthorities() {
        when(combination.resolveMonth(user, MONTH)).thenReturn(List.of(
                combination(21L, 41L, 10, 12, 480L, 40_000L, "RUB")
        ));
        when(regional.resolveMonth(user, MONTH)).thenReturn(List.of(
                regional(22L, 42L, 2, 3, 15_000L, "RUB")
        ));
        when(bonuses.resolveMonth(user, MONTH)).thenReturn(List.of(
                bonus(23L, 43L, PayrollEarningKind.ONE_TIME_BONUS, 5, 5, 25_000L, "RUB")
        ));

        var result = service.resolve(user, basePay(true, 10L));
        assertEquals(List.of(
                PayrollEarningKind.REGIONAL_COEFFICIENT,
                PayrollEarningKind.ONE_TIME_BONUS,
                PayrollEarningKind.COMBINATION
        ), result.observedFacts().stream().map(
                AverageEarningsParagraph7PreEventSemanticWageFactService.SemanticWageFact::earningKind
        ).toList());
    }

    @Test
    void zeroBasePayWithWorkedTimeStillDiscoversObservedSemanticFacts() {
        readyEmptySources();
        var basePay = basePay(true, 0L);
        var result = service.resolve(user, basePay);
        assertTrue(result.ready());
        verify(combination).resolveMonth(user, MONTH);
        verify(regional).resolveMonth(user, MONTH);
        verify(bonuses).resolveMonth(user, MONTH);
    }

    @Test
    void nullSourceAuthorityResultIsStructuralFailureNotSyntheticEmptyEvidence() {
        when(combination.resolveMonth(user, MONTH)).thenReturn(null);
        assertThrows(
                NullPointerException.class,
                () -> service.resolve(user, basePay(true, 10L))
        );
    }

    private void readyEmptySources() {
        when(combination.resolveMonth(user, MONTH)).thenReturn(List.of());
        when(regional.resolveMonth(user, MONTH)).thenReturn(List.of());
        when(bonuses.resolveMonth(user, MONTH)).thenReturn(List.of());
    }

    private void emptyRegionalAndBonus() {
        when(regional.resolveMonth(user, MONTH)).thenReturn(List.of());
        when(bonuses.resolveMonth(user, MONTH)).thenReturn(List.of());
    }

    private AverageEarningsParagraph7PreEventBasePayFormula.Calculation basePay(
            boolean worked,
            long amountMinor
    ) {
        var authority = mock(AverageEarningsParagraph7PreEventBasePayAuthorityService.Resolution.class);
        lenient().when(authority.ready()).thenReturn(true);
        lenient().when(authority.eventDate()).thenReturn(EVENT);
        lenient().when(authority.periodFrom()).thenReturn(FROM);
        lenient().when(authority.cutoffExclusive()).thenReturn(EVENT);
        lenient().when(authority.workedTimePresent()).thenReturn(worked);

        var calculation = mock(AverageEarningsParagraph7PreEventBasePayFormula.Calculation.class);
        lenient().when(calculation.authority()).thenReturn(authority);
        lenient().when(calculation.basePayAmountMinor()).thenReturn(amountMinor);
        lenient().when(calculation.currencyCode()).thenReturn(worked ? "RUB" : null);
        return calculation;
    }

    private EpisodeFact combination(
            long factId,
            long componentId,
            int fromDay,
            int toDay,
            long minutes,
            long amount,
            String currency
    ) {
        return new EpisodeFact(
                factId,
                componentId,
                LocalDate.of(2026, 8, fromDay),
                LocalDate.of(2026, 8, toDay),
                minutes,
                amount,
                currency,
                2_500
        );
    }

    private SourceFact regional(
            long factId,
            long componentId,
            int fromDay,
            int toDay,
            long amount,
            String currency
    ) {
        return new SourceFact(
                factId,
                componentId,
                LocalDate.of(2026, 8, fromDay),
                LocalDate.of(2026, 8, toDay),
                amount,
                currency
        );
    }

    private BonusFact bonus(
            long factId,
            long componentId,
            PayrollEarningKind kind,
            int fromDay,
            int toDay,
            long amount,
            String currency
    ) {
        return new BonusFact(
                factId,
                componentId,
                kind,
                LocalDate.of(2026, 8, fromDay),
                LocalDate.of(2026, 8, toDay),
                amount,
                currency
        );
    }
}
