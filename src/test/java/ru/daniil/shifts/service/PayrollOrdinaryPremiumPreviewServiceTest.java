package ru.daniil.shifts.service;

import org.junit.jupiter.api.Test;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.service.OrdinaryWorkPremiumPricingService.BlockingDay;
import ru.daniil.shifts.service.OrdinaryWorkPremiumPricingService.MonthPremiumProjection;
import ru.daniil.shifts.service.OrdinaryWorkPremiumPricingService.NightPremiumSourceLine;
import ru.daniil.shifts.service.OrdinaryWorkPremiumPricingService.SourceDateValuation;
import ru.daniil.shifts.service.OrdinaryWorkPremiumSourceService.SourceKind;
import ru.daniil.shifts.service.exception.ApiException;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PayrollOrdinaryPremiumPreviewServiceTest {

    private final OrdinaryWorkPremiumPricingService pricing =
            mock(
                    OrdinaryWorkPremiumPricingService.class
            );

    private final PayrollOrdinaryPremiumPreviewService service =
            new PayrollOrdinaryPremiumPreviewService(
                    pricing
            );

    private final AppUser user =
            new AppUser(
                    "ordinary-premium-payroll-preview",
                    "{noop}unused"
            );

    private final YearMonth month =
            YearMonth.of(
                    2026,
                    8
            );

    @Test
    void zeroOrdinaryMonthIsReadyWithZeroPremiumMoney() {
        when(
                pricing.priceMonth(
                        user,
                        month
                )
        ).thenReturn(
                new MonthPremiumProjection(
                        month,
                        true,
                        null,
                        List.of(),
                        null,
                        0,
                        0L,
                        0L,
                        List.of(),
                        List.of()
                )
        );

        var result =
                service.preview(
                        user,
                        month,
                        "RUB"
                );

        assertTrue(result.ready());
        assertNull(result.blockingReason());
        assertEquals(0, result.ordinaryMinutes());
        assertEquals(0L, result.referenceBaseAmountMinor());
        assertEquals(0L, result.premiumAmountMinor());
    }

    @Test
    void readyPremiumPassesOnlyAdditivePremiumMoneyTowardPayroll() {
        when(
                pricing.priceMonth(
                        user,
                        month
                )
        ).thenReturn(
                new MonthPremiumProjection(
                        month,
                        true,
                        null,
                        List.of(),
                        "RUB",
                        60,
                        60_000L,
                        12_000L,
                        12_000L,
                        0L,
                        List.of(
                                new NightPremiumSourceLine(
                                        LocalDate.of(
                                                2026,
                                                8,
                                                24
                                        ),
                                        60,
                                        12_000L
                                )
                        ),
                        List.of(),
                        List.of()
                )
        );

        var result =
                service.preview(
                        user,
                        month,
                        "RUB"
                );

        assertTrue(result.ready());
        assertEquals(60, result.ordinaryMinutes());

        assertEquals(
                60_000L,
                result.referenceBaseAmountMinor()
        );

        assertEquals(
                12_000L,
                result.premiumAmountMinor()
        );

        assertEquals(
                1,
                result.exactNightPremiumSourceLines().size()
        );

        assertEquals(
                LocalDate.of(
                        2026,
                        8,
                        24
                ),
                result.exactNightPremiumSourceLines()
                        .get(0)
                        .earningDate()
        );

        assertNotEquals(
                result.referenceBaseAmountMinor()
                        + result.premiumAmountMinor(),
                result.premiumAmountMinor(),
                "Payroll adapter must preserve delta-only semantics"
        );
    }

    @Test
    void sourceAmbiguityBecomesBlockedPreviewWithoutSpeculativeMoney() {
        LocalDate date =
                LocalDate.of(
                        2026,
                        8,
                        20
                );

        BlockingDay blocker =
                new BlockingDay(
                        date,
                        120,
                        OrdinaryWorkPremiumSourceService.BLOCK_CLOCK_QUANTITY
                );

        when(
                pricing.priceMonth(
                        user,
                        month
                )
        ).thenReturn(
                new MonthPremiumProjection(
                        month,
                        false,
                        "ORDINARY_PREMIUM_SOURCE_NOT_READY",
                        List.of(blocker),
                        null,
                        120,
                        0L,
                        0L,
                        List.of(),
                        List.of()
                )
        );

        var result =
                service.preview(
                        user,
                        month,
                        "RUB"
                );

        assertFalse(result.ready());

        assertEquals(
                "ORDINARY_PREMIUM_SOURCE_NOT_READY",
                result.blockingReason()
        );

        assertEquals(120, result.ordinaryMinutes());
        assertEquals(1, result.blockers().size());
        assertEquals(0L, result.referenceBaseAmountMinor());
        assertEquals(0L, result.premiumAmountMinor());
    }

    @Test
    void missingPricingRulesBecomesSoftPayrollBlocker() {
        when(
                pricing.priceMonth(
                        user,
                        month
                )
        ).thenThrow(
                ApiException.conflict(
                        "PAY_PRICING_RULES_REQUIRED",
                        "Нет правил оплаты"
                )
        );

        var result =
                service.preview(
                        user,
                        month,
                        "RUB"
                );

        assertFalse(result.ready());

        assertEquals(
                "PAY_PRICING_RULES_REQUIRED",
                result.blockingReason()
        );

        assertEquals(0L, result.premiumAmountMinor());
    }

    @Test
    void currencyMismatchBecomesSoftPayrollBlocker() {
        when(
                pricing.priceMonth(
                        user,
                        month
                )
        ).thenReturn(
                new MonthPremiumProjection(
                        month,
                        true,
                        null,
                        List.of(),
                        "USD",
                        60,
                        60_000L,
                        12_000L,
                        List.of(),
                        List.of()
                )
        );

        var result =
                service.preview(
                        user,
                        month,
                        "RUB"
                );

        assertFalse(result.ready());

        assertEquals(
                PayrollOrdinaryPremiumPreviewService
                        .PAYROLL_CURRENCY_MISMATCH,
                result.blockingReason()
        );

        assertEquals(0L, result.premiumAmountMinor());
    }

    @Test
    void explicitZeroPremiumStillRequiresImmutablePricingIdentity() {
        LocalDate sourceDate =
                LocalDate.of(
                        2026,
                        8,
                        18
                );

        var sourcePiece =
                new OrdinaryWorkPremiumSourceService.SourcePiece(
                        sourceDate,
                        OrdinaryWorkPremiumSourceService.SourceKind.EXPLICIT,
                        77L,
                        null,
                        java.time.Instant.parse(
                                "2026-08-18T22:00:00Z"
                        ),
                        java.time.Instant.parse(
                                "2026-08-18T23:00:00Z"
                        ),
                        "UTC",
                        60,
                        true,
                        false
                );

        var pricingSlice =
                new PayPricingEngine.PricingSlice(
                        60,
                        List.of(
                                new PayPricingEngine.PremiumComponent(
                                        "NIGHT",
                                        0
                                )
                        )
                );

        var valuation =
                new OrdinaryWorkPremiumPricingService.SourceDateValuation(
                        sourceDate,
                        OrdinaryWorkPremiumSourceService.SourceKind.EXPLICIT,
                        60,
                        60,
                        0,
                        LocalDate.of(
                                2026,
                                8,
                                1
                        ),
                        month,
                        LocalDate.of(
                                2026,
                                8,
                                1
                        ),
                        "HOURLY",
                        "RUB",
                        60_000L,
                        null,
                        List.of(
                                sourcePiece
                        ),
                        List.of(
                                pricingSlice
                        )
                );

        when(
                pricing.priceMonth(
                        user,
                        month
                )
        ).thenReturn(
                new MonthPremiumProjection(
                        month,
                        true,
                        null,
                        List.of(),
                        "RUB",
                        60,
                        60_000L,
                        0L,
                        List.of(
                                new OrdinaryWorkPremiumPricingService.PricedRateBucket(
                                        60_000L,
                                        60,
                                        60_000L,
                                        0L,
                                        List.of()
                                )
                        ),
                        List.of(
                                valuation
                        )
                )
        );

        var result =
                service.preview(
                        user,
                        month,
                        "RUB"
                );

        assertTrue(
                result.ready()
        );

        assertEquals(
                0L,
                result.premiumAmountMinor()
        );

        assertTrue(
                result.pricingIdentityRequired()
        );

        assertNotNull(
                result.pricingFingerprint()
        );

        assertTrue(
                result.pricingFingerprint()
                        .matches(
                                "[0-9a-f]{64}"
                        )
        );
    }

    @Test
    void structuralProgrammingErrorIsNotHiddenAsReadinessProblem() {
        when(
                pricing.priceMonth(
                        user,
                        month
                )
        ).thenThrow(
                new IllegalStateException(
                        "broken invariant"
                )
        );

        IllegalStateException error =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                service.preview(
                                        user,
                                        month,
                                        "RUB"
                                )
                );

        assertEquals(
                "broken invariant",
                error.getMessage()
        );
    }
}
