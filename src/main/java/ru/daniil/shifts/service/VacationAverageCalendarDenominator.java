package ru.daniil.shifts.service;

import java.math.BigInteger;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Exact calendar-day denominator kernel for vacation average earnings under
 * paragraph 10 of Government Resolution No. 540 of 24 April 2025.
 *
 * <p>This class owns only the statutory calendar denominator arithmetic.</p>
 *
 * <p>It deliberately does not decide:</p>
 * <ul>
 *     <li>which absence intervals are legally excluded;</li>
 *     <li>employment coverage inside a reference month;</li>
 *     <li>which earnings enter the numerator;</li>
 *     <li>premium allocation;</li>
 *     <li>reference-period fallback under paragraphs 6-8;</li>
 *     <li>average daily money or vacation-pay money.</li>
 * </ul>
 *
 * <p>The statutory monthly constant 29.3 is represented exactly as 293/10.
 * Partial-month contributions remain exact rational numbers, so no
 * intermediate rounding policy is invented here.</p>
 */
public final class VacationAverageCalendarDenominator {

    private static final ExactDays FULL_MONTH =
            ExactDays.of(
                    293,
                    10
            );

    private VacationAverageCalendarDenominator() {
    }

    /**
     * Calculates the primary twelve-calendar-month denominator immediately
     * preceding the event month.
     *
     * <p>Each {@link MonthFact#countableCalendarDays()} is an already resolved
     * legal/reference fact supplied by a higher layer. It is not inferred
     * here from schedules, absences, payroll money or display text.</p>
     */
    public static Result primary(
            LocalDate eventDate,
            YearMonth eventMonth,
            List<MonthFact> monthFacts
    ) {
        Objects.requireNonNull(eventMonth, "Vacation average denominator requires event month");
        if (!YearMonth.from(Objects.requireNonNull(
                eventDate,
                "Vacation average denominator requires event date"
        )).equals(eventMonth)) {
            throw new IllegalArgumentException(
                    "Average earnings event date must belong to event month"
            );
        }
        return calculate(
                eventDate,
                AverageEarningsReferenceWindow.primary(eventMonth),
                monthFacts
        );
    }

    public static Result calculate(
            LocalDate eventDate,
            AverageEarningsReferenceWindow referenceWindow,
            List<MonthFact> monthFacts
    ) {
        Objects.requireNonNull(
                eventDate,
                "Vacation average denominator requires event date"
        );
        Objects.requireNonNull(
                referenceWindow,
                "Vacation average denominator requires reference window"
        ).requireEventDate(eventDate);
        Objects.requireNonNull(
                monthFacts,
                "Vacation average denominator requires month facts"
        );

        AverageEarningsLegalPolicy.LegalRegime regime =
                AverageEarningsLegalPolicy
                        .requireRegime(
                                eventDate
                        );

        if (monthFacts.size() != 12) {
            throw new IllegalArgumentException(
                    "Vacation average denominator requires exactly 12 months"
            );
        }

        YearMonth eventMonth = referenceWindow.eventMonth();
        YearMonth referenceFrom = referenceWindow.referenceFrom();
        YearMonth referenceTo = referenceWindow.referenceTo();

        List<MonthContribution> contributions =
                new ArrayList<>(
                        12
                );

        ExactDays total =
                ExactDays.zero();

        for (int index = 0;
                index < 12;
                index++) {

            YearMonth expectedMonth =
                    referenceFrom.plusMonths(
                            index
                    );

            MonthFact fact =
                    Objects.requireNonNull(
                            monthFacts.get(
                                    index
                            ),
                            "Vacation average denominator month fact is required"
                    );

            if (!expectedMonth.equals(
                    fact.month()
            )) {
                throw new IllegalArgumentException(
                        "Primary vacation average denominator month sequence mismatch: expected "
                                + expectedMonth
                                + ", got "
                                + fact.month()
                );
            }

            int calendarDaysInMonth =
                    fact.month()
                            .lengthOfMonth();

            int countableCalendarDays =
                    fact.countableCalendarDays();

            boolean fullMonth =
                    countableCalendarDays
                            == calendarDaysInMonth;

            ExactDays contribution =
                    fullMonth
                            ? FULL_MONTH
                            : ExactDays.of(
                                    BigInteger
                                            .valueOf(
                                                    293L
                                            )
                                            .multiply(
                                                    BigInteger.valueOf(
                                                            countableCalendarDays
                                                    )
                                            ),
                                    BigInteger
                                            .valueOf(
                                                    10L
                                            )
                                            .multiply(
                                                    BigInteger.valueOf(
                                                            calendarDaysInMonth
                                                    )
                                            )
                            );

            contributions.add(
                    new MonthContribution(
                            fact.month(),
                            calendarDaysInMonth,
                            countableCalendarDays,
                            fullMonth,
                            contribution
                    )
            );

            total =
                    total.plus(
                            contribution
                    );
        }

        if (total.isZero()) {
            throw new IllegalStateException(
                    "Primary vacation average denominator has no countable calendar time; paragraphs 6-8 fallback resolution is required"
            );
        }

        return new Result(
                regime,
                eventDate,
                eventMonth,
                referenceFrom,
                referenceTo,
                contributions,
                total
        );
    }

    /**
     * Calendar facts already resolved by a higher factual/legal layer.
     *
     * <p>For a legally full calendar month this value equals the actual number
     * of calendar days in that month. For a partial month it is the number of
     * calendar days attributable to countable worked time under paragraph 10.
     * Zero is permitted because a reference month may contribute no countable
     * calendar time.</p>
     */
    public record MonthFact(
            YearMonth month,
            int countableCalendarDays
    ) {
        public MonthFact {
            Objects.requireNonNull(
                    month,
                    "Vacation average denominator month is required"
            );

            int calendarDaysInMonth =
                    month.lengthOfMonth();

            if (countableCalendarDays < 0
                    || countableCalendarDays > calendarDaysInMonth) {
                throw new IllegalArgumentException(
                        "Countable calendar days must be between 0 and "
                                + calendarDaysInMonth
                                + " for "
                                + month
                );
            }
        }
    }

    public record MonthContribution(
            YearMonth month,
            int calendarDaysInMonth,
            int countableCalendarDays,
            boolean fullMonth,
            ExactDays denominatorDays
    ) {
        public MonthContribution {
            Objects.requireNonNull(
                    month,
                    "Month contribution month is required"
            );

            Objects.requireNonNull(
                    denominatorDays,
                    "Month denominator contribution is required"
            );

            if (calendarDaysInMonth
                    != month.lengthOfMonth()) {
                throw new IllegalArgumentException(
                        "Month contribution calendar length mismatch"
                );
            }

            if (countableCalendarDays < 0
                    || countableCalendarDays > calendarDaysInMonth) {
                throw new IllegalArgumentException(
                        "Month contribution countable days are invalid"
                );
            }

            if (fullMonth
                    != (countableCalendarDays
                    == calendarDaysInMonth)) {
                throw new IllegalArgumentException(
                        "Month contribution full-month marker is inconsistent"
                );
            }
        }
    }

    public record Result(
            AverageEarningsLegalPolicy.LegalRegime regime,
            LocalDate eventDate,
            YearMonth eventMonth,
            YearMonth referenceFrom,
            YearMonth referenceTo,
            List<MonthContribution> months,
            ExactDays denominatorDays
    ) {
        public Result {
            Objects.requireNonNull(
                    regime,
                    "Legal regime is required"
            );

            Objects.requireNonNull(
                    eventDate,
                    "Event date is required"
            );

            Objects.requireNonNull(
                    eventMonth,
                    "Event month is required"
            );

            Objects.requireNonNull(
                    referenceFrom,
                    "Reference from is required"
            );

            Objects.requireNonNull(
                    referenceTo,
                    "Reference to is required"
            );

            months = List.copyOf(
                    Objects.requireNonNull(
                            months,
                            "Month contributions are required"
                    )
            );

            Objects.requireNonNull(
                    denominatorDays,
                    "Calendar denominator is required"
            );

            if (!eventMonth.equals(YearMonth.from(eventDate))) {
                throw new IllegalArgumentException(
                        "Vacation denominator event month does not match legal event date"
                );
            }
            new AverageEarningsReferenceWindow(eventMonth, referenceFrom, referenceTo);

            if (months.size() != 12) {
                throw new IllegalArgumentException(
                        "Primary denominator result requires 12 month contributions"
                );
            }

            if (denominatorDays.isZero()) {
                throw new IllegalArgumentException(
                        "Primary denominator result must be positive"
                );
            }
        }
    }

    /**
     * Reduced exact rational calendar-day quantity.
     *
     * <p>No decimal conversion or rounding method is intentionally exposed at
     * this layer. A later money layer must choose the legally appropriate
     * final rounding boundary explicitly.</p>
     */
    public record ExactDays(
            BigInteger numerator,
            BigInteger denominator
    ) {
        public ExactDays {
            Objects.requireNonNull(
                    numerator,
                    "Exact-day numerator is required"
            );

            Objects.requireNonNull(
                    denominator,
                    "Exact-day denominator is required"
            );

            if (denominator.signum() == 0) {
                throw new IllegalArgumentException(
                        "Exact-day denominator must not be zero"
                );
            }

            if (denominator.signum() < 0) {
                numerator =
                        numerator.negate();

                denominator =
                        denominator.negate();
            }

            if (numerator.signum() == 0) {
                numerator =
                        BigInteger.ZERO;

                denominator =
                        BigInteger.ONE;
            } else {
                BigInteger gcd =
                        numerator
                                .abs()
                                .gcd(
                                        denominator
                                );

                numerator =
                        numerator.divide(
                                gcd
                        );

                denominator =
                        denominator.divide(
                                gcd
                        );
            }
        }

        public static ExactDays zero() {
            return new ExactDays(
                    BigInteger.ZERO,
                    BigInteger.ONE
            );
        }

        public static ExactDays of(
                long numerator,
                long denominator
        ) {
            return of(
                    BigInteger.valueOf(
                            numerator
                    ),
                    BigInteger.valueOf(
                            denominator
                    )
            );
        }

        public static ExactDays of(
                BigInteger numerator,
                BigInteger denominator
        ) {
            return new ExactDays(
                    numerator,
                    denominator
            );
        }

        public ExactDays plus(
                ExactDays other
        ) {
            Objects.requireNonNull(
                    other,
                    "Exact-day addend is required"
            );

            return new ExactDays(
                    numerator
                            .multiply(
                                    other.denominator
                            )
                            .add(
                                    other.numerator
                                            .multiply(
                                                    denominator
                                            )
                            ),
                    denominator
                            .multiply(
                                    other.denominator
                            )
            );
        }

        public boolean isZero() {
            return numerator.signum()
                    == 0;
        }
    }
}
