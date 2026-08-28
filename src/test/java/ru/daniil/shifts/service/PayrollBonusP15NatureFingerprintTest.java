package ru.daniil.shifts.service;

import org.junit.jupiter.api.Test;
import ru.daniil.shifts.model.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class PayrollBonusP15NatureFingerprintTest {

    @Test
    void fingerprintIsStableForSameImmutableFactSet() {
        var facts = List.of(fact(0, PayrollBonusP15Nature.MONTHLY));
        assertEquals(
                PayrollBonusP15NatureFingerprint.calculate(1, facts),
                PayrollBonusP15NatureFingerprint.calculate(1, facts)
        );
    }

    @Test
    void fingerprintChangesWhenRewardNatureChanges() {
        var annual = List.of(factOneTime(0, PayrollBonusP15Nature.ANNUAL_RESULT));
        var service = List.of(factOneTime(0, PayrollBonusP15Nature.SERVICE_LENGTH));
        assertNotEquals(
                PayrollBonusP15NatureFingerprint.calculate(1, annual),
                PayrollBonusP15NatureFingerprint.calculate(1, service)
        );
    }

    @Test
    void rejectsNonContiguousOrderOrFactsBeyondAverageCount() {
        assertThrows(IllegalArgumentException.class,
                () -> PayrollBonusP15NatureFingerprint.calculate(1, List.of(fact(1, PayrollBonusP15Nature.MONTHLY))));
        assertThrows(IllegalArgumentException.class,
                () -> PayrollBonusP15NatureFingerprint.calculate(0, List.of(fact(0, PayrollBonusP15Nature.MONTHLY))));
    }

    private static PayrollSnapshotBonusP15NatureFact fact(int index, PayrollBonusP15Nature nature) {
        return new PayrollSnapshotBonusP15NatureFact(
                mock(PayrollSnapshot.class), index, 10L, 20L, 30L, 40L,
                PayrollEarningKind.MONTHLY_BONUS, nature
        );
    }

    private static PayrollSnapshotBonusP15NatureFact factOneTime(int index, PayrollBonusP15Nature nature) {
        return new PayrollSnapshotBonusP15NatureFact(
                mock(PayrollSnapshot.class), index, 10L, 20L, 30L, 40L,
                PayrollEarningKind.ONE_TIME_BONUS, nature
        );
    }
}
