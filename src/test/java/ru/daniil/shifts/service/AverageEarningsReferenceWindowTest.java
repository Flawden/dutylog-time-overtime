package ru.daniil.shifts.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.*;

class AverageEarningsReferenceWindowTest {

    private static final LocalDate EVENT = LocalDate.of(2026, 8, 15);

    @Test
    void primaryWindowRemainsTwelveMonthsImmediatelyBeforeEventMonth() {
        var window = AverageEarningsReferenceWindow.primary(EVENT);

        assertEquals(YearMonth.of(2026, 8), window.eventMonth());
        assertEquals(YearMonth.of(2025, 8), window.referenceFrom());
        assertEquals(YearMonth.of(2026, 7), window.referenceTo());
        assertTrue(window.primary());
    }

    @Test
    void previousEqualWindowIsValidWithoutChangingEventMonth() {
        var window = new AverageEarningsReferenceWindow(
                YearMonth.of(2026, 8),
                YearMonth.of(2024, 8),
                YearMonth.of(2025, 7)
        );

        assertFalse(window.primary());
        assertEquals(YearMonth.of(2026, 8), window.eventMonth());
        assertEquals(LocalDate.of(2024, 8, 1), window.referenceFromDate());
        assertEquals(LocalDate.of(2025, 7, 31), window.referenceToDate());
    }

    @Test
    void factoryFromLegalEventDateKeepsTrueEventMonthForEarlierWindow() {
        var window = AverageEarningsReferenceWindow.of(
                EVENT,
                YearMonth.of(2024, 8),
                YearMonth.of(2025, 7)
        );

        assertEquals(YearMonth.of(2026, 8), window.eventMonth());
        assertFalse(window.primary());
    }

    @Test
    void windowMustContainExactlyTwelveConsecutiveMonths() {
        assertThrows(IllegalArgumentException.class, () ->
                new AverageEarningsReferenceWindow(
                        YearMonth.of(2026, 8),
                        YearMonth.of(2024, 8),
                        YearMonth.of(2025, 6)
                ));

        assertThrows(IllegalArgumentException.class, () ->
                new AverageEarningsReferenceWindow(
                        YearMonth.of(2026, 8),
                        YearMonth.of(2024, 8),
                        YearMonth.of(2025, 8)
                ));
    }

    @Test
    void referenceWindowMustEndBeforeEventMonth() {
        assertThrows(IllegalArgumentException.class, () ->
                new AverageEarningsReferenceWindow(
                        YearMonth.of(2026, 8),
                        YearMonth.of(2025, 9),
                        YearMonth.of(2026, 8)
                ));
    }

    @Test
    void legalEventDateMustBelongToWindowEventMonth() {
        var window = AverageEarningsReferenceWindow.primary(EVENT);

        assertDoesNotThrow(() -> window.requireEventDate(EVENT));
        assertThrows(IllegalArgumentException.class, () ->
                window.requireEventDate(LocalDate.of(2026, 7, 31)));
    }

    @Test
    void primaryFactoryFromYearMonthMatchesDateFactory() {
        assertEquals(
                AverageEarningsReferenceWindow.primary(EVENT),
                AverageEarningsReferenceWindow.primary(YearMonth.of(2026, 8))
        );
    }

    @Test
    void nullBoundariesFailAtValueObjectBoundary() {
        assertThrows(NullPointerException.class, () ->
                new AverageEarningsReferenceWindow(null, YearMonth.of(2025, 8), YearMonth.of(2026, 7)));
        assertThrows(NullPointerException.class, () ->
                new AverageEarningsReferenceWindow(YearMonth.of(2026, 8), null, YearMonth.of(2026, 7)));
        assertThrows(NullPointerException.class, () ->
                new AverageEarningsReferenceWindow(YearMonth.of(2026, 8), YearMonth.of(2025, 8), null));
    }

    @Test
    void precedingEqualMovesExactlyTwelveMonthsAndPreservesEventMonth() {
        var primary = AverageEarningsReferenceWindow.primary(EVENT);

        var preceding = primary.precedingEqual();

        assertEquals(primary.eventMonth(), preceding.eventMonth());
        assertEquals(primary.referenceFrom().minusMonths(12), preceding.referenceFrom());
        assertEquals(primary.referenceTo().minusMonths(12), preceding.referenceTo());
        assertFalse(preceding.primary());
    }

    @Test
    void precedingEqualAlsoWorksForAlreadyExplicitWindowWithoutChangingLegalEventMonth() {
        var explicit = AverageEarningsReferenceWindow.of(
                EVENT,
                YearMonth.of(2024, 9),
                YearMonth.of(2025, 8)
        );

        var preceding = explicit.precedingEqual();

        assertEquals(YearMonth.from(EVENT), preceding.eventMonth());
        assertEquals(YearMonth.of(2023, 9), preceding.referenceFrom());
        assertEquals(YearMonth.of(2024, 8), preceding.referenceTo());
    }

}
