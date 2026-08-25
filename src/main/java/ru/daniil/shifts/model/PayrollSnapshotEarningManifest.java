package ru.daniil.shifts.model;

import jakarta.persistence.*;

import java.util.Objects;

/**
 * Integrity/completeness declaration for the semantic earning freeze of one
 * immutable Payroll snapshot revision.
 *
 * Absence of a manifest means historical semantic completeness is unknown.
 *
 * complete=false means the snapshot may contain a partial semantic earning
 * freeze and MUST NOT be used as a complete historical earnings month.
 *
 * complete=true means lineCount, amountMinor and fingerprint describe the
 * complete machine-owned semantic earning set for this snapshot.
 */
@Entity
@Table(
        name = "payroll_snapshot_earning_manifests"
)
public class PayrollSnapshotEarningManifest {

    @Id
    @Column(
            name = "snapshot_id"
    )
    private Long snapshotId;

    @OneToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @MapsId
    @JoinColumn(
            name = "snapshot_id",
            nullable = false
    )
    private PayrollSnapshot snapshot;

    @Column(
            name = "complete",
            nullable = false
    )
    private boolean complete;

    @Column(
            name = "line_count",
            nullable = false
    )
    private int lineCount;

    @Column(
            name = "amount_minor",
            nullable = false
    )
    private long amountMinor;

    @Column(
            name = "fingerprint",
            nullable = false,
            length = 64
    )
    private String fingerprint;

    protected PayrollSnapshotEarningManifest() {
    }

    public PayrollSnapshotEarningManifest(
            PayrollSnapshot snapshot,
            boolean complete,
            int lineCount,
            long amountMinor,
            String fingerprint
    ) {
        this.snapshot =
                Objects.requireNonNull(
                        snapshot,
                        "Semantic earning manifest requires snapshot"
                );

        if (lineCount < 0) {
            throw new IllegalArgumentException(
                    "Semantic earning line count must be non-negative"
            );
        }

        if (amountMinor < 0) {
            throw new IllegalArgumentException(
                    "Semantic earning amount must be non-negative"
            );
        }

        if (fingerprint == null
                || !fingerprint.matches(
                        "[0-9a-f]{64}"
                )) {
            throw new IllegalArgumentException(
                    "Semantic earning fingerprint must be SHA-256"
            );
        }

        this.complete = complete;
        this.lineCount = lineCount;
        this.amountMinor = amountMinor;
        this.fingerprint = fingerprint;
    }

    public Long getSnapshotId() {
        return snapshotId;
    }

    public PayrollSnapshot getSnapshot() {
        return snapshot;
    }

    public boolean isComplete() {
        return complete;
    }

    public int getLineCount() {
        return lineCount;
    }

    public long getAmountMinor() {
        return amountMinor;
    }

    public String getFingerprint() {
        return fingerprint;
    }
}
