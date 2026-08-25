package ru.daniil.shifts.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PayrollQualifiedQuantityTest {

    @Test
    void minutesFactoryPreservesExactValueAndUnit() {
        PayrollQualifiedQuantity quantity =
                PayrollQualifiedQuantity.minutes(
                        480
                );

        assertEquals(
                480L,
                quantity.value()
        );

        assertEquals(
                PayrollQuantityUnit.MINUTES,
                quantity.unit()
        );
    }

    @Test
    void calendarDaysFactoryPreservesExactValueAndUnit() {
        PayrollQualifiedQuantity quantity =
                PayrollQualifiedQuantity.calendarDays(
                        14
                );

        assertEquals(
                14L,
                quantity.value()
        );

        assertEquals(
                PayrollQuantityUnit.CALENDAR_DAYS,
                quantity.unit()
        );
    }

    @Test
    void negativeQuantityFailsClosed() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new PayrollQualifiedQuantity(
                                -1,
                                PayrollQuantityUnit.MINUTES
                        )
        );
    }

    @Test
    void missingUnitFailsClosed() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new PayrollQualifiedQuantity(
                                1,
                                null
                        )
        );
    }

    @Test
    void aggregationRequiresSameUnitAndUsesExactArithmetic() {
        assertEquals(
                PayrollQualifiedQuantity.minutes(
                        540
                ),
                PayrollQualifiedQuantity.minutes(
                                480
                        )
                        .plus(
                                PayrollQualifiedQuantity.minutes(
                                        60
                                )
                        )
        );

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        PayrollQualifiedQuantity.minutes(
                                        60
                                )
                                .plus(
                                        PayrollQualifiedQuantity.calendarDays(
                                                1
                                        )
                                )
        );

        assertThrows(
                ArithmeticException.class,
                () ->
                        new PayrollQualifiedQuantity(
                                Long.MAX_VALUE,
                                PayrollQuantityUnit.MINUTES
                        )
                                .plus(
                                        PayrollQualifiedQuantity.minutes(
                                                1
                                        )
                                )
        );
    }
}
