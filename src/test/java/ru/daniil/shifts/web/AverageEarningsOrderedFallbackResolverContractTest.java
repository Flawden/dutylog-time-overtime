package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AverageEarningsOrderedFallbackResolverContractTest {

    private static final Path RESOLVER = Path.of(
            "src/main/java/ru/daniil/shifts/service/",
            "AverageEarningsOrderedFallbackResolver.java"
    );

    @Test
    void J5DeclaresStrictPrimaryP6P7P8SelectionOrder() throws Exception {
        String source = Files.readString(RESOLVER);
        assertTrue(source.contains("PRIMARY -> PARAGRAPH 6 PRECEDING -> PARAGRAPH 7 PRE-EVENT -> PARAGRAPH 8"));
        assertTrue(source.indexOf("Selection.PRIMARY_REFERENCE_PERIOD") < source.indexOf("Selection.PARAGRAPH_6_PRECEDING_REFERENCE_PERIOD"));
        assertTrue(source.indexOf("Selection.PARAGRAPH_6_PRECEDING_REFERENCE_PERIOD") < source.indexOf("Selection.PARAGRAPH_7_PRE_EVENT_ACCRUED_WAGE"));
        assertTrue(source.indexOf("Selection.PARAGRAPH_7_PRE_EVENT_ACCRUED_WAGE") < source.indexOf("Selection.PARAGRAPH_8_ESTABLISHED_TARIFF_OR_SALARY"));
    }

    @Test
    void J5UsesLazySuppliersToPreventPrematureDownstreamAuthorityEvaluation() throws Exception {
        String source = Files.readString(RESOLVER);
        assertTrue(source.contains("Supplier<AverageEarningsParagraph7PreEventAccruedWageAuthority.Resolution>"));
        assertTrue(source.contains("Supplier<AverageEarningsParagraph8TariffSalaryAuthorityService.Resolution>"));
        assertTrue(source.indexOf("paragraph7Supplier.get()") < source.indexOf("paragraph8Supplier.get()"));
    }

    @Test
    void J5CannotJumpDirectlyFromParagraph6ExhaustionToParagraph8() throws Exception {
        String source = Files.readString(RESOLVER);
        int exhausted = source.indexOf("PARAGRAPH_6_EXHAUSTED");
        int p7Get = source.indexOf("paragraph7Supplier.get()", exhausted);
        int p8Get = source.indexOf("paragraph8Supplier.get()", exhausted);
        assertTrue(exhausted >= 0);
        assertTrue(p7Get > exhausted);
        assertTrue(p8Get > p7Get);
    }

    @Test
    void J5RequiresBothPreEventAccruedWageAndWorkedTimeForParagraph7() throws Exception {
        String compact = compact(Files.readString(RESOLVER));
        assertTrue(compact.contains("if (paragraph7WagePresent && paragraph7WorkedTimePresent)"));
        assertTrue(compact.contains("if (!paragraph7WagePresent)"));
        assertTrue(compact.contains("if (!paragraph7WorkedTimePresent)"));
    }

    @Test
    void J5TreatsBlockedAuthoritiesAsBlockersNotExhaustion() throws Exception {
        String source = Files.readString(RESOLVER);
        assertTrue(source.contains("if (!paragraph6.ready())"));
        assertTrue(source.contains("if (!paragraph7.ready())"));
        assertTrue(source.contains("if (!paragraph8.ready())"));
        assertTrue(source.contains("Blocked ordered fallback cannot expose partial selection"));
    }

    @Test
    void J5ValidatesP7ExactPreEventWindowIdentity() throws Exception {
        String source = Files.readString(RESOLVER);
        assertTrue(source.contains("YearMonth.from(eventDate).atDay(1).equals(paragraph7.periodFrom())"));
        assertTrue(source.contains("eventDate.equals(paragraph7.cutoffExclusive())"));
    }

    @Test
    void J5ValidatesP8EventMonthAndLegalRegimeIdentity() throws Exception {
        String source = Files.readString(RESOLVER);
        assertTrue(source.contains("eventMonth.atDay(1).equals(paragraph8.compensationBoundary())"));
        assertTrue(source.contains("legalRegime == paragraph8.legalRegime()"));
    }

    @Test
    void J5ContainsNoAverageDailyOrMoneyFormula() throws Exception {
        String source = Files.readString(RESOLVER);
        assertFalse(source.contains("VacationAverageDailyEarningsFormula"));
        assertFalse(source.contains("BigDecimal"));
        assertFalse(source.contains("RoundingMode"));
        assertFalse(source.contains("Math.addExact"));
        assertFalse(source.contains("Math.multiplyExact"));
    }

    @Test
    void J5DoesNotPerformRepositoryOrPayrollDiscovery() throws Exception {
        String source = Files.readString(RESOLVER);
        assertFalse(source.contains("Repository"));
        assertFalse(source.contains("PayrollSnapshot"));
        assertFalse(source.contains("PayrollHistoricalSemanticEarningsService"));
        assertFalse(source.contains("CompensationTermRepository"));
    }

    @Test
    void J5LeavesUnifiedAverageDailyCalculationForF3K() throws Exception {
        String source = Files.readString(RESOLVER);
        assertFalse(source.contains("VacationAveragePrimaryCalculationService"));
        assertFalse(source.contains("calculate("));
        assertTrue(source.contains("This layer is deliberately policy-only"));
    }

    private static String compact(String value) {
        return value.replaceAll("\\s+", " ").trim();
    }
}
