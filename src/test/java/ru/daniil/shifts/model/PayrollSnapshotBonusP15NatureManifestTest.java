package ru.daniil.shifts.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class PayrollSnapshotBonusP15NatureManifestTest {

    @Test
    void completeMeansEveryFrozenAverageFactHasExplicitNature() {
        var manifest = new PayrollSnapshotBonusP15NatureManifest(
                mock(PayrollSnapshot.class), 2, 2, "a".repeat(64)
        );
        assertTrue(manifest.isComplete());
        assertEquals(2, manifest.getAverageFactCount());
        assertEquals(2, manifest.getNatureFactCount());
    }

    @Test
    void missingNatureStaysIncompleteAndInvalidCountsFailClosed() {
        var manifest = new PayrollSnapshotBonusP15NatureManifest(
                mock(PayrollSnapshot.class), 2, 1, "b".repeat(64)
        );
        assertFalse(manifest.isComplete());
        assertThrows(IllegalArgumentException.class, () -> new PayrollSnapshotBonusP15NatureManifest(
                mock(PayrollSnapshot.class), 1, 2, "c".repeat(64)
        ));
    }
}
