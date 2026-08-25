package ru.daniil.shifts.model;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class PayrollSnapshotEarningLineTest {

    private final PayrollSnapshot snapshot =
            mock(
                    PayrollSnapshot.class
            );

    @Test
    void storesMachineOwnedKindPhaseAndAmount() {
        PayrollSnapshotEarningLine line =
                new PayrollSnapshotEarningLine(
                        snapshot,
                        0,
                        PayrollEarningKind.MONTHLY_BONUS,
                        2_518_797L,
                        null,
                        LocalDate.of(
                                2026,
                                7,
                                1
                        ),
                        LocalDate.of(
                                2026,
                                7,
                                31
                        ),
                        null,
                        null
                );

        assertEquals(
                "MONTHLY_BONUS",
                line.getEarningKind()
        );

        assertEquals(
                "PERFORMANCE_BONUS",
                line.getEarningPhase()
        );

        assertEquals(
                2_518_797L,
                line.getAmountMinor()
        );
    }

    @Test
    void qualifiedQuantityPreservesUnitWithoutConversion() {
        PayrollSnapshotEarningLine line =
                new PayrollSnapshotEarningLine(
                        snapshot,
                        0,
                        PayrollEarningKind.VACATION_PAY,
                        5_160_988L,
                        PayrollQualifiedQuantity.calendarDays(
                                14
                        ),
                        null,
                        null,
                        LocalDate.of(
                                2026,
                                6,
                                1
                        ),
                        LocalDate.of(
                                2026,
                                6,
                                15
                        )
                );

        assertEquals(
                14L,
                line.getQualifiedQuantityValue()
        );

        assertEquals(
                "CALENDAR_DAYS",
                line.getQualifiedQuantityUnit()
        );
    }

    @Test
    void qualifiedQuantityMayBeAbsent() {
        PayrollSnapshotEarningLine line =
                new PayrollSnapshotEarningLine(
                        snapshot,
                        0,
                        PayrollEarningKind.BASE_PAY,
                        6_054_800L,
                        null,
                        null,
                        null,
                        null,
                        null
                );

        assertNull(
                line.getQualifiedQuantityValue()
        );

        assertNull(
                line.getQualifiedQuantityUnit()
        );
    }

    @Test
    void earningAndCoveragePeriodsRequireCompleteOrderedPairs() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new PayrollSnapshotEarningLine(
                                snapshot,
                                0,
                                PayrollEarningKind.MONTHLY_BONUS,
                                1L,
                                null,
                                LocalDate.of(
                                        2026,
                                        1,
                                        1
                                ),
                                null,
                                null,
                                null
                        )
        );

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new PayrollSnapshotEarningLine(
                                snapshot,
                                0,
                                PayrollEarningKind.VACATION_PAY,
                                1L,
                                null,
                                null,
                                null,
                                LocalDate.of(
                                        2026,
                                        6,
                                        15
                                ),
                                LocalDate.of(
                                        2026,
                                        6,
                                        1
                                )
                        )
        );
    }

    @Test
    void displayNameCannotBecomeHistoricalFinancialSemantics() {
        assertFalse(
                Arrays.stream(
                                PayrollSnapshotEarningLine.class
                                        .getDeclaredFields()
                        )
                        .map(
                                Field::getName
                        )
                        .anyMatch(name ->
                                name.toLowerCase()
                                        .contains(
                                                "display"
                                        )
                                || name.toLowerCase()
                                        .contains(
                                                "title"
                                        )
                        )
        );
    }

    @Test
    void negativeIdentityOrMoneyIsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new PayrollSnapshotEarningLine(
                                snapshot,
                                -1,
                                PayrollEarningKind.BASE_PAY,
                                1L,
                                null,
                                null,
                                null,
                                null,
                                null
                        )
        );

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new PayrollSnapshotEarningLine(
                                snapshot,
                                0,
                                PayrollEarningKind.BASE_PAY,
                                -1L,
                                null,
                                null,
                                null,
                                null,
                                null
                        )
        );
    }
}
