package ru.daniil.shifts.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.daniil.shifts.model.AppUser;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VacationAveragePrimaryCalculationServiceTest {

    private static final LocalDate EVENT = LocalDate.of(2026, 9, 10);
    private static final YearMonth EVENT_MONTH = YearMonth.of(2026, 9);
    private static final YearMonth FROM = YearMonth.of(2025, 9);
    private static final YearMonth TO = YearMonth.of(2026, 8);
    private static final YearMonth THROUGH = YearMonth.of(2026, 10);

    @Mock
    private AverageEarningsNumeratorCalculationService numerator;
    @Mock
    private VacationAverageReferenceCalendarService calendar;
    @Mock
    private AppUser user;

    private VacationAveragePrimaryCalculationService service;

    @BeforeEach
    void setUp() {
        service = new VacationAveragePrimaryCalculationService(numerator, calendar);
    }

    @Test
    void readyPrimaryAuthorityProducesExactAverageDailyWithoutIntermediateRounding() {
        var money = readyNumerator(3_516_000L, "RUB");
        var calendarResult = readyCalendar(VacationAverageCalendarDenominator.ExactDays.of(1758, 5));
        when(numerator.calculate(user, EVENT, THROUGH, List.of())).thenReturn(money);
        when(calendar.resolve(user, EVENT)).thenReturn(calendarResult);

        var result = service.calculate(user, EVENT, THROUGH, List.of());

        assertTrue(result.ready());
        assertEquals("RUB", result.currencyCode());
        assertEquals(java.math.BigInteger.valueOf(10_000L), result.averageDaily().numeratorMinor());
        assertEquals(java.math.BigInteger.ONE, result.averageDaily().denominatorDays());
    }

    @Test
    void numeratorBlockerPropagatesBeforeCalendarAuthorityIsRead() {
        var blocked = mock(AverageEarningsNumeratorCalculationService.Resolution.class);
        when(blocked.ready()).thenReturn(false);
        when(blocked.blockingReason()).thenReturn("NUMERATOR_BLOCKED");
        when(blocked.blockingPeriod()).thenReturn(YearMonth.of(2026, 1));
        when(numerator.calculate(user, EVENT, THROUGH, List.of())).thenReturn(blocked);

        var result = service.calculate(user, EVENT, THROUGH, List.of());

        assertFalse(result.ready());
        assertEquals("NUMERATOR_BLOCKED", result.blockingReason());
        assertEquals(YearMonth.of(2026, 1), result.blockingPeriod());
        verifyNoInteractions(calendar);
    }

    @Test
    void nullNumeratorAuthorityFailsFastBeforeCalendar() {
        when(numerator.calculate(user, EVENT, THROUGH, List.of())).thenReturn(null);

        assertThrows(NullPointerException.class,
                () -> service.calculate(user, EVENT, THROUGH, List.of()));

        verifyNoInteractions(calendar);
    }

    @Test
    void numeratorWindowMismatchBlocksBeforeCalendar() {
        var money = mock(AverageEarningsNumeratorCalculationService.Resolution.class);
        when(money.ready()).thenReturn(true);
        when(money.eventDate()).thenReturn(EVENT);
        when(money.eventMonth()).thenReturn(EVENT_MONTH);
        when(money.referenceFrom()).thenReturn(FROM.minusMonths(1));
        when(numerator.calculate(user, EVENT, THROUGH, List.of())).thenReturn(money);

        var result = service.calculate(user, EVENT, THROUGH, List.of());

        assertFalse(result.ready());
        assertEquals(VacationAveragePrimaryCalculationService.AUTHORITY_WINDOW_MISMATCH,
                result.blockingReason());
        verifyNoInteractions(calendar);
    }

    @Test
    void nullCalendarAuthorityFailsFastAfterReadyNumerator() {
        var money = readyNumerator(1L, "RUB");
        when(numerator.calculate(user, EVENT, THROUGH, List.of())).thenReturn(money);
        when(calendar.resolve(user, EVENT)).thenReturn(null);

        assertThrows(NullPointerException.class,
                () -> service.calculate(user, EVENT, THROUGH, List.of()));
    }

    @Test
    void calendarReferenceWindowMismatchBlocksBeforeFormula() {
        var money = readyNumerator(1L, "RUB");
        when(numerator.calculate(user, EVENT, THROUGH, List.of())).thenReturn(money);
        var wrong = mock(VacationAverageReferenceCalendarService.Result.class);
        when(wrong.eventDate()).thenReturn(EVENT);
        when(wrong.eventMonth()).thenReturn(EVENT_MONTH);
        when(wrong.referenceFrom()).thenReturn(FROM.minusMonths(1).atDay(1));
        when(calendar.resolve(user, EVENT)).thenReturn(wrong);

        var result = service.calculate(user, EVENT, THROUGH, List.of());

        assertFalse(result.ready());
        assertEquals(VacationAveragePrimaryCalculationService.AUTHORITY_WINDOW_MISMATCH,
                result.blockingReason());
    }

    @Test
    void nestedDenominatorWindowMismatchAlsoBlocks() {
        var money = readyNumerator(1L, "RUB");
        when(numerator.calculate(user, EVENT, THROUGH, List.of())).thenReturn(money);

        var calendarResult = mock(VacationAverageReferenceCalendarService.Result.class);
        when(calendarResult.eventDate()).thenReturn(EVENT);
        when(calendarResult.eventMonth()).thenReturn(EVENT_MONTH);
        when(calendarResult.referenceFrom()).thenReturn(FROM.atDay(1));
        when(calendarResult.referenceTo()).thenReturn(TO.atEndOfMonth());

        var denominator = mock(VacationAverageCalendarDenominator.Result.class);
        when(denominator.eventDate()).thenReturn(EVENT);
        when(denominator.eventMonth()).thenReturn(EVENT_MONTH);
        when(denominator.referenceFrom()).thenReturn(FROM);
        when(denominator.referenceTo()).thenReturn(TO.minusMonths(1));
        when(calendarResult.denominator()).thenReturn(denominator);
        when(calendar.resolve(user, EVENT)).thenReturn(calendarResult);

        var result = service.calculate(user, EVENT, THROUGH, List.of());

        assertFalse(result.ready());
        assertEquals(VacationAveragePrimaryCalculationService.AUTHORITY_WINDOW_MISMATCH,
                result.blockingReason());
    }

    @Test
    void zeroNumeratorWithNoCurrencyRemainsExactReadyZeroRate() {
        var money = readyNumerator(0L, null);
        var calendarResult = readyCalendar(VacationAverageCalendarDenominator.ExactDays.of(1758, 5));
        when(numerator.calculate(user, EVENT, THROUGH, List.of())).thenReturn(money);
        when(calendar.resolve(user, EVENT)).thenReturn(calendarResult);

        var result = service.calculate(user, EVENT, THROUGH, List.of());

        assertTrue(result.ready());
        assertNull(result.currencyCode());
        assertEquals(java.math.BigInteger.ZERO, result.averageDaily().numeratorMinor());
        assertEquals(java.math.BigInteger.ONE, result.averageDaily().denominatorDays());
    }

    @Test
    void noPayrollProofsAreForwardedUnmodifiedToFinalNumeratorAuthority() {
        List<YearMonth> proofs = List.of(FROM, YearMonth.of(2026, 10));
        var money = readyNumerator(0L, null);
        var calendarResult = readyCalendar(VacationAverageCalendarDenominator.ExactDays.of(1758, 5));
        when(numerator.calculate(user, EVENT, THROUGH, proofs)).thenReturn(money);
        when(calendar.resolve(user, EVENT)).thenReturn(calendarResult);

        service.calculate(user, EVENT, THROUGH, proofs);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<YearMonth>> captor = ArgumentCaptor.forClass(List.class);
        verify(numerator).calculate(eq(user), eq(EVENT), eq(THROUGH), captor.capture());
        assertEquals(proofs, captor.getValue());
    }

    @Test
    void exactPartialMonthDenominatorFlowsThroughWithoutDecimalConversion() {
        var money = readyNumerator(1_000_000L, "RUB");
        var calendarResult = readyCalendar(VacationAverageCalendarDenominator.ExactDays.of(105773, 310));
        when(numerator.calculate(user, EVENT, THROUGH, List.of())).thenReturn(money);
        when(calendar.resolve(user, EVENT)).thenReturn(calendarResult);

        var result = service.calculate(user, EVENT, THROUGH, List.of());

        assertEquals(java.math.BigInteger.valueOf(310_000_000L),
                result.averageDaily().numeratorMinor());
        assertEquals(java.math.BigInteger.valueOf(105_773L),
                result.averageDaily().denominatorDays());
    }

    @Test
    void blockedResolutionCannotExposePartialAuthority() {
        assertThrows(IllegalArgumentException.class, () ->
                new VacationAveragePrimaryCalculationService.Resolution(
                        EVENT, EVENT_MONTH, FROM, TO, THROUGH,
                        false, "blocked", FROM, "RUB",
                        readyNumerator(1L, "RUB"), null, null
                ));
    }

    @Test
    void readyResolutionRequiresNumeratorCalendarAndExactDailyRate() {
        assertThrows(IllegalArgumentException.class, () ->
                new VacationAveragePrimaryCalculationService.Resolution(
                        EVENT, EVENT_MONTH, FROM, TO, THROUGH,
                        true, null, null, "RUB",
                        null, null, null
                ));
    }

    private AverageEarningsNumeratorCalculationService.Resolution readyNumerator(
            long amount,
            String currency
    ) {
        var result = mock(AverageEarningsNumeratorCalculationService.Resolution.class);
        lenient().when(result.ready()).thenReturn(true);
        lenient().when(result.eventDate()).thenReturn(EVENT);
        lenient().when(result.eventMonth()).thenReturn(EVENT_MONTH);
        lenient().when(result.referenceFrom()).thenReturn(FROM);
        lenient().when(result.referenceTo()).thenReturn(TO);
        lenient().when(result.discoveryThroughMonth()).thenReturn(THROUGH);
        lenient().when(result.currencyCode()).thenReturn(currency);
        lenient().when(result.numeratorAmountMinor()).thenReturn(amount);
        return result;
    }

    private VacationAverageReferenceCalendarService.Result readyCalendar(
            VacationAverageCalendarDenominator.ExactDays days
    ) {
        var denominator = mock(VacationAverageCalendarDenominator.Result.class);
        when(denominator.eventDate()).thenReturn(EVENT);
        when(denominator.eventMonth()).thenReturn(EVENT_MONTH);
        when(denominator.referenceFrom()).thenReturn(FROM);
        when(denominator.referenceTo()).thenReturn(TO);
        when(denominator.denominatorDays()).thenReturn(days);

        var result = mock(VacationAverageReferenceCalendarService.Result.class);
        when(result.eventDate()).thenReturn(EVENT);
        when(result.eventMonth()).thenReturn(EVENT_MONTH);
        when(result.referenceFrom()).thenReturn(FROM.atDay(1));
        when(result.referenceTo()).thenReturn(TO.atEndOfMonth());
        when(result.denominator()).thenReturn(denominator);
        return result;
    }
}
