package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AverageEarningsParagraph7PreEventHarmfulCompensationAuthorityContractTest {
    private static final Path SERVICE = Path.of(
            "src/main/java/ru/daniil/shifts/service/",
            "AverageEarningsParagraph7PreEventHarmfulCompensationService.java"
    );

    @Test
    void J3B5UsesMachineOwnedHarmfulIdentityAndNeverDisplayNameInference() throws Exception {
        String source = Files.readString(SERVICE);
        assertTrue(source.contains("PayrollEarningKind.HARMFUL_CONDITIONS"));
        assertTrue(source.contains("version.getEarningKind()"));
        assertFalse(source.contains("getDisplayName().toLowerCase"));
        assertFalse(source.contains("contains(\"harm"));
        assertFalse(source.contains("contains(\"вред"));
    }

    @Test
    void J3B5AcceptsOnlyRangeBoundEarnedBaseHarmfulConfiguration() throws Exception {
        String source = Files.readString(SERVICE);
        assertTrue(source.contains("CalculationType.PERCENT_OF_BASE"));
        assertTrue(source.contains("CalculationBase.EARNED_BASE_PAY"));
        assertTrue(source.contains("CONFIGURATION_NOT_RANGE_BOUND"));
        assertTrue(source.contains("basePay.basePayAmountMinor()"));
    }

    @Test
    void J3B5ReusesCanonicalResolverAndComponentCalculator() throws Exception {
        String source = Files.readString(SERVICE);
        assertTrue(source.contains("CompensationComponentResolverService"));
        assertTrue(source.contains("CompensationComponentCalculationService"));
        assertTrue(source.contains("resolver.resolve(user, eventMonth)"));
        assertTrue(source.contains("calculator.calculate("));
        assertFalse(source.contains("RoundingMode"));
        assertFalse(source.contains("BigDecimal"));
    }

    @Test
    void J3B5DoesNotUseMonthlyPreviewFrozenGrossOrParagraph8() throws Exception {
        String source = Files.readString(SERVICE);
        assertFalse(source.contains("PayrollCompensationComponentPreviewService"));
        assertFalse(source.contains("PayrollSnapshot"));
        assertFalse(source.contains("PayrollHistoricalSemanticEarningsService"));
        assertFalse(source.contains("PayrollService"));
        assertFalse(source.contains("PARAGRAPH_8"));
        assertFalse(source.contains("Paragraph8"));
    }

    @Test
    void J3B5BlocksUnsafeShapesAndNeverExposesPartialMoney() throws Exception {
        String source = Files.readString(SERVICE);
        assertTrue(source.contains("FIXED_AMOUNT, NOMINAL_SALARY and LOCAL_ELIGIBLE_EARNINGS"));
        assertTrue(source.contains("harmfulAmountMinor != 0L"));
        assertTrue(source.contains("!lines.isEmpty()"));
        assertTrue(source.contains("Blocked paragraph-7 harmful compensation cannot expose partial money"));
    }
}
