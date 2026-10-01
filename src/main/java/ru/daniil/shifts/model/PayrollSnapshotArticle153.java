package ru.daniil.shifts.model;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;
import java.util.Objects;

/** Versioned authority document belonging to exactly one immutable payroll revision. */
@Entity @Immutable
@Table(name = "payroll_snapshot_article153")
public class PayrollSnapshotArticle153 {
    @Id @Column(name = "snapshot_id") private Long snapshotId;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @MapsId
    @JoinColumn(name = "snapshot_id", nullable = false, updatable = false)
    private PayrollSnapshot snapshot;
    @Column(name = "schema_version", nullable = false, updatable = false, length = 40)
    private String schemaVersion;
    @Column(name = "piece_count", nullable = false, updatable = false)
    private int pieceCount;
    @Column(name = "qualified_minutes", nullable = false, updatable = false)
    private long qualifiedMinutes;
    @Column(name = "payload_json", nullable = false, updatable = false, columnDefinition = "text")
    private String payloadJson;
    @Column(nullable = false, updatable = false, length = 64)
    private String fingerprint;

    protected PayrollSnapshotArticle153() {}
    public PayrollSnapshotArticle153(PayrollSnapshot snapshot, String schemaVersion, int count,
            long minutes, String json, String fingerprint) {
        this.snapshot = Objects.requireNonNull(snapshot);
        if (schemaVersion == null || schemaVersion.isBlank() || schemaVersion.length() > 40
                || count < 0 || minutes < 0 || json == null || json.isBlank()
                || fingerprint == null || !fingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Invalid Article 153 snapshot envelope");
        }
        this.schemaVersion = schemaVersion; this.pieceCount = count; this.qualifiedMinutes = minutes;
        this.payloadJson = json; this.fingerprint = fingerprint;
    }
    public Long getSnapshotId() { return snapshotId; }
    public PayrollSnapshot getSnapshot() { return snapshot; }
    public String getSchemaVersion() { return schemaVersion; }
    public int getPieceCount() { return pieceCount; }
    public long getQualifiedMinutes() { return qualifiedMinutes; }
    public String getPayloadJson() { return payloadJson; }
    public String getFingerprint() { return fingerprint; }
}
