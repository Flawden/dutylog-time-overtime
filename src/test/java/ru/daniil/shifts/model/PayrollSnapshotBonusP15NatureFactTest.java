package ru.daniil.shifts.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class PayrollSnapshotBonusP15NatureFactTest {

    @Test
    void acceptsExplicitMonthlyNatureWithScalarHistoricalIdentities() {
        var fact = new PayrollSnapshotBonusP15NatureFact(
                mock(PayrollSnapshot.class), 0, 10L, 20L, 30L, 40L,
                PayrollEarningKind.MONTHLY_BONUS, PayrollBonusP15Nature.MONTHLY
        );
        assertEquals(10L, fact.getBonusSourceFactId());
        assertEquals(30L, fact.getBonusNatureFactId());
        assertEquals(PayrollBonusP15Nature.MONTHLY, fact.getP15Nature());
    }

    @Test
    void rejectsMonthlyNatureForOneTimeBonus() {
        assertThrows(IllegalArgumentException.class, () -> new PayrollSnapshotBonusP15NatureFact(
                mock(PayrollSnapshot.class), 0, 10L, 20L, 30L, 40L,
                PayrollEarningKind.ONE_TIME_BONUS, PayrollBonusP15Nature.MONTHLY
        ));
    }

    @Test
    void rejectsNonMonthlyNatureForMonthlyBonus() {
        assertThrows(IllegalArgumentException.class, () -> new PayrollSnapshotBonusP15NatureFact(
                mock(PayrollSnapshot.class), 0, 10L, 20L, 30L, 40L,
                PayrollEarningKind.MONTHLY_BONUS, PayrollBonusP15Nature.SERVICE_LENGTH
        ));
    }
}
