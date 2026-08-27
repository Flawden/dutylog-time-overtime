package ru.daniil.shifts.service;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static ru.daniil.shifts.service.VacationAverageCalendarDenominator.*;

class VacationAverageCalendarDenominatorTest {

    @Test
    void twelveFullMonthsAreExactly351Point6Days() {
        YearMonth eventMonth =
                YearMonth.of(
                        2026,
                        8
                );

        Result result =
                VacationAverageCalendarDenominator
                        .primary(
                                eventMonth.atDay(
                                        14
                                ),
                                eventMonth,
                                fullMonths(
                                        eventMonth
                                )
                        );

        assertEquals(
                YearMonth.of(
                        2025,
                        8
                ),
                result.referenceFrom()
        );

        assertEquals(
                YearMonth.of(
                        2026,
                        7
                ),
                result.referenceTo()
        );

        assertEquals(
                12,
                result.months().size()
        );

        assertTrue(
                result
                        .months()
                        .stream()
                        .allMatch(
                                MonthContribution::fullMonth
                        )
        );

        assertExact(
                result.denominatorDays(),
                1758,
                5
        );
    }

    @Test
    void partialJanuaryUsesExactRationalWithoutIntermediateRounding() {
        YearMonth eventMonth =
                YearMonth.of(
                        2026,
                        8
                );

        List<MonthFact> months =
                withCountableDays(
                        fullMonths(
                                eventMonth
                        ),
                        YearMonth.of(
                                2026,
                                1
                        ),
                        20
                );

        Result result =
                VacationAverageCalendarDenominator
                        .primary(
                                eventMonth.atDay(
                                        14
                                ),
                                eventMonth,
                                months
                        );

        MonthContribution january =
                contribution(
                        result,
                        YearMonth.of(
                                2026,
                                1
                        )
                );

        assertFalse(
                january.fullMonth()
        );

        assertEquals(
                31,
                january.calendarDaysInMonth()
        );

        assertEquals(
                20,
                january.countableCalendarDays()
        );

        /*
         * 29.3 / 31 * 20
         * = 293/10 * 20/31
         * = 586/31 exactly.
         */
        assertExact(
                january.denominatorDays(),
                586,
                31
        );

        /*
         * 11 * 29.3 + 586/31
         * = 105773/310 exactly.
         */
        assertExact(
                result.denominatorDays(),
                105773,
                310
        );
    }

    @Test
    void partialFebruaryUsesActualCommonAndLeapMonthLengths() {
        YearMonth commonEvent =
                YearMonth.of(
                        2026,
                        8
                );

        Result common =
                VacationAverageCalendarDenominator
                        .primary(
                                commonEvent.atDay(
                                        14
                                ),
                                commonEvent,
                                withCountableDays(
                                        fullMonths(
                                                commonEvent
                                        ),
                                        YearMonth.of(
                                                2026,
                                                2
                                        ),
                                        14
                                )
                        );

        MonthContribution commonFebruary =
                contribution(
                        common,
                        YearMonth.of(
                                2026,
                                2
                        )
                );

        assertEquals(
                28,
                commonFebruary.calendarDaysInMonth()
        );

        assertExact(
                commonFebruary.denominatorDays(),
                293,
                20
        );

        YearMonth leapEvent =
                YearMonth.of(
                        2028,
                        8
                );

        Result leap =
                VacationAverageCalendarDenominator
                        .primary(
                                leapEvent.atDay(
                                        14
                                ),
                                leapEvent,
                                withCountableDays(
                                        fullMonths(
                                                leapEvent
                                        ),
                                        YearMonth.of(
                                                2028,
                                                2
                                        ),
                                        14
                                )
                        );

        MonthContribution leapFebruary =
                contribution(
                        leap,
                        YearMonth.of(
                                2028,
                                2
                        )
                );

        assertEquals(
                29,
                leapFebruary.calendarDaysInMonth()
        );

        assertExact(
                leapFebruary.denominatorDays(),
                2051,
                145
        );
    }

    @Test
    void zeroCountableMonthContributesExactlyZero() {
        YearMonth eventMonth =
                YearMonth.of(
                        2026,
                        8
                );

        YearMonth emptyMonth =
                YearMonth.of(
                        2026,
                        1
                );

        Result result =
                VacationAverageCalendarDenominator
                        .primary(
                                eventMonth.atDay(
                                        14
                                ),
                                eventMonth,
                                withCountableDays(
                                        fullMonths(
                                                eventMonth
                                        ),
                                        emptyMonth,
                                        0
                                )
                        );

        MonthContribution empty =
                contribution(
                        result,
                        emptyMonth
                );

        assertFalse(
                empty.fullMonth()
        );

        assertExact(
                empty.denominatorDays(),
                0,
                1
        );

        assertExact(
                result.denominatorDays(),
                3223,
                10
        );
    }

    @Test
    void primaryWindowRequiresExactTwelveConsecutiveMonthsAndMatchingEventMonth() {
        YearMonth eventMonth =
                YearMonth.of(
                        2026,
                        8
                );

        List<MonthFact> eleven =
                new ArrayList<>(
                        fullMonths(
                                eventMonth
                        )
                );

        eleven.remove(
                eleven.size() - 1
        );

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        VacationAverageCalendarDenominator
                                .primary(
                                        eventMonth.atDay(
                                                14
                                        ),
                                        eventMonth,
                                        eleven
                                )
        );

        List<MonthFact> wrongSequence =
                new ArrayList<>(
                        fullMonths(
                                eventMonth
                        )
                );

        wrongSequence.set(
                4,
                new MonthFact(
                        YearMonth.of(
                                2030,
                                1
                        ),
                        31
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        VacationAverageCalendarDenominator
                                .primary(
                                        eventMonth.atDay(
                                                14
                                        ),
                                        eventMonth,
                                        wrongSequence
                                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        VacationAverageCalendarDenominator
                                .primary(
                                        LocalDate.of(
                                                2026,
                                                7,
                                                31
                                        ),
                                        eventMonth,
                                        fullMonths(
                                                eventMonth
                                        )
                                )
        );
    }

    @Test
    void countableDaysOutsideCalendarBoundsFailClosed() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new MonthFact(
                                YearMonth.of(
                                        2026,
                                        1
                                ),
                                -1
                        )
        );

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new MonthFact(
                                YearMonth.of(
                                        2026,
                                        2
                                ),
                                29
                        )
        );

        assertDoesNotThrow(
                () ->
                        new MonthFact(
                                YearMonth.of(
                                        2028,
                                        2
                                ),
                                29
                        )
        );
    }

    @Test
    void allZeroPrimaryPeriodRequiresFallbackInsteadOfZeroDenominator() {
        YearMonth eventMonth =
                YearMonth.of(
                        2026,
                        8
                );

        List<MonthFact> zeroMonths =
                new ArrayList<>();

        YearMonth from =
                eventMonth.minusMonths(
                        12
                );

        for (int offset = 0;
                offset < 12;
                offset++) {

            zeroMonths.add(
                    new MonthFact(
                            from.plusMonths(
                                    offset
                            ),
                            0
                    )
            );
        }

        IllegalStateException error =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                VacationAverageCalendarDenominator
                                        .primary(
                                                eventMonth.atDay(
                                                        14
                                                ),
                                                eventMonth,
                                                zeroMonths
                                        )
                );

        assertTrue(
                error
                        .getMessage()
                        .contains(
                                "fallback"
                        )
        );
    }

    private static List<MonthFact> fullMonths(
            YearMonth eventMonth
    ) {
        List<MonthFact> result =
                new ArrayList<>();

        YearMonth from =
                eventMonth.minusMonths(
                        12
                );

        for (int offset = 0;
                offset < 12;
                offset++) {

            YearMonth month =
                    from.plusMonths(
                            offset
                    );

            result.add(
                    new MonthFact(
                            month,
                            month.lengthOfMonth()
                    )
            );
        }

        return result;
    }

    private static List<MonthFact> withCountableDays(
            List<MonthFact> source,
            YearMonth targetMonth,
            int countableCalendarDays
    ) {
        List<MonthFact> result =
                new ArrayList<>(
                        source
                );

        for (int index = 0;
                index < result.size();
                index++) {

            if (result
                    .get(index)
                    .month()
                    .equals(
                            targetMonth
                    )) {

                result.set(
                        index,
                        new MonthFact(
                                targetMonth,
                                countableCalendarDays
                        )
                );

                return result;
            }
        }

        throw new AssertionError(
                "Target month is outside test reference window: "
                        + targetMonth
        );
    }

    private static MonthContribution contribution(
            Result result,
            YearMonth month
    ) {
        return result
                .months()
                .stream()
                .filter(
                        item ->
                                item.month()
                                        .equals(
                                                month
                                        )
                )
                .findFirst()
                .orElseThrow();
    }

    private static void assertExact(
            ExactDays actual,
            long numerator,
            long denominator
    ) {
        assertEquals(
                BigInteger.valueOf(
                        numerator
                ),
                actual.numerator()
        );

        assertEquals(
                BigInteger.valueOf(
                        denominator
                ),
                actual.denominator()
        );
    }
}
