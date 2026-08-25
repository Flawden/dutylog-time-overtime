package ru.daniil.shifts.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class PayrollSnapshotEarningManifestTest {

    private final PayrollSnapshot snapshot =
            mock(
                    PayrollSnapshot.class
            );

    @Test
    void completeManifestMayRepresentZeroEarningMonth() {
        PayrollSnapshotEarningManifest manifest =
                new PayrollSnapshotEarningManifest(
                        snapshot,
                        true,
                        0,
                        0L,
                        "0".repeat(
                                64
                        )
                );

        assertTrue(
                manifest.isComplete()
        );

        assertEquals(
                0,
                manifest.getLineCount()
        );

        assertEquals(
                0L,
                manifest.getAmountMinor()
        );
    }

    @Test
    void incompleteManifestIsExplicitState() {
        PayrollSnapshotEarningManifest manifest =
                new PayrollSnapshotEarningManifest(
                        snapshot,
                        false,
                        1,
                        100L,
                        "a".repeat(
                                64
                        )
                );

        assertFalse(
                manifest.isComplete()
        );
    }

    @Test
    void negativeCountOrAmountIsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new PayrollSnapshotEarningManifest(
                                snapshot,
                                true,
                                -1,
                                0L,
                                "0".repeat(
                                        64
                                )
                        )
        );

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new PayrollSnapshotEarningManifest(
                                snapshot,
                                true,
                                0,
                                -1L,
                                "0".repeat(
                                        64
                                )
                        )
        );
    }

    @Test
    void fingerprintMustBeLowercaseSha256() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new PayrollSnapshotEarningManifest(
                                snapshot,
                                true,
                                0,
                                0L,
                                "ABC"
                        )
        );
    }

    @Test
    void completeManifestRejectsUnclassifiedMoney() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new PayrollSnapshotEarningManifest(
                                snapshot,
                                true,
                                1,
                                100L,
                                1L,
                                "0".repeat(
                                        64
                                )
                        )
        );
    }

    @Test
    void incompleteManifestRetainsExplicitUnclassifiedMoney() {
        PayrollSnapshotEarningManifest manifest =
                new PayrollSnapshotEarningManifest(
                        snapshot,
                        false,
                        1,
                        100L,
                        250L,
                        "0".repeat(
                                64
                        )
                );

        assertEquals(
                250L,
                manifest.getUnclassifiedAmountMinor()
        );

        assertFalse(
                manifest.isComplete()
        );
    }

}
