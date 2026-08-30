package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AverageEarningsParagraph7PreEventBasePayFormulaContractTest {
    private static final Path SOURCE = Path.of(
            "src/main/java/ru/daniil/shifts/service/",
            "AverageEarningsParagraph7PreEventBasePayFormula.java"
    );

    @Test
    void J3B2IsPureFormulaWithoutRepositoryOrSpringDependencies() throws Exception {
        String source = Files.readString(SOURCE);

        assertFalse(source.contains("@Service"));
        assertFalse(source.contains("Repository"));
        assertFalse(source.contains("ProductionCalendarService"));
        assertFalse(source.contains("TimeCompensationService"));
        assertFalse(source.contains("PayrollHistoricalSemanticEarningsService"));
    }

    @Test
    void hourlyFormulaUsesConfiguredRateAndEligibleMinutesOverSixty() throws Exception {
        String source = compact(Files.readString(SOURCE));

        assertTrue(source.contains(
                "ratioMoney( configuredRate, eligibleMinutes, 60L )"
        ));
    }

    @Test
    void salaryFormulaUsesMonthlySalaryEligibleMinutesAndProvenNorm() throws Exception {
        String source = compact(Files.readString(SOURCE));

        assertTrue(source.contains(
                "ratioMoney( monthlySalary, eligibleMinutes, productionNorm.longValue() )"
        ));
    }

    @Test
    void J3B2HasOneFinalHalfUpMinorRoundingBoundary() throws Exception {
        String source = Files.readString(SOURCE);

        assertEquals(1, occurrences(source, "RoundingMode.HALF_UP"));
        assertFalse(source.contains("double"));
        assertFalse(source.contains("float"));
        assertFalse(source.contains("setScale("));
    }

    @Test
    void J3B2DoesNotSelectFallbackOrInferEligibilityFromMoney() throws Exception {
        String source = Files.readString(SOURCE);

        assertFalse(source.contains("AverageEarningsParagraph6ReferenceResolver"));
        assertFalse(source.contains("HistoricalCompensationRateService"));
        assertFalse(source.contains("PARAGRAPH_8"));
        assertFalse(source.contains("Paragraph8"));
        assertFalse(source.contains("numeratorAmountMinor"));
        assertTrue(source.contains("A zero amount"));
        assertTrue(source.contains("carries no fallback meaning"));
    }

    private static String compact(String text) {
        return text.replaceAll("\\s+", " ").trim();
    }

    private static int occurrences(String source, String needle) {
        int count = 0;
        int from = 0;
        while (true) {
            int next = source.indexOf(needle, from);
            if (next < 0) {
                return count;
            }
            count++;
            from = next + needle.length();
        }
    }
}
