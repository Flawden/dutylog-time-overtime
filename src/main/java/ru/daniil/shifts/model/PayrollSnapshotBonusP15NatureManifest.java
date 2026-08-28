package ru.daniil.shifts.model;

import jakarta.persistence.*;

/** Completeness/integrity declaration for immutable F3A reward-nature facts of one Payroll snapshot. */
@Entity
@Table(name = "payroll_snapshot_bonus_p15_nature_manifests")
public class PayrollSnapshotBonusP15NatureManifest {

    @Id
    @Column(name = "snapshot_id")
    private Long snapshotId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "snapshot_id", nullable = false)
    private PayrollSnapshot snapshot;

    @Column(name = "complete", nullable = false)
    private boolean complete;

    @Column(name = "average_fact_count", nullable = false)
    private int averageFactCount;

    @Column(name = "nature_fact_count", nullable = false)
    private int natureFactCount;

    @Column(name = "fingerprint", nullable = false, length = 64)
    private String fingerprint;

    protected PayrollSnapshotBonusP15NatureManifest() {
    }

    public PayrollSnapshotBonusP15NatureManifest(
            PayrollSnapshot snapshot,
            int averageFactCount,
            int natureFactCount,
            String fingerprint
    ) {
        if (snapshot == null) throw new IllegalArgumentException("Snapshot P15 nature manifest requires snapshot");
        if (averageFactCount < 0 || natureFactCount < 0 || natureFactCount > averageFactCount) {
            throw new IllegalArgumentException("Snapshot P15 nature manifest counts are invalid");
        }
        if (fingerprint == null || !fingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Snapshot P15 nature fingerprint is invalid");
        }
        this.snapshot = snapshot;
        this.averageFactCount = averageFactCount;
        this.natureFactCount = natureFactCount;
        this.complete = averageFactCount == natureFactCount;
        this.fingerprint = fingerprint;
    }

    public Long getSnapshotId() { return snapshotId; }
    public PayrollSnapshot getSnapshot() { return snapshot; }
    public boolean isComplete() { return complete; }
    public int getAverageFactCount() { return averageFactCount; }
    public int getNatureFactCount() { return natureFactCount; }
    public String getFingerprint() { return fingerprint; }
}
