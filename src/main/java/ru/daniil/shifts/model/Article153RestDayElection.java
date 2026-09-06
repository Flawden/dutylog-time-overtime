package ru.daniil.shifts.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Persistent, source-locked employee election of the Article 153
 * "another rest day" branch for one exact qualifying work event/date.
 *
 * <p>This is deliberately not an overtime-bank credit, usage or absence.
 * It records the employee's legal election only. Payroll pricing, rest-day
 * scheduling/consumption and immutable payroll snapshot freeze belong to later
 * authorities.</p>
 */
@Entity
@Table(
        name = "article153_rest_day_elections",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_article153_rest_day_election_source",
                columnNames = {"user_id", "work_date", "source_identity"}
        ),
        indexes = {
                @Index(
                        name = "idx_article153_rest_day_elections_owner_date",
                        columnList = "user_id,work_date,id"
                ),
                @Index(
                        name = "idx_article153_rest_day_elections_source",
                        columnList = "user_id,source_identity,id"
                )
        }
)
public class Article153RestDayElection {
    public static final String STATUS_ELECTED = "ELECTED";
    public static final String STATUS_REVOKED = "REVOKED";
    public static final String SOURCE_EXPLICIT = "EXPLICIT";
    public static final String SOURCE_PLAN_DERIVED = "PLAN_DERIVED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser owner;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Column(name = "source_kind", nullable = false, length = 20)
    private String sourceKind;

    @Column(name = "source_identity", nullable = false, length = 96)
    private String sourceIdentity;

    @Column(name = "source_actual_work_interval_id")
    private Long sourceActualWorkIntervalId;

    @Column(name = "source_day_entry_id")
    private Long sourceDayEntryId;

    @Column(name = "source_evidence_start_instant", nullable = false)
    private Instant sourceEvidenceStartInstant;

    @Column(name = "source_evidence_end_instant", nullable = false)
    private Instant sourceEvidenceEndInstant;

    @Column(name = "source_evidence_timezone", nullable = false, length = 80)
    private String sourceEvidenceTimezone;

    @Column(name = "qualified_cause", nullable = false, length = 32)
    private String qualifiedCause;

    @Column(name = "qualified_minutes", nullable = false)
    private Integer qualifiedMinutes;

    @Column(name = "source_event_fingerprint", nullable = false, length = 64)
    private String sourceEventFingerprint;

    @Column(name = "status", nullable = false, length = 16)
    private String status = STATUS_ELECTED;

    @Column(name = "elected_at", nullable = false, updatable = false)
    private Instant electedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revocation_reason", length = 500)
    private String revocationReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Article153RestDayElection() {
    }

    public Article153RestDayElection(
            AppUser owner,
            LocalDate workDate,
            String sourceKind,
            String sourceIdentity,
            Long sourceActualWorkIntervalId,
            Long sourceDayEntryId,
            Instant sourceEvidenceStartInstant,
            Instant sourceEvidenceEndInstant,
            String sourceEvidenceTimezone,
            String qualifiedCause,
            int qualifiedMinutes,
            String sourceEventFingerprint,
            Instant electedAt
    ) {
        this.owner = Objects.requireNonNull(owner, "Article 153 election requires owner");
        this.workDate = Objects.requireNonNull(workDate, "Article 153 election requires work date");
        this.sourceKind = sourceKind;
        this.sourceIdentity = sourceIdentity;
        this.sourceActualWorkIntervalId = sourceActualWorkIntervalId;
        this.sourceDayEntryId = sourceDayEntryId;
        this.sourceEvidenceStartInstant = sourceEvidenceStartInstant;
        this.sourceEvidenceEndInstant = sourceEvidenceEndInstant;
        this.sourceEvidenceTimezone = sourceEvidenceTimezone;
        this.qualifiedCause = qualifiedCause;
        this.qualifiedMinutes = qualifiedMinutes;
        this.sourceEventFingerprint = sourceEventFingerprint;
        this.status = STATUS_ELECTED;
        this.electedAt = Objects.requireNonNull(electedAt, "Article 153 election requires electedAt");
        this.createdAt = electedAt;
        this.updatedAt = electedAt;
        validate();
    }

    public void revoke(String reason, Instant at) {
        if (!STATUS_ELECTED.equals(status)) {
            throw new IllegalStateException("Article 153 election is not active");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Article 153 election revocation requires correction reason");
        }
        Instant timestamp = Objects.requireNonNull(at, "Article 153 revocation requires timestamp");
        this.status = STATUS_REVOKED;
        this.revokedAt = timestamp;
        this.revocationReason = reason.trim();
        this.updatedAt = timestamp;
        validate();
    }

    @PrePersist
    void beforeInsert() {
        Instant now = Instant.now();
        if (electedAt == null) electedAt = now;
        if (createdAt == null) createdAt = electedAt;
        if (updatedAt == null) updatedAt = electedAt;
        validate();
    }

    @PreUpdate
    void beforeUpdate() {
        if (updatedAt == null) updatedAt = Instant.now();
        validate();
    }

    private void validate() {
        if (owner == null || workDate == null) {
            throw new IllegalStateException("Article 153 election owner/date is required");
        }
        if (!SOURCE_EXPLICIT.equals(sourceKind) && !SOURCE_PLAN_DERIVED.equals(sourceKind)) {
            throw new IllegalStateException("Article 153 election source kind is invalid");
        }
        if (sourceIdentity == null || sourceIdentity.isBlank()) {
            throw new IllegalStateException("Article 153 election source identity is required");
        }
        if (SOURCE_EXPLICIT.equals(sourceKind)) {
            if (sourceActualWorkIntervalId == null || sourceActualWorkIntervalId <= 0L || sourceDayEntryId != null) {
                throw new IllegalStateException("Explicit Article 153 election requires only ActualWork identity");
            }
            if (!sourceIdentity.equals(SOURCE_EXPLICIT + ":" + sourceActualWorkIntervalId)) {
                throw new IllegalStateException("Explicit Article 153 election source identity disagrees with source id");
            }
        } else {
            if (sourceDayEntryId == null || sourceDayEntryId <= 0L || sourceActualWorkIntervalId != null) {
                throw new IllegalStateException("Plan-derived Article 153 election requires only DayEntry identity");
            }
            if (!sourceIdentity.equals(SOURCE_PLAN_DERIVED + ":" + sourceDayEntryId)) {
                throw new IllegalStateException("Plan-derived Article 153 election source identity disagrees with source id");
            }
        }
        if (sourceEvidenceStartInstant == null
                || sourceEvidenceEndInstant == null
                || !sourceEvidenceEndInstant.isAfter(sourceEvidenceStartInstant)) {
            throw new IllegalStateException("Article 153 election requires ordered source evidence instants");
        }
        if (sourceEvidenceTimezone == null || sourceEvidenceTimezone.isBlank()) {
            throw new IllegalStateException("Article 153 election requires source timezone");
        }
        if (qualifiedCause == null || !qualifiedCause.matches("PUBLIC_HOLIDAY|EMPLOYEE_REST_DAY|BOTH")) {
            throw new IllegalStateException("Article 153 election qualified cause is invalid");
        }
        if (qualifiedMinutes == null || qualifiedMinutes <= 0) {
            throw new IllegalStateException("Article 153 election qualified minutes must be positive");
        }
        if (sourceEventFingerprint == null || !sourceEventFingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalStateException("Article 153 election source fingerprint is invalid");
        }
        if (!STATUS_ELECTED.equals(status) && !STATUS_REVOKED.equals(status)) {
            throw new IllegalStateException("Article 153 election status is invalid");
        }
        if (electedAt == null || createdAt == null || updatedAt == null) {
            throw new IllegalStateException("Article 153 election audit timestamps are required");
        }
        if (STATUS_ELECTED.equals(status)) {
            if (revokedAt != null || revocationReason != null) {
                throw new IllegalStateException("Active Article 153 election cannot contain revocation audit");
            }
        } else {
            if (revokedAt == null || revocationReason == null || revocationReason.isBlank()) {
                throw new IllegalStateException("Revoked Article 153 election requires audit reason and timestamp");
            }
        }
    }

    public Long getId() { return id; }
    public AppUser getOwner() { return owner; }
    public LocalDate getWorkDate() { return workDate; }
    public String getSourceKind() { return sourceKind; }
    public String getSourceIdentity() { return sourceIdentity; }
    public Long getSourceActualWorkIntervalId() { return sourceActualWorkIntervalId; }
    public Long getSourceDayEntryId() { return sourceDayEntryId; }
    public Instant getSourceEvidenceStartInstant() { return sourceEvidenceStartInstant; }
    public Instant getSourceEvidenceEndInstant() { return sourceEvidenceEndInstant; }
    public String getSourceEvidenceTimezone() { return sourceEvidenceTimezone; }
    public String getQualifiedCause() { return qualifiedCause; }
    public int getQualifiedMinutes() { return qualifiedMinutes; }
    public String getSourceEventFingerprint() { return sourceEventFingerprint; }
    public String getStatus() { return status; }
    public Instant getElectedAt() { return electedAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public String getRevocationReason() { return revocationReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
