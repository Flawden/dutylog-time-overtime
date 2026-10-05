package ru.daniil.shifts.model;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;
import java.util.Objects;

/** Unbooked tariff references; never part of PayrollSnapshot.totalPayMinor. */
@Entity @Immutable
@Table(name = "payroll_snapshot_article153_tariff")
public class PayrollSnapshotArticle153Tariff {
    @Id @Column(name = "snapshot_id") private Long snapshotId;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @MapsId
    @JoinColumn(name = "snapshot_id", nullable = false, updatable = false)
    private PayrollSnapshotArticle153 authority;
    @Column(name = "schema_version", nullable = false, updatable = false, length = 40)
    private String schemaVersion;
    @Column(name = "payload_json", nullable = false, updatable = false, columnDefinition = "text")
    private String payloadJson;
    @Column(nullable = false, updatable = false, length = 64) private String fingerprint;
    protected PayrollSnapshotArticle153Tariff() {}
    public PayrollSnapshotArticle153Tariff(PayrollSnapshotArticle153 authority, String schema, String json, String fingerprint) {
        this.authority = Objects.requireNonNull(authority);
        if (schema == null || schema.isBlank() || schema.length() > 40 || json == null || json.isBlank()
                || fingerprint == null || !fingerprint.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Invalid tariff document");
        this.schemaVersion = schema; this.payloadJson = json; this.fingerprint = fingerprint;
    }
    public Long getSnapshotId() { return snapshotId; }
    public PayrollSnapshotArticle153 getAuthority() { return authority; }
    public String getSchemaVersion() { return schemaVersion; }
    public String getPayloadJson() { return payloadJson; }
    public String getFingerprint() { return fingerprint; }
}
