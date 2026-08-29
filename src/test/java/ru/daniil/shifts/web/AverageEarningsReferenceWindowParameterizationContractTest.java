package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AverageEarningsReferenceWindowParameterizationContractTest {

    private static final Path SERVICE = Path.of("src/main/java/ru/daniil/shifts/service");

    @Test
    void referenceWindowOwnsPrimaryEventMinusTwelveArithmetic() throws IOException {
        String window = source("AverageEarningsReferenceWindow.java");
        assertTrue(window.contains("eventMonth.minusMonths(12)"));
        assertTrue(window.contains("referenceTo.equals(referenceFrom.plusMonths(11))"));

        for (String file : parameterizedChain()) {
            assertFalse(source(file).contains("minusMonths(12)"), file);
        }
    }

    @Test
    void numeratorAndParagraph15ChainAcceptExplicitReferenceWindow() throws IOException {
        assertTrue(source("AverageEarningsNumeratorFactsService.java")
                .contains("AverageEarningsReferenceWindow referenceWindow"));
        assertTrue(source("AverageEarningsNumeratorCalculationService.java")
                .contains("AverageEarningsReferenceWindow referenceWindow"));
        assertTrue(source("AverageEarningsBonusP15HistoricalFactDiscoveryService.java")
                .contains("AverageEarningsReferenceWindow referenceWindow"));
        assertTrue(source("AverageEarningsBonusP15ReferenceWorkedTimeFactService.java")
                .contains("AverageEarningsReferenceWindow referenceWindow"));
        assertTrue(source("AverageEarningsBonusP15ReferenceCompletenessService.java")
                .contains("AverageEarningsReferenceWindow referenceWindow"));
        assertTrue(source("AverageEarningsBonusP15CalculationPipelineService.java")
                .contains("AverageEarningsReferenceWindow referenceWindow"));
    }

    @Test
    void selectedWindowFlowsThroughP15InsteadOfBeingRebuiltFromEventDate() throws IOException {
        String pipeline = compact(source("AverageEarningsBonusP15CalculationPipelineService.java"));
        assertTrue(pipeline.contains(
                "eventDate, referenceWindow, discoveryThroughMonth, provenNoPayrollMonths"
        ));
        assertTrue(pipeline.contains(
                "eventDate, referenceWindow, referenceNoPayrollMonths"
        ));
        assertFalse(pipeline.contains("eventMonth.minusMonths"));
    }

    @Test
    void paragraph15AnnualEventYearRuleStillUsesLegalEventDate() throws IOException {
        String policy = source("AverageEarningsBonusP15Policy.java");
        assertTrue(policy.contains("int previousEventYear = eventDate.getYear() - 1"));
        assertTrue(policy.contains("AverageEarningsReferenceWindow.of("));
        assertFalse(policy.contains("expectedFrom = eventMonth.minusMonths"));
    }

    @Test
    void vacationCalendarAndDenominatorUseSameSelectedReferenceWindow() throws IOException {
        String calendar = compact(source("VacationAverageReferenceCalendarService.java"));
        String denominator = source("VacationAverageCalendarDenominator.java");
        assertTrue(calendar.contains("referenceFacts.resolve( user, referenceWindow )"));
        assertTrue(calendar.contains(".calculate( eventDate, referenceWindow, monthFacts )"));
        assertTrue(denominator.contains("AverageEarningsReferenceWindow referenceWindow"));
    }

    @Test
    void vacationCalculationPassesWindowToBothMoneyAndCalendarAuthorities() throws IOException {
        String text = compact(source("VacationAveragePrimaryCalculationService.java"));
        assertTrue(text.contains(
                "numerator.calculate( user, eventDate, referenceWindow, discoveryThroughMonth, "
                        + "provenNoPayrollMonths )"
        ));
        assertTrue(text.contains("calendar.resolve(user, eventDate, referenceWindow)"));
        assertTrue(text.contains("AverageEarningsReferenceWindow.primary(eventDate)"));
    }

    @Test
    void j1AddsNoPersistenceOrFallbackSelection() throws IOException {
        String vacation = source("VacationAveragePrimaryCalculationService.java");
        String window = source("AverageEarningsReferenceWindow.java");
        assertFalse(vacation.contains("PARAGRAPH_6"));
        assertFalse(vacation.contains("PARAGRAPH_7"));
        assertFalse(vacation.contains("PARAGRAPH_8"));
        assertFalse(window.contains("Repository"));
        assertFalse(window.contains("@Entity"));
    }

    private static List<String> parameterizedChain() {
        return List.of(
                "AverageEarningsNumeratorFactsService.java",
                "AverageEarningsNumeratorCalculationService.java",
                "AverageEarningsParagraph5MoneyPolicy.java",
                "AverageEarningsReferenceFactsService.java",
                "AverageEarningsBonusP15HistoricalFactDiscoveryService.java",
                "AverageEarningsBonusP15ReferenceWorkedTimeFactService.java",
                "AverageEarningsBonusP15ReferenceCompletenessService.java",
                "AverageEarningsBonusP15CalculationPipelineService.java",
                "PayrollHistoricalSemanticEarningsService.java",
                "VacationAverageCalendarDenominator.java",
                "VacationAverageReferenceCalendarService.java",
                "VacationAveragePrimaryCalculationService.java"
        );
    }

    private static String source(String file) throws IOException {
        return Files.readString(SERVICE.resolve(file));
    }

    private static String compact(String text) {
        return text.replaceAll("\\s+", " ").trim();
    }
}
