package ru.daniil.shifts.service;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.time.LocalDate;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VacationPayMoneyFormulaTest {
    private static final long ABSENCE_ID = 77L;
    private static final LocalDate EVENT = LocalDate.of(2026, 8, 31);

    @Test
    void exactIntegralProductProducesMinorMoneyWithoutExtraRounding() {
        var result = VacationPayMoneyFormula.calculate(
                daily(EVENT, true, "RUB", rate(2500, 1), null),
                days(EVENT, true, 14, null)
        );
        assertTrue(result.ready());
        assertEquals(35000L, result.vacationPayMinor());
        assertEquals(new VacationPayMoneyFormula.ExactVacationPay(BigInteger.valueOf(35000), BigInteger.ONE), result.exactVacationPay());
    }

    @Test
    void fractionBelowHalfRoundsDownOnlyAtFinalMinorBoundary() {
        var result = VacationPayMoneyFormula.calculate(
                daily(EVENT, true, "RUB", rate(7, 3), null),
                days(EVENT, true, 1, null)
        );
        assertEquals(2L, result.vacationPayMinor());
        assertEquals(BigInteger.valueOf(7), result.exactVacationPay().numeratorMinor());
        assertEquals(BigInteger.valueOf(3), result.exactVacationPay().denominator());
    }

    @Test
    void fractionAboveHalfRoundsUpOnlyAtFinalMinorBoundary() {
        var result = VacationPayMoneyFormula.calculate(
                daily(EVENT, true, "RUB", rate(8, 3), null),
                days(EVENT, true, 1, null)
        );
        assertEquals(3L, result.vacationPayMinor());
    }

    @Test
    void exactHalfMinorRoundsAwayFromZeroUnderExplicitHalfUpPolicy() {
        var result = VacationPayMoneyFormula.calculate(
                daily(EVENT, true, "RUB", rate(1, 2), null),
                days(EVENT, true, 1, null)
        );
        assertEquals(1L, result.vacationPayMinor());
        assertEquals(VacationPayMoneyFormula.ROUNDING_POLICY, result.roundingPolicy());
    }

    @Test
    void multiplicationRetainsExactRationalProductBeforeFinalRounding() {
        var result = VacationPayMoneyFormula.calculate(
                daily(EVENT, true, "RUB", rate(5, 7), null),
                days(EVENT, true, 3, null)
        );
        assertEquals(BigInteger.valueOf(15), result.exactVacationPay().numeratorMinor());
        assertEquals(BigInteger.valueOf(7), result.exactVacationPay().denominator());
        assertEquals(2L, result.vacationPayMinor());
    }

    @Test
    void exactProductIsReducedAfterMultiplication() {
        var result = VacationPayMoneyFormula.calculate(
                daily(EVENT, true, "RUB", rate(3, 2), null),
                days(EVENT, true, 4, null)
        );
        assertEquals(BigInteger.valueOf(6), result.exactVacationPay().numeratorMinor());
        assertEquals(BigInteger.ONE, result.exactVacationPay().denominator());
    }

    @Test
    void provenZeroPayableDaysProduceReadyZeroMoney() {
        var result = VacationPayMoneyFormula.calculate(
                daily(EVENT, true, "RUB", rate(12345, 293), null),
                days(EVENT, true, 0, null)
        );
        assertTrue(result.ready());
        assertEquals(0L, result.vacationPayMinor());
        assertEquals(BigInteger.ZERO, result.exactVacationPay().numeratorMinor());
        assertEquals(BigInteger.ONE, result.exactVacationPay().denominator());
    }

    @Test
    void exactZeroDailyRateWithKnownCurrencyProducesReadyZeroMoney() {
        var result = VacationPayMoneyFormula.calculate(
                daily(EVENT, true, "RUB", rate(0, 1), null),
                days(EVENT, true, 14, null)
        );
        assertTrue(result.ready());
        assertEquals(0L, result.vacationPayMinor());
    }

    @Test
    void veryLargeExactArithmeticCanRemainReadyWhenRoundedValueFitsLong() {
        BigInteger numerator = BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.valueOf(2));
        var result = VacationPayMoneyFormula.calculate(
                daily(EVENT, true, "RUB", new VacationAverageDailyEarningsFormula.ExactMoneyPerDay(numerator, BigInteger.valueOf(4)), null),
                days(EVENT, true, 2, null)
        );
        assertTrue(result.ready());
        assertEquals(Long.MAX_VALUE, result.vacationPayMinor());
    }

    @Test
    void finalAmountOverflowBlocksInsteadOfWrapping() {
        BigInteger numerator = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE);
        var result = VacationPayMoneyFormula.calculate(
                daily(EVENT, true, "RUB", new VacationAverageDailyEarningsFormula.ExactMoneyPerDay(numerator, BigInteger.ONE), null),
                days(EVENT, true, 1, null)
        );
        assertBlocked(result, VacationPayMoneyFormula.BlockingStage.AMOUNT, VacationPayMoneyFormula.AMOUNT_OVERFLOW);
    }

    @Test
    void blockedKStopsBeforeMoneyAndPreservesUpstreamReason() {
        var result = VacationPayMoneyFormula.calculate(
                daily(EVENT, false, null, null, "K_BLOCK"),
                days(EVENT, true, 14, null)
        );
        assertBlocked(result, VacationPayMoneyFormula.BlockingStage.DAILY_AUTHORITY, VacationPayMoneyFormula.DAILY_AUTHORITY_BLOCKED);
        assertEquals("K_BLOCK", result.upstreamBlockingReason());
    }

    @Test
    void blockedKWithoutReasonUsesNoInventedUpstreamReason() {
        var result = VacationPayMoneyFormula.calculate(
                daily(EVENT, false, null, null, null),
                days(EVENT, true, 14, null)
        );
        assertNull(result.upstreamBlockingReason());
    }

    @Test
    void blockedLStopsBeforeMoneyAndPreservesUpstreamReason() {
        var result = VacationPayMoneyFormula.calculate(
                daily(EVENT, true, "RUB", rate(10, 1), null),
                days(EVENT, false, 0, "L_BLOCK")
        );
        assertBlocked(result, VacationPayMoneyFormula.BlockingStage.PAYABLE_DAYS_AUTHORITY, VacationPayMoneyFormula.PAYABLE_DAYS_AUTHORITY_BLOCKED);
        assertEquals("L_BLOCK", result.upstreamBlockingReason());
    }

    @Test
    void blockedLWithoutReasonUsesNoInventedUpstreamReason() {
        var result = VacationPayMoneyFormula.calculate(
                daily(EVENT, true, "RUB", rate(10, 1), null),
                days(EVENT, false, 0, null)
        );
        assertNull(result.upstreamBlockingReason());
    }

    @Test
    void whenBothAuthoritiesAreBlockedKIsTheFirstBlockingStage() {
        var result = VacationPayMoneyFormula.calculate(
                daily(EVENT, false, null, null, "K_BLOCK"),
                days(EVENT, false, 0, "L_BLOCK")
        );
        assertEquals(VacationPayMoneyFormula.BlockingStage.DAILY_AUTHORITY, result.blockingStage());
        assertEquals("K_BLOCK", result.upstreamBlockingReason());
    }

    @Test
    void differentEventDatesBlockBeforeEitherAuthorityCanProduceMoney() {
        var result = VacationPayMoneyFormula.calculate(
                daily(EVENT, true, "RUB", rate(10, 1), null),
                days(EVENT.plusDays(1), true, 14, null)
        );
        assertBlocked(result, VacationPayMoneyFormula.BlockingStage.IDENTITY, VacationPayMoneyFormula.IDENTITY_MISMATCH);
    }

    @Test
    void mismatchedKEventMonthBlocksIdentity() {
        var k = daily(EVENT, true, "RUB", rate(10, 1), null);
        when(k.eventMonth()).thenReturn(YearMonth.of(2026, 7));
        var result = VacationPayMoneyFormula.calculate(k, days(EVENT, true, 14, null));
        assertBlocked(result, VacationPayMoneyFormula.BlockingStage.IDENTITY, VacationPayMoneyFormula.IDENTITY_MISMATCH);
    }

    @Test
    void mismatchedLEventMonthBlocksIdentity() {
        var l = days(EVENT, true, 14, null);
        when(l.eventMonth()).thenReturn(YearMonth.of(2026, 7));
        var result = VacationPayMoneyFormula.calculate(daily(EVENT, true, "RUB", rate(10, 1), null), l);
        assertBlocked(result, VacationPayMoneyFormula.BlockingStage.IDENTITY, VacationPayMoneyFormula.IDENTITY_MISMATCH);
    }

    @Test
    void readyLMustStartOnTheSameExactVacationEventDate() {
        var l = days(EVENT, true, 14, null);
        when(l.vacationFrom()).thenReturn(EVENT.minusDays(1));
        var result = VacationPayMoneyFormula.calculate(daily(EVENT, true, "RUB", rate(10, 1), null), l);
        assertBlocked(result, VacationPayMoneyFormula.BlockingStage.IDENTITY, VacationPayMoneyFormula.IDENTITY_MISMATCH);
    }

    @Test
    void readyLMustExposePersistedAbsenceIdentity() {
        var l = days(EVENT, true, 14, null);
        when(l.absencePeriodId()).thenReturn(null);
        var result = VacationPayMoneyFormula.calculate(daily(EVENT, true, "RUB", rate(10, 1), null), l);
        assertBlocked(result, VacationPayMoneyFormula.BlockingStage.IDENTITY, VacationPayMoneyFormula.IDENTITY_MISMATCH);
    }

    @Test
    void readyLMustMatchRequestedAndResolvedAbsenceIdentity() {
        var l = days(EVENT, true, 14, null);
        when(l.requestedAbsencePeriodId()).thenReturn(88L);
        var result = VacationPayMoneyFormula.calculate(daily(EVENT, true, "RUB", rate(10, 1), null), l);
        assertBlocked(result, VacationPayMoneyFormula.BlockingStage.IDENTITY, VacationPayMoneyFormula.IDENTITY_MISMATCH);
    }

    @Test
    void nullCurrencyBlocksEvenWhenExactFinalAmountWouldBeZero() {
        var result = VacationPayMoneyFormula.calculate(
                daily(EVENT, true, null, rate(0, 1), null),
                days(EVENT, true, 0, null)
        );
        assertBlocked(result, VacationPayMoneyFormula.BlockingStage.CURRENCY, VacationPayMoneyFormula.CURRENCY_REQUIRED);
    }

    @Test
    void lowercaseCurrencyBlocksFinalMoneyIdentity() {
        var result = VacationPayMoneyFormula.calculate(
                daily(EVENT, true, "rub", rate(10, 1), null),
                days(EVENT, true, 1, null)
        );
        assertBlocked(result, VacationPayMoneyFormula.BlockingStage.CURRENCY, VacationPayMoneyFormula.CURRENCY_REQUIRED);
    }

    @Test
    void malformedTwoLetterCurrencyBlocksFinalMoneyIdentity() {
        var result = VacationPayMoneyFormula.calculate(
                daily(EVENT, true, "RU", rate(10, 1), null),
                days(EVENT, true, 1, null)
        );
        assertBlocked(result, VacationPayMoneyFormula.BlockingStage.CURRENCY, VacationPayMoneyFormula.CURRENCY_REQUIRED);
    }

    @Test
    void readyKWithoutExactDailyRateBlocksInsteadOfInventingMoney() {
        var result = VacationPayMoneyFormula.calculate(
                daily(EVENT, true, "RUB", null, null),
                days(EVENT, true, 14, null)
        );
        assertBlocked(result, VacationPayMoneyFormula.BlockingStage.DAILY_AUTHORITY, VacationPayMoneyFormula.DAILY_AUTHORITY_BLOCKED);
    }

    @Test
    void exactVacationPayRejectsNegativeNumerator() {
        assertThrows(IllegalArgumentException.class, () ->
                new VacationPayMoneyFormula.ExactVacationPay(BigInteger.valueOf(-1), BigInteger.ONE));
    }

    @Test
    void exactVacationPayRejectsNonPositiveDenominator() {
        assertThrows(IllegalArgumentException.class, () ->
                new VacationPayMoneyFormula.ExactVacationPay(BigInteger.ONE, BigInteger.ZERO));
    }

    @Test
    void exactVacationPayNormalizesZeroToZeroOverOne() {
        var exact = new VacationPayMoneyFormula.ExactVacationPay(BigInteger.ZERO, BigInteger.valueOf(293));
        assertEquals(BigInteger.ZERO, exact.numeratorMinor());
        assertEquals(BigInteger.ONE, exact.denominator());
    }

    @Test
    void calculateRequiresKAuthority() {
        assertThrows(NullPointerException.class, () ->
                VacationPayMoneyFormula.calculate(null, days(EVENT, true, 1, null)));
    }

    @Test
    void calculateRequiresLAuthority() {
        assertThrows(NullPointerException.class, () ->
                VacationPayMoneyFormula.calculate(daily(EVENT, true, "RUB", rate(1, 1), null), null));
    }

    private VacationAverageUnifiedDailyResolver.Resolution daily(
            LocalDate event,
            boolean ready,
            String currency,
            VacationAverageDailyEarningsFormula.ExactMoneyPerDay rate,
            String blockingReason
    ) {
        var resolution = mock(VacationAverageUnifiedDailyResolver.Resolution.class);
        when(resolution.eventDate()).thenReturn(event);
        when(resolution.eventMonth()).thenReturn(YearMonth.from(event));
        when(resolution.ready()).thenReturn(ready);
        when(resolution.currencyCode()).thenReturn(currency);
        when(resolution.averageDaily()).thenReturn(rate);
        when(resolution.blockingReason()).thenReturn(blockingReason);
        return resolution;
    }

    private VacationPayableDaysFactService.Resolution days(
            LocalDate event,
            boolean ready,
            int payableDays,
            String blockingReason
    ) {
        var resolution = mock(VacationPayableDaysFactService.Resolution.class);
        when(resolution.eventDate()).thenReturn(event);
        when(resolution.eventMonth()).thenReturn(YearMonth.from(event));
        when(resolution.ready()).thenReturn(ready);
        when(resolution.blockingReason()).thenReturn(blockingReason);
        when(resolution.vacationFrom()).thenReturn(ready ? event : null);
        when(resolution.absencePeriodId()).thenReturn(ready ? ABSENCE_ID : null);
        when(resolution.requestedAbsencePeriodId()).thenReturn(ABSENCE_ID);
        when(resolution.payableCalendarDays()).thenReturn(payableDays);
        return resolution;
    }

    private VacationAverageDailyEarningsFormula.ExactMoneyPerDay rate(long numerator, long denominator) {
        return new VacationAverageDailyEarningsFormula.ExactMoneyPerDay(
                BigInteger.valueOf(numerator),
                BigInteger.valueOf(denominator)
        );
    }

    private void assertBlocked(
            VacationPayMoneyFormula.Resolution result,
            VacationPayMoneyFormula.BlockingStage stage,
            String reason
    ) {
        assertFalse(result.ready());
        assertEquals(stage, result.blockingStage());
        assertEquals(reason, result.blockingReason());
        assertNull(result.currencyCode());
        assertNull(result.averageDaily());
        assertEquals(0, result.payableCalendarDays());
        assertNull(result.exactVacationPay());
        assertNull(result.vacationPayMinor());
        assertNull(result.roundingPolicy());
    }
}
