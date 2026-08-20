package ru.daniil.shifts.service;

import org.junit.jupiter.api.Test;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.PayPricingTerm;
import ru.daniil.shifts.repo.PayPricingTermRepository;
import ru.daniil.shifts.service.PayPricingRuleResolver.ConsumedSlice;
import ru.daniil.shifts.service.exception.ApiException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PayPricingPolicyServiceTest {

    private final PayPricingTermRepository terms =
            mock(
                    PayPricingTermRepository.class
            );

    private final PayPricingRuleResolver resolver =
            new PayPricingRuleResolver();

    private final PayPricingPolicyService service =
            new PayPricingPolicyService(
                    terms,
                    resolver
            );

    private final AppUser user =
            new AppUser(
                    "pricing-policy-user",
                    "{noop}irrelevant"
            );

    @Test
    void sourceWorkDateSelectsHistoricalTermAndPersistedRulesReachResolver() {
        LocalDate sourceDate =
                LocalDate.of(
                        2026,
                        8,
                        20
                );

        PayPricingTerm term =
                new PayPricingTerm(
                        user,
                        LocalDate.of(
                                2026,
                                8,
                                15
                        )
                );

        term.addRule(
                "NIGHT",
                "NIGHT",
                2_000,
                0,
                null,
                null
        );

        term.addRule(
                "OT_TIER_1",
                "OVERTIME",
                5_000,
                0,
                120,
                null
        );

        when(
                terms
                        .findFirstByOwnerAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                                user,
                                sourceDate
                        )
        ).thenReturn(
                Optional.of(term)
        );

        var resolved =
                service.resolveForSourceDate(
                        user,
                        sourceDate,
                        List.of(
                                new ConsumedSlice(
                                        60,
                                        true,
                                        false,
                                        0
                                )
                        )
                );

        assertEquals(
                sourceDate,
                resolved.sourceDate()
        );

        assertEquals(
                LocalDate.of(
                        2026,
                        8,
                        15
                ),
                resolved.effectiveFrom()
        );

        assertEquals(
                2,
                resolved.rules()
                        .rules()
                        .size()
        );

        assertEquals(
                1,
                resolved.pricingSlices()
                        .size()
        );

        assertEquals(
                List.of(
                        "NIGHT",
                        "OT_TIER_1"
                ),
                resolved.pricingSlices()
                        .get(0)
                        .components()
                        .stream()
                        .map(component ->
                                component.code()
                        )
                        .toList()
        );

        verify(
                terms,
                times(1)
        ).findFirstByOwnerAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                user,
                sourceDate
        );
    }

    @Test
    void existingEmptyTermMeansExplicitBaseOnlyPolicyNotMissingConfiguration() {
        LocalDate sourceDate =
                LocalDate.of(
                        2026,
                        9,
                        1
                );

        PayPricingTerm term =
                new PayPricingTerm(
                        user,
                        sourceDate
                );

        when(
                terms
                        .findFirstByOwnerAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                                user,
                                sourceDate
                        )
        ).thenReturn(
                Optional.of(term)
        );

        var resolved =
                service.resolveForSourceDate(
                        user,
                        sourceDate,
                        List.of(
                                new ConsumedSlice(
                                        45,
                                        false,
                                        false,
                                        0
                                )
                        )
                );

        assertTrue(
                resolved.rules()
                        .rules()
                        .isEmpty()
        );

        assertEquals(
                1,
                resolved.pricingSlices()
                        .size()
        );

        assertEquals(
                45,
                resolved.pricingSlices()
                        .get(0)
                        .minutes()
        );

        assertTrue(
                resolved.pricingSlices()
                        .get(0)
                        .components()
                        .isEmpty()
        );
    }

    @Test
    void absentHistoricalTermFailsClosedInsteadOfAssumingZeroPremiums() {
        LocalDate sourceDate =
                LocalDate.of(
                        2026,
                        5,
                        31
                );

        when(
                terms
                        .findFirstByOwnerAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                                user,
                                sourceDate
                        )
        ).thenReturn(
                Optional.empty()
        );

        ApiException error =
                assertThrows(
                        ApiException.class,
                        () ->
                                service.resolveForSourceDate(
                                        user,
                                        sourceDate,
                                        List.of(
                                                new ConsumedSlice(
                                                        60,
                                                        false,
                                                        false,
                                                        0
                                                )
                                        )
                                )
                );

        assertTrue(
                error.getMessage()
                        .contains(
                                "не настроены правила оплаты"
                        )
        );
    }
}
