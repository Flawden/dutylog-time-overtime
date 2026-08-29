package ru.daniil.shifts.model;

import jakarta.persistence.*;

import java.util.Objects;

/**
 * Completeness/integrity declaration for one immutable Payroll revision's
 * paragraph-15 scheduled-work FACT freeze.
 *
 * <p>Absence of this manifest means legacy authority is unknown. complete=false
 * is an explicit fail-closed state; downstream P15 resolution must not consume
 * the partial facts as a complete reference-period month.</p>
 */
@Entity
@Table(name = "payroll_snapshot_p15_work_time_manifests")
public class PayrollSnapshotP15WorkTimeManifest {

    @Id
    @Column(name = "snapshot_id")
    private Long snapshotId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "snapshot_id", nullable = false)
    private PayrollSnapshot snapshot;

    @Column(name = "complete", nullable = false)
    private boolean complete;

    @Column(name = "candidate_day_count", nullable = false)
    private int candidateDayCount;

    @Column(name = "fact_count", nullable = false)
    private int factCount;

    @Column(name = "exact_fact_count", nullable = false)
    private int exactFactCount;

    @Column(name = "fingerprint", nullable = false, length = 64)
    private String fingerprint;

    protected PayrollSnapshotP15WorkTimeManifest() {
    }

    public PayrollSnapshotP15WorkTimeManifest(
            PayrollSnapshot snapshot,
            int candidateDayCount,
            int factCount,
            int exactFactCount,
            String fingerprint
    ) {
        this.snapshot = Objects.requireNonNull(snapshot, "P15 work-time manifest requires snapshot");
        if (candidateDayCount < 0
                || factCount < 0
                || exactFactCount < 0
                || factCount > candidateDayCount
                || exactFactCount > factCount) {
            throw new IllegalArgumentException("P15 work-time manifest counts are invalid");
        }
        if (fingerprint == null || !fingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("P15 work-time manifest fingerprint must be SHA-256");
        }
        this.candidateDayCount = candidateDayCount;
        this.factCount = factCount;
        this.exactFactCount = exactFactCount;
        this.complete = candidateDayCount == factCount && factCount == exactFactCount;
        this.fingerprint = fingerprint;
    }

    public Long getSnapshotId() { return snapshotId; }
    public PayrollSnapshot getSnapshot() { return snapshot; }
    public boolean isComplete() { return complete; }
    public int getCandidateDayCount() { return candidateDayCount; }
    public int getFactCount() { return factCount; }
    public int getExactFactCount() { return exactFactCount; }
    public String getFingerprint() { return fingerprint; }
}
