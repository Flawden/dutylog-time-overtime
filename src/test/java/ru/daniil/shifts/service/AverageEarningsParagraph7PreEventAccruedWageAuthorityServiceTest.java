package ru.daniil.shifts.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.daniil.shifts.model.AppUser;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AverageEarningsParagraph7PreEventAccruedWageAuthorityServiceTest {
    private static final LocalDate EVENT = LocalDate.of(2026, 9, 15);
    private static final YearMonth DISCOVERY = YearMonth.of(2026, 8);

    private AverageEarningsParagraph7PreEventAccruedWageAuthorityService.BaseAuthorityResolver base;
    private AverageEarningsParagraph7PreEventAccruedWageAuthorityService.BaseFormulaCalculator baseFormula;
    private AverageEarningsParagraph7PreEventAccruedWageAuthorityService.SemanticResolver semantic;
    private AverageEarningsParagraph7PreEventAccruedWageAuthorityService.OrdinaryResolver ordinary;
    private AverageEarningsParagraph7PreEventAccruedWageAuthorityService.HarmfulResolver harmful;
    private AverageEarningsParagraph7PreEventAccruedWageAuthorityService.BonusFactResolver bonusFacts;
    private AverageEarningsParagraph7PreEventAccruedWageAuthorityService.BonusAccrualResolver bonusAccrual;
    private AverageEarningsParagraph7PreEventAccruedWageAuthorityService.BonusPolicyResolver bonusPolicy;
    private AverageEarningsParagraph7PreEventAccruedWageAuthorityService.BonusWorkTimeResolver bonusWorkTime;
    private AverageEarningsParagraph7PreEventAccruedWageAuthorityService.BonusFormulaCalculator bonusFormula;
    private AverageEarningsParagraph7PreEventAccruedWageAuthorityService.FinalResolver finalResolver;

    private AppUser user;
    private AverageEarningsParagraph7PreEventBasePayAuthorityService.Resolution baseResolution;
    private AverageEarningsParagraph7PreEventBasePayFormula.Calculation baseCalculation;
    private AverageEarningsParagraph7PreEventSemanticWageFactService.Resolution semanticResolution;
    private AverageEarningsParagraph7PreEventOrdinaryPremiumService.Resolution ordinaryResolution;
    private AverageEarningsParagraph7PreEventHarmfulCompensationService.Resolution harmfulResolution;
    private AverageEarningsParagraph7PreEventBonusP15FactService.Resolution bonusFactsResolution;
    private AverageEarningsParagraph7PreEventBonusAccrualAuthorityService.Resolution accrualResolution;
    private AverageEarningsParagraph7PreEventBonusP15Policy.Resolution policyResolution;
    private AverageEarningsParagraph7PreEventBonusWorkTimeFactService.Resolution workTimeResolution;
    private AverageEarningsParagraph7PreEventBonusP15Formula.Calculation bonusCalculation;
    private AverageEarningsParagraph7PreEventAccruedWageAuthority.Resolution finalResolution;

    @BeforeEach
    void setUp() {
        base = mock(AverageEarningsParagraph7PreEventAccruedWageAuthorityService.BaseAuthorityResolver.class);
        baseFormula = mock(AverageEarningsParagraph7PreEventAccruedWageAuthorityService.BaseFormulaCalculator.class);
        semantic = mock(AverageEarningsParagraph7PreEventAccruedWageAuthorityService.SemanticResolver.class);
        ordinary = mock(AverageEarningsParagraph7PreEventAccruedWageAuthorityService.OrdinaryResolver.class);
        harmful = mock(AverageEarningsParagraph7PreEventAccruedWageAuthorityService.HarmfulResolver.class);
        bonusFacts = mock(AverageEarningsParagraph7PreEventAccruedWageAuthorityService.BonusFactResolver.class);
        bonusAccrual = mock(AverageEarningsParagraph7PreEventAccruedWageAuthorityService.BonusAccrualResolver.class);
        bonusPolicy = mock(AverageEarningsParagraph7PreEventAccruedWageAuthorityService.BonusPolicyResolver.class);
        bonusWorkTime = mock(AverageEarningsParagraph7PreEventAccruedWageAuthorityService.BonusWorkTimeResolver.class);
        bonusFormula = mock(AverageEarningsParagraph7PreEventAccruedWageAuthorityService.BonusFormulaCalculator.class);
        finalResolver = mock(AverageEarningsParagraph7PreEventAccruedWageAuthorityService.FinalResolver.class);

        user = mock(AppUser.class);
        baseResolution = mock(AverageEarningsParagraph7PreEventBasePayAuthorityService.Resolution.class);
        baseCalculation = mock(AverageEarningsParagraph7PreEventBasePayFormula.Calculation.class);
        semanticResolution = mock(AverageEarningsParagraph7PreEventSemanticWageFactService.Resolution.class);
        ordinaryResolution = mock(AverageEarningsParagraph7PreEventOrdinaryPremiumService.Resolution.class);
        harmfulResolution = mock(AverageEarningsParagraph7PreEventHarmfulCompensationService.Resolution.class);
        bonusFactsResolution = mock(AverageEarningsParagraph7PreEventBonusP15FactService.Resolution.class);
        accrualResolution = mock(AverageEarningsParagraph7PreEventBonusAccrualAuthorityService.Resolution.class);
        policyResolution = mock(AverageEarningsParagraph7PreEventBonusP15Policy.Resolution.class);
        workTimeResolution = mock(AverageEarningsParagraph7PreEventBonusWorkTimeFactService.Resolution.class);
        bonusCalculation = mock(AverageEarningsParagraph7PreEventBonusP15Formula.Calculation.class);
        finalResolution = mock(AverageEarningsParagraph7PreEventAccruedWageAuthority.Resolution.class);

        when(base.resolve(user, EVENT)).thenReturn(baseResolution);
        when(baseResolution.ready()).thenReturn(true);
        when(baseFormula.calculate(baseResolution)).thenReturn(baseCalculation);
        when(semantic.resolve(user, baseCalculation)).thenReturn(semanticResolution);
        when(semanticResolution.ready()).thenReturn(true);
        when(ordinary.resolve(user, semanticResolution)).thenReturn(ordinaryResolution);
        when(ordinaryResolution.ready()).thenReturn(true);
        when(harmful.resolve(user, ordinaryResolution)).thenReturn(harmfulResolution);
        when(harmfulResolution.ready()).thenReturn(true);
        when(bonusFacts.resolve(user, semanticResolution)).thenReturn(bonusFactsResolution);
        when(bonusFactsResolution.ready()).thenReturn(true);
        when(bonusAccrual.resolve(eq(user), eq(bonusFactsResolution), eq(DISCOVERY), any()))
                .thenReturn(accrualResolution);
        when(accrualResolution.ready()).thenReturn(true);
        when(bonusPolicy.resolve(accrualResolution)).thenReturn(policyResolution);
        when(policyResolution.ready()).thenReturn(true);
        when(bonusWorkTime.resolve(user, policyResolution)).thenReturn(workTimeResolution);
        when(workTimeResolution.ready()).thenReturn(true);
        when(bonusFormula.calculate(workTimeResolution)).thenReturn(bonusCalculation);
        when(finalResolver.resolve(harmfulResolution, bonusCalculation)).thenReturn(finalResolution);
    }

    @Test
    void blockedBaseAuthorityStopsBeforeMoneyAndPreservesBlocker() {
        when(baseResolution.ready()).thenReturn(false);
        when(baseResolution.blockingReason()).thenReturn("BASE_BLOCK");
        when(baseResolution.blockingMessage()).thenReturn("base blocked");

        var result = service().resolve(user, EVENT, DISCOVERY, List.of());

        assertEquals("BASE_BLOCK", result.blockingReason());
        assertEquals(
                AverageEarningsParagraph7PreEventAccruedWageAuthority.BlockingSource.COMPOSITION_AUTHORITY,
                result.blockingSource()
        );
        verifyNoInteractions(baseFormula, semantic, ordinary, harmful, bonusFacts);
    }

    @Test
    void blockedSemanticAuthorityStopsBeforePremiumAndBonusDiscovery() {
        when(semanticResolution.ready()).thenReturn(false);
        when(semanticResolution.blockingReason()).thenReturn("SEMANTIC_BLOCK");
        when(semanticResolution.blockingMessage()).thenReturn("semantic blocked");

        var result = service().resolve(user, EVENT, DISCOVERY, List.of());

        assertEquals("SEMANTIC_BLOCK", result.blockingReason());
        verifyNoInteractions(ordinary, harmful, bonusFacts, bonusAccrual);
    }

    @Test
    void blockedHarmfulAuthorityPreservesHarmfulBlockingSourceAndSkipsBonusBranch() {
        when(harmfulResolution.ready()).thenReturn(false);
        when(harmfulResolution.eventDate()).thenReturn(EVENT);
        when(harmfulResolution.blockingReason()).thenReturn("HARMFUL_BLOCK");
        when(harmfulResolution.blockingMessage()).thenReturn("harmful blocked");

        var result = service().resolve(user, EVENT, DISCOVERY, List.of());

        assertEquals("HARMFUL_BLOCK", result.blockingReason());
        assertEquals(
                AverageEarningsParagraph7PreEventAccruedWageAuthority.BlockingSource.HARMFUL_AUTHORITY,
                result.blockingSource()
        );
        assertSame(harmfulResolution, result.harmfulAuthority());
        verifyNoInteractions(bonusFacts, bonusAccrual, bonusPolicy, bonusWorkTime, bonusFormula);
    }

    @Test
    void blockedBonusFactAuthorityPreservesBonusBlockingSourceAndStopsAccrualDiscovery() {
        when(bonusFactsResolution.ready()).thenReturn(false);
        when(bonusFactsResolution.blockingReason()).thenReturn("BONUS_FACT_BLOCK");
        when(bonusFactsResolution.blockingMessage()).thenReturn("bonus facts blocked");

        var result = service().resolve(user, EVENT, DISCOVERY, List.of());

        assertEquals("BONUS_FACT_BLOCK", result.blockingReason());
        assertEquals(
                AverageEarningsParagraph7PreEventAccruedWageAuthority.BlockingSource.BONUS_AUTHORITY,
                result.blockingSource()
        );
        assertSame(harmfulResolution, result.harmfulAuthority());
        verifyNoInteractions(bonusAccrual, bonusPolicy, bonusWorkTime, bonusFormula);
    }

    @Test
    void readyPipelinePreservesExactAuthorityOrderAndReturnsFinalAggregate() {
        var result = service().resolve(user, EVENT, DISCOVERY, List.of());

        assertSame(finalResolution, result);
        var order = inOrder(
                base,
                baseFormula,
                semantic,
                ordinary,
                harmful,
                bonusFacts,
                bonusAccrual,
                bonusPolicy,
                bonusWorkTime,
                bonusFormula,
                finalResolver
        );
        order.verify(base).resolve(user, EVENT);
        order.verify(baseFormula).calculate(baseResolution);
        order.verify(semantic).resolve(user, baseCalculation);
        order.verify(ordinary).resolve(user, semanticResolution);
        order.verify(harmful).resolve(user, ordinaryResolution);
        order.verify(bonusFacts).resolve(user, semanticResolution);
        order.verify(bonusAccrual).resolve(user, bonusFactsResolution, DISCOVERY, List.of());
        order.verify(bonusPolicy).resolve(accrualResolution);
        order.verify(bonusWorkTime).resolve(user, policyResolution);
        order.verify(bonusFormula).calculate(workTimeResolution);
        order.verify(finalResolver).resolve(harmfulResolution, bonusCalculation);
    }

    @Test
    void zeroProofsAreCopiedBeforeLazyBonusAccrualUsesThem() {
        List<YearMonth> mutable = new ArrayList<>();
        mutable.add(YearMonth.of(2026, 1));
        when(bonusAccrual.resolve(eq(user), eq(bonusFactsResolution), eq(DISCOVERY), any()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    List<YearMonth> proofs = invocation.getArgument(3);
                    mutable.clear();
                    assertEquals(List.of(YearMonth.of(2026, 1)), proofs);
                    assertThrows(
                            UnsupportedOperationException.class,
                            () -> proofs.add(YearMonth.of(2026, 2))
                    );
                    return accrualResolution;
                });

        assertSame(finalResolution, service().resolve(user, EVENT, DISCOVERY, mutable));
    }

    private AverageEarningsParagraph7PreEventAccruedWageAuthorityService service() {
        return new AverageEarningsParagraph7PreEventAccruedWageAuthorityService(
                base,
                baseFormula,
                semantic,
                ordinary,
                harmful,
                bonusFacts,
                bonusAccrual,
                bonusPolicy,
                bonusWorkTime,
                bonusFormula,
                finalResolver
        );
    }
}
