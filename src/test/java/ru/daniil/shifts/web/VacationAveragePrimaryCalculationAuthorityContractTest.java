package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class VacationAveragePrimaryCalculationAuthorityContractTest {

    private static final Path SERVICE = Path.of(
            "src/main/java/ru/daniil/shifts/service/VacationAveragePrimaryCalculationService.java"
    );
    private static final Path FORMULA = Path.of(
            "src/main/java/ru/daniil/shifts/service/VacationAverageDailyEarningsFormula.java"
    );
    private static final Path DENOMINATOR = Path.of(
            "src/main/java/ru/daniil/shifts/service/VacationAverageCalendarDenominator.java"
    );
    private static final Path NUMERATOR = Path.of(
            "src/main/java/ru/daniil/shifts/service/AverageEarningsNumeratorCalculationService.java"
    );

    @Test
    void primaryPipelineIsFinalNumeratorThenCalendarDenominatorThenExactDailyFormula() throws IOException {
        String text = Files.readString(SERVICE);
        int numerator = text.indexOf("numerator.calculate(");
        int calendar = text.indexOf("calendar.resolve(");
        int formula = text.indexOf("VacationAverageDailyEarningsFormula.calculate(");
        assertTrue(numerator >= 0 && numerator < calendar);
        assertTrue(calendar < formula);
    }

    @Test
    void averageDailyFormulaContainsNoDecimalOrRoundingPolicy() throws IOException {
        String text = Files.readString(FORMULA);
        assertFalse(text.contains("BigDecimal"));
        assertFalse(text.contains("RoundingMode"));
        assertFalse(text.contains("HALF_UP"));
        assertFalse(text.contains("double"));
        assertTrue(text.contains("BigInteger"));
    }

    @Test
    void paragraph13HourlyAuthorityCannotLeakIntoVacationPrimaryCalculation() throws IOException {
        String service = Files.readString(SERVICE);
        String formula = Files.readString(FORMULA);
        for (String forbidden : new String[]{
                "WorkTimeAccountingMode",
                "ReferenceWorkedTimeFact",
                "averageHourly",
                "WORKING_MINUTES"
        }) {
            assertFalse(service.contains(forbidden), forbidden);
            assertFalse(formula.contains(forbidden), forbidden);
        }
        assertTrue(service.contains("Paragraph 13 average-hourly earnings is intentionally absent"));
    }

    @Test
    void primaryAverageDailyLayerStopsBeforeVacationPayMoneyAndPayableDayMultiplication() throws IOException {
        String service = Files.readString(SERVICE);
        String formula = Files.readString(FORMULA);
        assertFalse(service.contains("vacationPayAmountMinor"));
        assertFalse(formula.contains("vacationPayAmountMinor"));
        assertFalse(formula.contains("payableVacationDays"));
        assertTrue(formula.contains("does not calculate vacation-pay money"));
    }

    @Test
    void existingNumeratorAndDenominatorBoundariesDoNotDependOnNewPrimaryLayer() throws IOException {
        String numerator = Files.readString(NUMERATOR);
        String denominator = Files.readString(DENOMINATOR);
        assertFalse(numerator.contains("VacationAveragePrimaryCalculationService"));
        assertFalse(denominator.contains("VacationAveragePrimaryCalculationService"));
        assertFalse(denominator.contains("VacationAverageDailyEarningsFormula"));
    }
}
