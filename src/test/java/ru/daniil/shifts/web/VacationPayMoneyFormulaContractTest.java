package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class VacationPayMoneyFormulaContractTest {
    private static final String SOURCE =
            "src/main/java/ru/daniil/shifts/service/VacationPayMoneyFormula.java";

    @Test
    void MConsumesExactlyKDailyAndLPayableDayAuthorities() throws IOException {
        String source = source();
        assertTrue(source.contains("VacationAverageUnifiedDailyResolver.Resolution dailyAuthority"));
        assertTrue(source.contains("VacationPayableDaysFactService.Resolution payableDaysAuthority"));
        assertTrue(source.contains("dailyAuthority.averageDaily()"));
        assertTrue(source.contains("payableDaysAuthority.payableCalendarDays()"));
    }

    @Test
    void MImplementsParagraph9MultiplicationAsExactRationalArithmetic() throws IOException {
        String source = source();
        assertTrue(source.contains("RULE_ID = \"PP_540_P9_VACATION_PAY_MONEY\""));
        assertTrue(source.contains(".multiply(BigInteger.valueOf(payableCalendarDays))"));
        assertTrue(source.contains("ExactVacationPay"));
    }

    @Test
    void MHasExactlyOneExplicitFinalMinorUnitRoundingPolicy() throws IOException {
        String source = source();
        assertTrue(source.contains("ROUNDING_POLICY = \"FINAL_MINOR_UNIT_HALF_UP\""));
        assertTrue(source.contains("divideAndRemainder"));
        assertTrue(source.contains("remainder") || source.contains("quotientAndRemainder[1]"));
        assertTrue(source.contains("multiply(TWO).compareTo(exact.denominator()) >= 0"));
    }

    @Test
    void MUsesNoDecimalOrFloatingPointIntermediateMoney() throws IOException {
        String source = source();
        assertFalse(source.contains("BigDecimal"));
        assertFalse(source.contains("RoundingMode"));
        assertFalse(source.contains("double"));
        assertFalse(source.contains("float"));
    }

    @Test
    void MValidatesCommonEventAndAbsenceIdentityBeforeMoney() throws IOException {
        String source = source();
        assertTrue(source.contains("IDENTITY_MISMATCH"));
        assertTrue(source.contains("payableDaysAuthority.vacationFrom()"));
        assertTrue(source.contains("payableDaysAuthority.absencePeriodId()"));
        assertTrue(source.contains("payableDaysAuthority.requestedAbsencePeriodId()"));
    }

    @Test
    void MRequiresCurrencyEvenForFinalZeroMoney() throws IOException {
        String source = source();
        assertTrue(source.contains("CURRENCY_REQUIRED"));
        assertTrue(source.contains("currencyCode.matches(\"[A-Z]{3}\")"));
        assertTrue(source.contains("if (!validCurrency(currencyCode))"));
    }

    @Test
    void MBlocksOverflowInsteadOfWrappingLongMoney() throws IOException {
        String source = source();
        assertTrue(source.contains("AMOUNT_OVERFLOW"));
        assertTrue(source.contains("LONG_MAX"));
        assertTrue(source.contains("longValueExact()"));
    }

    @Test
    void MDoesNotRecomputeKOrLPolicies() throws IOException {
        String source = source();
        assertFalse(source.contains("AverageEarningsOrderedFallbackResolver.resolve"));
        assertFalse(source.contains("AnnualPaidVacationHolidayPolicy.classify"));
        assertFalse(source.contains("VacationSettings"));
        assertFalse(source.contains("ProductionCalendarService"));
        assertFalse(source.contains("AbsencePeriodRepository"));
    }

    private String source() throws IOException {
        return Files.readString(Path.of(SOURCE), StandardCharsets.UTF_8);
    }
}
