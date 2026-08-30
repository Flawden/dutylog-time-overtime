package ru.daniil.shifts.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class VacationAverageUnifiedDailyResolverContractTest {

    private static final Path SOURCE = Path.of(
            "src/main/java/ru/daniil/shifts/service/VacationAverageUnifiedDailyResolver.java"
    );

    @Test
    void KDoesNotChooseFallbackBranch() throws Exception {
        String source = source();
        assertTrue(source.contains("J5 owns that policy"));
        assertFalse(source.contains("AverageEarningsParagraph6ReferenceResolver.resolve("));
        assertFalse(source.contains("new AverageEarningsOrderedFallbackResolver"));
    }

    @Test
    void KReusesF3IForReferenceBranches() throws Exception {
        String source = source();
        assertTrue(source.contains("VacationAveragePrimaryCalculationService.Resolution"));
        assertTrue(source.contains("calculation.averageDaily()"));
        assertTrue(source.contains("selectedReferenceWindow()"));
    }

    @Test
    void KUsesExplicitParagraph7CalendarBasis() throws Exception {
        String source = source();
        assertTrue(source.contains("Paragraph7CalendarBasis"));
        assertTrue(source.contains("basis.denominatorDays()"));
        assertTrue(source.contains("293L"));
        assertTrue(source.contains("eventMonth.lengthOfMonth()"));
    }

    @Test
    void KNeverUsesWorkedDayCountAsParagraph7CalendarDenominator() throws Exception {
        String source = source();
        assertFalse(source.contains(".workedDayCount()"));
        assertTrue(source.contains("workedDayCount()"));
        assertTrue(source.contains("not {@code workedDayCount()}"));
    }

    @Test
    void KRequiresExplicitParagraph8FormulaBasis() throws Exception {
        String source = source();
        assertTrue(source.contains("Paragraph8FormulaBasis"));
        assertTrue(source.contains("PARAGRAPH_8_FORMULA_BASIS_REQUIRED"));
        assertTrue(source.contains("this resolver never invents a tariff/salary conversion policy"));
    }

    @Test
    void KHasNoDecimalOrIntermediateRoundingPolicy() throws Exception {
        String source = source();
        assertFalse(source.contains("BigDecimal"));
        assertFalse(source.contains("RoundingMode"));
        assertFalse(source.contains("double "));
        assertFalse(source.contains("Math.round"));
    }

    @Test
    void KDoesNotCalculatePayableVacationDaysOrFinalVacationPay() throws Exception {
        String source = source();
        assertFalse(source.contains("payableVacationDays"));
        assertFalse(source.contains("vacationPayMinor"));
        assertTrue(source.contains("F3L/F3M"));
    }

    @Test
    void KPreservesExplicitParagraph8PolicyAuthority() throws Exception {
        String source = source();
        assertTrue(source.contains("MONTHLY_OFFICIAL_SALARY_DIV_29_3"));
        assertTrue(source.contains("HOURLY_TARIFF_AVERAGE_MONTHLY_NORM_DIV_29_3"));
        assertTrue(source.contains("authorityCode"));
        assertTrue(source.contains("annualNormMinutes"));
    }

    private static String source() throws Exception {
        return Files.readString(
                SOURCE,
                StandardCharsets.UTF_8
        );
    }
}
