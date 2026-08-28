package ru.daniil.shifts.model;

import jakarta.persistence.*;

/**
 * Completeness/integrity declaration for immutable paragraph-15 bonus facts of
 * one Payroll snapshot revision.
 *
 * <p>complete=false is meaningful: at least one explicit D2 bonus source fact
 * existed at freeze time without a matching F1 factual row. It is never filled
 * with inferred defaults.</p>
 */
@Entity
@Table(name = "payroll_snapshot_bonus_average_earnings_manifests")
public class PayrollSnapshotBonusAverageEarningsManifest {

    @Id
    @Column(name = "snapshot_id")
    private Long snapshotId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "snapshot_id", nullable = false)
    private PayrollSnapshot snapshot;

    @Column(name = "complete", nullable = false)
    private boolean complete;

    @Column(name = "source_fact_count", nullable = false)
    private int sourceFactCount;

    @Column(name = "fact_count", nullable = false)
    private int factCount;

    @Column(name = "fingerprint", nullable = false, length = 64)
    private String fingerprint;

    protected PayrollSnapshotBonusAverageEarningsManifest() {
    }

    public PayrollSnapshotBonusAverageEarningsManifest(
            PayrollSnapshot snapshot,
            int sourceFactCount,
            int factCount,
            String fingerprint
    ) {
        if (snapshot == null) {
            throw new IllegalArgumentException(
                    "Snapshot bonus average-earnings manifest requires snapshot"
            );
        }

        if (sourceFactCount < 0
                || factCount < 0
                || factCount > sourceFactCount) {
            throw new IllegalArgumentException(
                    "Snapshot bonus average-earnings manifest counts are invalid"
            );
        }

        if (fingerprint == null
                || !fingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(
                    "Snapshot bonus average-earnings fingerprint must be SHA-256"
            );
        }

        this.snapshot = snapshot;
        this.sourceFactCount = sourceFactCount;
        this.factCount = factCount;
        this.complete = sourceFactCount == factCount;
        this.fingerprint = fingerprint;
    }

    public Long getSnapshotId() { return snapshotId; }
    public PayrollSnapshot getSnapshot() { return snapshot; }
    public boolean isComplete() { return complete; }
    public int getSourceFactCount() { return sourceFactCount; }
    public int getFactCount() { return factCount; }
    public String getFingerprint() { return fingerprint; }
}
