package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AverageEarningsParagraph7PreEventOrdinaryPremiumAuthorityContractTest {
    private static final Path SOURCE = Path.of(
            "src/main/java/ru/daniil/shifts/service/" +
                    "AverageEarningsParagraph7PreEventOrdinaryPremiumService.java"
    );

    @Test
    void J3B4PricesOnlyTheLegalPreEventRange() throws Exception {
        String source = Files.readString(SOURCE);

        assertTrue(source.contains("date.isBefore(cutoffExclusive)"));
        assertTrue(source.contains("sourceService.project(user, date)"));
        assertFalse(source.contains("priceMonth("));
        assertFalse(source.contains("PayrollOrdinaryPremiumPreviewService"));
    }

    @Test
    void J3B4ReusesCanonicalOrdinaryPricingAuthorities() throws Exception {
        String source = Files.readString(SOURCE);

        assertTrue(source.contains("OrdinaryWorkPremiumSourceService"));
        assertTrue(source.contains("PayPricingPolicyService"));
        assertTrue(source.contains("HistoricalCompensationRateService"));
        assertTrue(source.contains("PayPricingEngine"));
        assertTrue(source.contains("semanticFacts.basePay()"));
    }

    @Test
    void J3B4AggregatesRangeSlicesByHistoricalRateBeforeMoney() throws Exception {
        String source = Files.readString(SOURCE);

        assertTrue(source.contains("Map<Long, List<PricingSlice>> slicesByRate"));
        assertTrue(source.contains(".addAll(pricingSlices)"));
        assertTrue(source.contains("pricingEngine.price("));
        assertFalse(source.contains("RoundingMode"));
        assertFalse(source.contains("BigDecimal"));
    }

    @Test
    void J3B4AddsOnlyPremiumMoneyAndNeverDuplicatesBasePay() throws Exception {
        String source = Files.readString(SOURCE);

        assertTrue(source.contains("priced.premiumAmountMinor()"));
        assertFalse(source.contains("priced.baseAmountMinor()"));
        assertFalse(source.contains("priced.totalAmountMinor()"));
        assertFalse(source.contains("PayrollService"));
        assertFalse(source.contains("PayrollSnapshot"));
    }

    @Test
    void J3B4CannotSelectParagraph8OrInferFallbackFromZeroPremium() throws Exception {
        String source = Files.readString(SOURCE);

        assertFalse(source.contains("PARAGRAPH_8"));
        assertFalse(source.contains("Paragraph8"));
        assertFalse(source.contains("fallback"));
        assertTrue(source.contains("ordinaryPremiumAmountMinor > 0L"));
        assertTrue(source.contains("Blocked paragraph-7 ordinary premium cannot expose partial money"));
    }
}
