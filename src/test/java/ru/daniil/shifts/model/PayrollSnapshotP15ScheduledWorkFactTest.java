package ru.daniil.shifts.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class PayrollSnapshotP15ScheduledWorkFactTest {

    private final PayrollSnapshot snapshot = mock(PayrollSnapshot.class);

    @Test
    void validFactKeepsOutsidePlanWorkSeparateFromScheduledWork() {
        PayrollSnapshotP15ScheduledWorkFact fact = new PayrollSnapshotP15ScheduledWorkFact(
                snapshot,
                0,
                LocalDate.of(2026, 8, 18),
                42L,
                LocalDate.of(2026, 1, 1),
                WorkTimeAccountingMode.SUMMARIZED,
                PayrollSnapshotP15ScheduledWorkSourceKind.EXPLICIT_ACTUAL,
                480,
                600,
                480,
                480,
                480,
                0,
                120,
                true,
                "10",
                "20,21",
                "a".repeat(64)
        );

        assertEquals(480, fact.getScheduleMinutes());
        assertEquals(480, fact.getPlannedAndWorkedMinutes());
        assertEquals(120, fact.getWorkedOutsidePlanMinutes());
        assertTrue(fact.isSourceIdentityExact());
    }

    @Test
    void factRejectsScheduleRelationThatDoesNotReconcile() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PayrollSnapshotP15ScheduledWorkFact(
                        snapshot,
                        0,
                        LocalDate.of(2026, 8, 18),
                        42L,
                        LocalDate.of(2026, 1, 1),
                        WorkTimeAccountingMode.DAILY,
                        PayrollSnapshotP15ScheduledWorkSourceKind.PLAN_DERIVED,
                        480,
                        360,
                        360,
                        480,
                        360,
                        100,
                        0,
                        true,
                        "10",
                        "",
                        "b".repeat(64)
                )
        );
    }

    @Test
    void factRejectsNonCanonicalMutableSourceIdentityAndFingerprint() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PayrollSnapshotP15ScheduledWorkFact(
                        snapshot,
                        0,
                        LocalDate.of(2026, 8, 18),
                        42L,
                        LocalDate.of(2026, 1, 1),
                        WorkTimeAccountingMode.DAILY,
                        PayrollSnapshotP15ScheduledWorkSourceKind.PLAN_DERIVED,
                        0, 0, 0, 0, 0, 0, 0,
                        true,
                        "10,02",
                        "",
                        "not-a-sha"
                )
        );
    }

    @Test
    void manifestCompletenessRequiresEveryCandidateAndExactIdentity() {
        PayrollSnapshotP15WorkTimeManifest complete =
                new PayrollSnapshotP15WorkTimeManifest(
                        snapshot,
                        2,
                        2,
                        2,
                        "c".repeat(64)
                );

        PayrollSnapshotP15WorkTimeManifest incomplete =
                new PayrollSnapshotP15WorkTimeManifest(
                        snapshot,
                        2,
                        2,
                        1,
                        "d".repeat(64)
                );

        assertTrue(complete.isComplete());
        assertFalse(incomplete.isComplete());
        assertThrows(
                IllegalArgumentException.class,
                () -> new PayrollSnapshotP15WorkTimeManifest(
                        snapshot,
                        1,
                        2,
                        2,
                        "e".repeat(64)
                )
        );
    }
}
