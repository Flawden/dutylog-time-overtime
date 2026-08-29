package ru.daniil.shifts.service;

import java.math.BigInteger;
import java.util.Objects;

/**
 * Exact paragraph-10 average-daily earnings formula for calendar-day vacation.
 *
 * <p>The numerator is already legally assembled money in minor currency units.
 * The denominator is the exact calendar-day quantity produced by
 * {@link VacationAverageCalendarDenominator}. No decimal conversion or
 * intermediate monetary rounding is performed here.</p>
 *
 * <p>This formula does not calculate vacation-pay money. A later payment layer
 * may multiply this exact rate by payable vacation calendar days and choose the
 * legally appropriate final minor-unit rounding boundary.</p>
 */
public final class VacationAverageDailyEarningsFormula {

    private VacationAverageDailyEarningsFormula() {
    }

    public static ExactMoneyPerDay calculate(
            long numeratorAmountMinor,
            VacationAverageCalendarDenominator.ExactDays denominatorDays
    ) {
        if (numeratorAmountMinor < 0L) {
            throw new IllegalArgumentException(
                    "Vacation average numerator money must be non-negative"
            );
        }

        Objects.requireNonNull(
                denominatorDays,
                "Vacation average denominator is required"
        );

        if (denominatorDays.isZero()
                || denominatorDays.numerator().signum() <= 0) {
            throw new IllegalArgumentException(
                    "Vacation average denominator must be positive"
            );
        }

        BigInteger rateNumerator =
                BigInteger.valueOf(numeratorAmountMinor)
                        .multiply(denominatorDays.denominator());

        return new ExactMoneyPerDay(
                rateNumerator,
                denominatorDays.numerator()
        );
    }

    /**
     * Reduced exact minor-currency-units per calendar day.
     *
     * <p>For example, 12345/2 means 6172.5 minor units per day. Deliberately
     * no rounded-minor accessor exists at this layer.</p>
     */
    public record ExactMoneyPerDay(
            BigInteger numeratorMinor,
            BigInteger denominatorDays
    ) {
        public ExactMoneyPerDay {
            Objects.requireNonNull(
                    numeratorMinor,
                    "Exact daily money numerator is required"
            );
            Objects.requireNonNull(
                    denominatorDays,
                    "Exact daily money denominator is required"
            );

            if (numeratorMinor.signum() < 0) {
                throw new IllegalArgumentException(
                        "Exact daily money numerator must be non-negative"
                );
            }
            if (denominatorDays.signum() <= 0) {
                throw new IllegalArgumentException(
                        "Exact daily money denominator must be positive"
                );
            }

            if (numeratorMinor.signum() == 0) {
                numeratorMinor = BigInteger.ZERO;
                denominatorDays = BigInteger.ONE;
            } else {
                BigInteger gcd = numeratorMinor.gcd(denominatorDays);
                numeratorMinor = numeratorMinor.divide(gcd);
                denominatorDays = denominatorDays.divide(gcd);
            }
        }
    }
}
