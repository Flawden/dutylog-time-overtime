package ru.daniil.shifts.service;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.*;

class VacationAverageDailyEarningsFormulaTest {

    @Test
    void twelveFullMonthsProduceExactDailyMinorRateWithoutRounding() {
        var rate = VacationAverageDailyEarningsFormula.calculate(
                3_516_000L,
                VacationAverageCalendarDenominator.ExactDays.of(1758, 5)
        );

        assertEquals(BigInteger.valueOf(10_000L), rate.numeratorMinor());
        assertEquals(BigInteger.ONE, rate.denominatorDays());
    }

    @Test
    void partialMonthDenominatorRemainsExactRationalMoneyPerDay() {
        var rate = VacationAverageDailyEarningsFormula.calculate(
                1_000_000L,
                VacationAverageCalendarDenominator.ExactDays.of(105773, 310)
        );

        assertEquals(BigInteger.valueOf(310_000_000L), rate.numeratorMinor());
        assertEquals(BigInteger.valueOf(105_773L), rate.denominatorDays());
    }

    @Test
    void exactRateIsReducedByGreatestCommonDivisor() {
        var rate = new VacationAverageDailyEarningsFormula.ExactMoneyPerDay(
                BigInteger.valueOf(200),
                BigInteger.valueOf(40)
        );

        assertEquals(BigInteger.valueOf(5), rate.numeratorMinor());
        assertEquals(BigInteger.ONE, rate.denominatorDays());
    }

    @Test
    void zeroNumeratorCanonicalizesToZeroOverOne() {
        var rate = VacationAverageDailyEarningsFormula.calculate(
                0L,
                VacationAverageCalendarDenominator.ExactDays.of(1758, 5)
        );

        assertEquals(BigInteger.ZERO, rate.numeratorMinor());
        assertEquals(BigInteger.ONE, rate.denominatorDays());
    }

    @Test
    void negativeNumeratorIsRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                VacationAverageDailyEarningsFormula.calculate(
                        -1L,
                        VacationAverageCalendarDenominator.ExactDays.of(1, 1)
                ));
    }

    @Test
    void nullDenominatorIsRejected() {
        assertThrows(NullPointerException.class, () ->
                VacationAverageDailyEarningsFormula.calculate(1L, null));
    }

    @Test
    void zeroDenominatorDaysAuthorityIsRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                VacationAverageDailyEarningsFormula.calculate(
                        1L,
                        VacationAverageCalendarDenominator.ExactDays.zero()
                ));
    }

    @Test
    void exactRateRecordRejectsNegativeMoney() {
        assertThrows(IllegalArgumentException.class, () ->
                new VacationAverageDailyEarningsFormula.ExactMoneyPerDay(
                        BigInteger.valueOf(-1),
                        BigInteger.ONE
                ));
    }

    @Test
    void exactRateRecordRejectsNonPositiveDenominator() {
        assertThrows(IllegalArgumentException.class, () ->
                new VacationAverageDailyEarningsFormula.ExactMoneyPerDay(
                        BigInteger.ONE,
                        BigInteger.ZERO
                ));
    }
}
