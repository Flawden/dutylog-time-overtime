package ru.daniil.shifts.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class PayrollSnapshotBonusAverageEarningsManifestTest {

    private final PayrollSnapshot snapshot = mock(PayrollSnapshot.class);

    @Test
    void completeIsDerivedOnlyFromExplicitFactCounts() {
        PayrollSnapshotBonusAverageEarningsManifest complete =
                new PayrollSnapshotBonusAverageEarningsManifest(
                        snapshot,
                        2,
                        2,
                        "0".repeat(64)
                );

        PayrollSnapshotBonusAverageEarningsManifest incomplete =
                new PayrollSnapshotBonusAverageEarningsManifest(
                        snapshot,
                        2,
                        1,
                        "1".repeat(64)
                );

        assertTrue(complete.isComplete());
        assertFalse(incomplete.isComplete());
        assertEquals(2, incomplete.getSourceFactCount());
        assertEquals(1, incomplete.getFactCount());
    }

    @Test
    void rejectsImpossibleCountsAndNonShaFingerprint() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PayrollSnapshotBonusAverageEarningsManifest(
                        snapshot,
                        1,
                        2,
                        "0".repeat(64)
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new PayrollSnapshotBonusAverageEarningsManifest(
                        snapshot,
                        0,
                        0,
                        "not-a-sha"
                )
        );
    }
}
