package ru.daniil.shifts.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Explicit effective-dated work/legal jurisdiction fact.
 *
 * <p>One row owns the half-open interval
 * {@code [effectiveFrom, nextTerm.effectiveFrom)}.</p>
 *
 * <p>There is intentionally no implicit baseline. Jurisdiction must never be
 * inferred from timezone, language, locale, production-calendar contents or
 * employment dates. Missing persisted authority remains unknown.</p>
 */
@Entity
@Table(
        name = "work_jurisdiction_terms",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_work_jurisdiction_term_owner_effective",
                columnNames = {"user_id", "effective_from"}
        ),
        indexes = @Index(
                name = "idx_work_jurisdiction_terms_owner_effective",
                columnList = "user_id,effective_from"
        )
)
public class WorkJurisdictionTerm {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser owner;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "jurisdiction_code", nullable = false, length = 16)
    private String jurisdictionCode;

    @Column(name = "region_code", length = 32)
    private String regionCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected WorkJurisdictionTerm() {
    }

    public WorkJurisdictionTerm(
            AppUser owner,
            LocalDate effectiveFrom,
            String jurisdictionCode,
            String regionCode
    ) {
        this.owner = Objects.requireNonNull(
                owner,
                "Work jurisdiction term requires owner"
        );
        this.effectiveFrom = Objects.requireNonNull(
                effectiveFrom,
                "Work jurisdiction effective date is required"
        );
        setJurisdiction(
                jurisdictionCode,
                regionCode
        );
    }

    public Long getId() {
        return id;
    }

    public AppUser getOwner() {
        return owner;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public String getJurisdictionCode() {
        return jurisdictionCode;
    }

    public String getRegionCode() {
        return regionCode;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setJurisdiction(
            String jurisdictionCode,
            String regionCode
    ) {
        this.jurisdictionCode = Objects.requireNonNull(
                jurisdictionCode,
                "Work jurisdiction code is required"
        );
        this.regionCode = regionCode;
        this.updatedAt = Instant.now();
    }

    @PrePersist
    void onCreate() {
        validate();
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        validate();
        updatedAt = Instant.now();
    }

    private void validate() {
        if (owner == null) {
            throw new IllegalStateException(
                    "Work jurisdiction owner is required"
            );
        }
        if (effectiveFrom == null) {
            throw new IllegalStateException(
                    "Work jurisdiction effective date is required"
            );
        }
        if (jurisdictionCode == null
                || jurisdictionCode.isBlank()) {
            throw new IllegalStateException(
                    "Work jurisdiction code is required"
            );
        }
        if (regionCode != null
                && regionCode.isBlank()) {
            throw new IllegalStateException(
                    "Work jurisdiction region code must be null or non-blank"
            );
        }
    }
}
