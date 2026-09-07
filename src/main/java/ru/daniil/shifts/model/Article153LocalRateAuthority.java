package ru.daniil.shifts.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.Objects;

/**
 * Source-backed legal authority for the HOLIDAY rules of one exact
 * effective-dated PayPricingTerm.
 *
 * <p>The pricing term remains the country-neutral economic configuration.
 * This sidecar proves that its HOLIDAY rule set is backed by one of the
 * Article 153 sources allowed to establish a concrete rate: collective
 * agreement, local normative act, or employment contract.</p>
 *
 * <p>The authority is immutable. If the underlying HOLIDAY rule set changes,
 * its SHA-256 fingerprint no longer matches and resolution fails closed.
 * The correction path is a new effective PayPricingTerm plus a new authority,
 * preserving history.</p>
 */
@Entity
@Table(
        name = "article153_local_rate_authorities",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_article153_local_rate_term",
                columnNames = {"user_id", "pay_pricing_term_id"}
        ),
        indexes = @Index(
                name = "idx_article153_local_rate_owner_term",
                columnList = "user_id,pay_pricing_term_id,id"
        )
)
public class Article153LocalRateAuthority {
    public static final String SOURCE_COLLECTIVE_AGREEMENT = "COLLECTIVE_AGREEMENT";
    public static final String SOURCE_LOCAL_NORMATIVE_ACT = "LOCAL_NORMATIVE_ACT";
    public static final String SOURCE_EMPLOYMENT_CONTRACT = "EMPLOYMENT_CONTRACT";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser owner;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pay_pricing_term_id", nullable = false)
    private PayPricingTerm pricingTerm;

    @Column(name = "source_kind", nullable = false, length = 40)
    private String sourceKind;

    @Column(name = "source_reference", nullable = false, length = 500)
    private String sourceReference;

    @Column(name = "source_revision", nullable = false, length = 160)
    private String sourceRevision;

    @Column(name = "holiday_policy_fingerprint", nullable = false, length = 64)
    private String holidayPolicyFingerprint;

    @Column(name = "certified_at", nullable = false, updatable = false)
    private Instant certifiedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Article153LocalRateAuthority() {
    }

    public Article153LocalRateAuthority(
            AppUser owner,
            PayPricingTerm pricingTerm,
            String sourceKind,
            String sourceReference,
            String sourceRevision,
            String holidayPolicyFingerprint,
            Instant certifiedAt
    ) {
        this.owner = Objects.requireNonNull(owner, "Article 153 local rate requires owner");
        this.pricingTerm = Objects.requireNonNull(pricingTerm, "Article 153 local rate requires pricing term");
        this.sourceKind = clean(sourceKind);
        this.sourceReference = clean(sourceReference);
        this.sourceRevision = clean(sourceRevision);
        this.holidayPolicyFingerprint = clean(holidayPolicyFingerprint);
        this.certifiedAt = Objects.requireNonNull(certifiedAt, "Article 153 local rate requires certifiedAt");
        this.createdAt = certifiedAt;
        validate();
    }

    @PrePersist
    void beforeInsert() {
        Instant now = Instant.now();
        if (certifiedAt == null) certifiedAt = now;
        if (createdAt == null) createdAt = certifiedAt;
        validate();
    }

    private void validate() {
        if (owner == null || pricingTerm == null) {
            throw new IllegalStateException("Article 153 local rate owner/term is required");
        }
        if (!SOURCE_COLLECTIVE_AGREEMENT.equals(sourceKind)
                && !SOURCE_LOCAL_NORMATIVE_ACT.equals(sourceKind)
                && !SOURCE_EMPLOYMENT_CONTRACT.equals(sourceKind)) {
            throw new IllegalStateException("Article 153 local rate source kind is invalid");
        }
        if (sourceReference == null || sourceReference.isBlank() || sourceReference.length() > 500) {
            throw new IllegalStateException("Article 153 local rate source reference is invalid");
        }
        if (sourceRevision == null || sourceRevision.isBlank() || sourceRevision.length() > 160) {
            throw new IllegalStateException("Article 153 local rate source revision is invalid");
        }
        if (holidayPolicyFingerprint == null
                || !holidayPolicyFingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalStateException("Article 153 local rate fingerprint must be SHA-256");
        }
        if (certifiedAt == null || createdAt == null) {
            throw new IllegalStateException("Article 153 local rate audit timestamps are required");
        }
    }

    private static String clean(String value) {
        return value == null ? null : value.trim();
    }

    public Long getId() { return id; }
    public AppUser getOwner() { return owner; }
    public PayPricingTerm getPricingTerm() { return pricingTerm; }
    public String getSourceKind() { return sourceKind; }
    public String getSourceReference() { return sourceReference; }
    public String getSourceRevision() { return sourceRevision; }
    public String getHolidayPolicyFingerprint() { return holidayPolicyFingerprint; }
    public Instant getCertifiedAt() { return certifiedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
