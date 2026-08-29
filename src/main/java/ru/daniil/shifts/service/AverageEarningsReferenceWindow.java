package ru.daniil.shifts.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Objects;

/**
 * Immutable 12-month average-earnings reference window, separated from the
 * legal event date.
 *
 * <p>The legal event date remains authoritative for legal-regime selection and
 * event-year rules such as paragraph 15 annual rewards. This value object owns
 * only the selected reference months. The window must contain exactly twelve
 * consecutive calendar months and must end before the event month.</p>
 *
 * <p>The ordinary primary window is still {@code eventMonth-12 ..
 * eventMonth-1}. Paragraph-6 fallback can later select an earlier equal window
 * without changing the legal event date.</p>
 */
public record AverageEarningsReferenceWindow(
        YearMonth eventMonth,
        YearMonth referenceFrom,
        YearMonth referenceTo
) {
    public AverageEarningsReferenceWindow {
        Objects.requireNonNull(eventMonth, "Average earnings event month is required");
        Objects.requireNonNull(referenceFrom, "Average earnings reference start is required");
        Objects.requireNonNull(referenceTo, "Average earnings reference end is required");

        if (!referenceTo.equals(referenceFrom.plusMonths(11))) {
            throw new IllegalArgumentException(
                    "Average earnings reference window must contain exactly 12 consecutive months"
            );
        }
        if (!referenceTo.isBefore(eventMonth)) {
            throw new IllegalArgumentException(
                    "Average earnings reference window must end before the event month"
            );
        }
    }

    public static AverageEarningsReferenceWindow primary(LocalDate eventDate) {
        Objects.requireNonNull(eventDate, "Average earnings legal event date is required");
        return primary(YearMonth.from(eventDate));
    }

    public static AverageEarningsReferenceWindow primary(YearMonth eventMonth) {
        Objects.requireNonNull(eventMonth, "Average earnings event month is required");
        return new AverageEarningsReferenceWindow(
                eventMonth,
                eventMonth.minusMonths(12),
                eventMonth.minusMonths(1)
        );
    }

    public static AverageEarningsReferenceWindow of(
            LocalDate eventDate,
            YearMonth referenceFrom,
            YearMonth referenceTo
    ) {
        Objects.requireNonNull(eventDate, "Average earnings legal event date is required");
        return new AverageEarningsReferenceWindow(
                YearMonth.from(eventDate),
                referenceFrom,
                referenceTo
        );
    }

    public boolean primary() {
        return referenceFrom.equals(eventMonth.minusMonths(12))
                && referenceTo.equals(eventMonth.minusMonths(1));
    }

    public AverageEarningsReferenceWindow precedingEqual() {
        return new AverageEarningsReferenceWindow(
                eventMonth,
                referenceFrom.minusMonths(12),
                referenceTo.minusMonths(12)
        );
    }

    public LocalDate referenceFromDate() {
        return referenceFrom.atDay(1);
    }

    public LocalDate referenceToDate() {
        return referenceTo.atEndOfMonth();
    }

    public void requireEventDate(LocalDate eventDate) {
        Objects.requireNonNull(eventDate, "Average earnings legal event date is required");
        if (!eventMonth.equals(YearMonth.from(eventDate))) {
            throw new IllegalArgumentException(
                    "Average earnings reference window event month does not match legal event date"
            );
        }
    }
}
