package ru.daniil.shifts.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class PayrollSnapshotBonusAverageEarningsFactTest {

    private final PayrollSnapshot snapshot = mock(PayrollSnapshot.class);

    @Test
    void preservesExactSourceAndParagraph15FactsIncludingUnknownFlags() {
        PayrollSnapshotBonusAverageEarningsFact fact =
                new PayrollSnapshotBonusAverageEarningsFact(
                        snapshot,
                        0,
                        71L,
                        72L,
                        9L,
                        PayrollEarningKind.MONTHLY_BONUS,
                        LocalDate.of(2026, 3, 1),
                        LocalDate.of(2026, 3, 31),
                        1_234_500L,
                        "rub",
                        " monthly:kpi ",
                        LocalDate.of(2026, 3, 1),
                        LocalDate.of(2026, 3, 31),
                        null,
                        Boolean.TRUE,
                        null
                );

        assertEquals(0, fact.getFactIndex());
        assertEquals(71L, fact.getBonusSourceFactId());
        assertEquals(72L, fact.getBonusAverageFactId());
        assertEquals(9L, fact.getComponentId());
        assertEquals(PayrollEarningKind.MONTHLY_BONUS, fact.getEarningKind());
        assertEquals(1_234_500L, fact.getAmountMinor());
        assertEquals("RUB", fact.getCurrencyCode());
        assertEquals("MONTHLY:KPI", fact.getIndicatorKey());
        assertNull(fact.getAnnualResult());
        assertEquals(Boolean.TRUE, fact.getAccruedForActualWorkTime());
        assertNull(fact.getProratedForPartialAwardPeriod());
    }

    @Test
    void annualResultRequiresOneTimeBonusAndCompleteCalendarYear() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PayrollSnapshotBonusAverageEarningsFact(
                        snapshot,
                        0,
                        1L,
                        2L,
                        3L,
                        PayrollEarningKind.MONTHLY_BONUS,
                        LocalDate.of(2026, 2, 1),
                        LocalDate.of(2026, 2, 28),
                        100L,
                        "RUB",
                        "ANNUAL",
                        LocalDate.of(2025, 1, 1),
                        LocalDate.of(2025, 12, 31),
                        true,
                        null,
                        null
                )
        );

        PayrollSnapshotBonusAverageEarningsFact valid =
                new PayrollSnapshotBonusAverageEarningsFact(
                        snapshot,
                        0,
                        1L,
                        2L,
                        3L,
                        PayrollEarningKind.ONE_TIME_BONUS,
                        LocalDate.of(2026, 2, 1),
                        LocalDate.of(2026, 2, 28),
                        100L,
                        "RUB",
                        "ANNUAL",
                        LocalDate.of(2025, 1, 1),
                        LocalDate.of(2025, 12, 31),
                        true,
                        false,
                        false
                );

        assertTrue(valid.getAnnualResult());
    }

    @Test
    void rejectsCrossMonthSourcePeriodAndNonBonusKind() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PayrollSnapshotBonusAverageEarningsFact(
                        snapshot,
                        0,
                        1L,
                        2L,
                        3L,
                        PayrollEarningKind.ONE_TIME_BONUS,
                        LocalDate.of(2026, 2, 28),
                        LocalDate.of(2026, 3, 1),
                        100L,
                        "RUB",
                        "KPI",
                        LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 1, 31),
                        false,
                        false,
                        false
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new PayrollSnapshotBonusAverageEarningsFact(
                        snapshot,
                        0,
                        1L,
                        2L,
                        3L,
                        PayrollEarningKind.BASE_PAY,
                        LocalDate.of(2026, 2, 1),
                        LocalDate.of(2026, 2, 28),
                        100L,
                        "RUB",
                        "KPI",
                        LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 1, 31),
                        false,
                        false,
                        false
                )
        );
    }
}
