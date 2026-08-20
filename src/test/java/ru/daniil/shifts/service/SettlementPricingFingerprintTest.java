package ru.daniil.shifts.service;

import org.junit.jupiter.api.Test;
import ru.daniil.shifts.service.OvertimeSettlementPricingService.PricedRateBucket;
import ru.daniil.shifts.service.OvertimeSettlementPricingService.SettlementMoneyProjection;
import ru.daniil.shifts.service.OvertimeSettlementPricingService.SourceValuation;
import ru.daniil.shifts.service.PayPricingEngine.PremiumComponent;
import ru.daniil.shifts.service.PayPricingEngine.PricedPremium;
import ru.daniil.shifts.service.PayPricingEngine.PricingSlice;
import ru.daniil.shifts.service.PayrollSettlementPricingService.PayrollSettlementPricing;
import ru.daniil.shifts.service.PayrollSettlementPricingService.SettlementLine;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SettlementPricingFingerprintTest {

    @Test
    void deepFingerprintIsStableButChangesWhenSourceOrResolvedRuleIdentityChanges() {
        SettlementMoneyProjection baseline =
                projection(
                        3000L,
                        "OT_TIER_1"
                );

        SettlementMoneyProjection identical =
                projection(
                        3000L,
                        "OT_TIER_1"
                );

        SettlementMoneyProjection anotherActualWorkSource =
                projection(
                        3001L,
                        "OT_TIER_1"
                );

        /*
         * Same bps/minutes/money, different resolved rule code.
         * Aggregate amount remains identical, explainability identity does not.
         */
        SettlementMoneyProjection anotherRuleIdentity =
                projection(
                        3000L,
                        "COMPANY_OT"
                );

        assertTrue(
                baseline.pricingFingerprint()
                        .matches(
                                "[0-9a-f]{64}"
                        )
        );

        assertEquals(
                baseline.pricingFingerprint(),
                identical.pricingFingerprint()
        );

        assertNotEquals(
                baseline.pricingFingerprint(),
                anotherActualWorkSource.pricingFingerprint()
        );

        assertNotEquals(
                baseline.pricingFingerprint(),
                anotherRuleIdentity.pricingFingerprint()
        );

        assertEquals(
                baseline.totalAmountMinor(),
                anotherRuleIdentity.totalAmountMinor()
        );
    }

    @Test
    void monthlyFingerprintIsIndependentOfRepositoryListOrderButOwnsEverySettlementFingerprint() {
        String firstFingerprint =
                "a".repeat(
                        64
                );

        String secondFingerprint =
                "b".repeat(
                        64
                );

        SettlementLine first =
                new SettlementLine(
                        10L,
                        LocalDate.of(
                                2026,
                                8,
                                10
                        ),
                        "RUB",
                        60,
                        100_000L,
                        50_000L,
                        150_000L,
                        firstFingerprint
                );

        SettlementLine second =
                new SettlementLine(
                        11L,
                        LocalDate.of(
                                2026,
                                8,
                                20
                        ),
                        "RUB",
                        60,
                        100_000L,
                        50_000L,
                        150_000L,
                        secondFingerprint
                );

        PayrollSettlementPricing ordered =
                new PayrollSettlementPricing(
                        YearMonth.of(
                                2026,
                                8
                        ),
                        "RUB",
                        2,
                        120,
                        200_000L,
                        100_000L,
                        300_000L,
                        List.of(
                                first,
                                second
                        )
                );

        PayrollSettlementPricing reversed =
                new PayrollSettlementPricing(
                        YearMonth.of(
                                2026,
                                8
                        ),
                        "RUB",
                        2,
                        120,
                        200_000L,
                        100_000L,
                        300_000L,
                        List.of(
                                second,
                                first
                        )
                );

        SettlementLine changedSecond =
                new SettlementLine(
                        11L,
                        LocalDate.of(
                                2026,
                                8,
                                20
                        ),
                        "RUB",
                        60,
                        100_000L,
                        50_000L,
                        150_000L,
                        "c".repeat(
                                64
                        )
                );

        PayrollSettlementPricing changed =
                new PayrollSettlementPricing(
                        YearMonth.of(
                                2026,
                                8
                        ),
                        "RUB",
                        2,
                        120,
                        200_000L,
                        100_000L,
                        300_000L,
                        List.of(
                                first,
                                changedSecond
                        )
                );

        assertEquals(
                ordered.pricingFingerprint(),
                reversed.pricingFingerprint()
        );

        assertNotEquals(
                ordered.pricingFingerprint(),
                changed.pricingFingerprint()
        );

        assertTrue(
                ordered.pricingFingerprint()
                        .matches(
                                "[0-9a-f]{64}"
                        )
        );
    }

    @Test
    void emptyPayrollMonthHasNoSyntheticPricingFingerprint() {
        PayrollSettlementPricing empty =
                new PayrollSettlementPricing(
                        YearMonth.of(
                                2026,
                                8
                        ),
                        null,
                        0,
                        0,
                        0L,
                        0L,
                        0L,
                        List.of()
                );

        assertTrue(
                empty.empty()
        );

        assertNull(
                empty.pricingFingerprint()
        );
    }

    private SettlementMoneyProjection projection(
            long sourceActualWorkIntervalId,
            String premiumCode
    ) {
        LocalDate sourceDate =
                LocalDate.of(
                        2026,
                        8,
                        10
                );

        PricingSlice pricingSlice =
                new PricingSlice(
                        60,
                        List.of(
                                new PremiumComponent(
                                        premiumCode,
                                        5_000
                                )
                        )
                );

        SourceValuation source =
                new SourceValuation(
                        1000L,
                        2000L,
                        sourceActualWorkIntervalId,
                        sourceDate,
                        60,
                        0,
                        480,
                        LocalDate.of(
                                2026,
                                8,
                                1
                        ),
                        YearMonth.of(
                                2026,
                                8
                        ),
                        LocalDate.of(
                                2026,
                                8,
                                1
                        ),
                        "HOURLY",
                        "RUB",
                        100_000L,
                        null,
                        false,
                        false,
                        List.of(
                                pricingSlice
                        )
                );

        PricedRateBucket bucket =
                new PricedRateBucket(
                        100_000L,
                        60,
                        100_000L,
                        50_000L,
                        150_000L,
                        List.of(
                                new PricedPremium(
                                        premiumCode,
                                        5_000,
                                        60,
                                        50_000L
                                )
                        )
                );

        return new SettlementMoneyProjection(
                10L,
                LocalDate.of(
                        2026,
                        8,
                        20
                ),
                "RUB",
                60,
                100_000L,
                50_000L,
                150_000L,
                List.of(
                        bucket
                ),
                List.of(
                        source
                )
        );
    }
}
