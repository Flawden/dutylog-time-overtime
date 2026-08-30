package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AverageEarningsParagraph7PreEventBasePayAuthorityContractTest {
    private static final Path SERVICE = Path.of(
            "src/main/java/ru/daniil/shifts/service/",
            "AverageEarningsParagraph7PreEventBasePayAuthorityService.java"
    );

    @Test
    void authorityConsumesJ3AInsteadOfReReadingCalendarReality() throws Exception {
        String source = Files.readString(SERVICE);
        assertTrue(source.contains("workFacts.resolve(user, eventDate)"));
        assertFalse(source.contains("TimeCompensationService"));
        assertFalse(source.contains("payrollSource("));
    }

    @Test
    void compensationIdentityIsAnchoredToEventMonthStart() throws Exception {
        String compact = compact(Files.readString(SERVICE));
        assertTrue(compact.contains("LocalDate compensationBoundary = eventMonth.atDay(1);"));
        assertTrue(compact.contains("findFirstByOwnerAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc( user, compensationBoundary )"));
        assertFalse(compact.contains("eventDate.minusMonths"));
    }

    @Test
    void hourlyAuthorityUsesBankFirstBaseQuantity() throws Exception {
        String source = Files.readString(SERVICE);
        assertTrue(source.contains("day.hourlyBaseWorkedMinutes()"));
        assertFalse(source.contains("day.workedMinutes() *"));
        assertFalse(source.contains("workedMinutes /"));
    }

    @Test
    void salaryAuthorityCapsQuantityAtScheduledWorkWithoutMoneyFormula() throws Exception {
        String compact = compact(Files.readString(SERVICE));
        assertTrue(compact.contains("Math.min( day.plannedMinutes(), day.workedMinutes() )"));
        assertFalse(compact.contains("CompensationCalculationService"));
        assertFalse(compact.contains("BigDecimal"));
        assertFalse(compact.contains("RoundingMode"));
    }

    @Test
    void J3B1DoesNotSelectParagraph8OrInferMonthlyGross() throws Exception {
        String source = Files.readString(SERVICE);
        assertFalse(source.contains("PARAGRAPH_8"));
        assertFalse(source.contains("PayrollSnapshot"));
        assertFalse(source.contains("totalPayMinor"));
        assertFalse(source.contains("PayrollHistoricalSemanticEarningsService"));
        assertFalse(source.contains("amountMinor"));
    }

    private static String compact(String value) {
        return value.replaceAll("\\s+", " ").trim();
    }
}
