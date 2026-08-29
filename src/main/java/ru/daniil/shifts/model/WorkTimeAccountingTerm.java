package ru.daniil.shifts.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Explicit effective-dated work-time accounting regime FACT.
 *
 * <p>One row owns the half-open interval
 * {@code [effectiveFrom, nextTerm.effectiveFrom)}. There is intentionally no
 * implicit baseline: absence of a term means the historical regime is
 * unknown and downstream average-earnings logic must fail closed.</p>
 */
@Entity
@Table(
        name = "work_time_accounting_terms",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_work_time_accounting_term_owner_effective",
                columnNames = {"user_id", "effective_from"}
        ),
        indexes = @Index(
                name = "idx_work_time_accounting_terms_owner_effective",
                columnList = "user_id,effective_from"
        )
)
public class WorkTimeAccountingTerm {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser owner;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Enumerated(EnumType.STRING)
    @Column(name = "accounting_mode", nullable = false, length = 16)
    private WorkTimeAccountingMode accountingMode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected WorkTimeAccountingTerm() {}

    public WorkTimeAccountingTerm(
            AppUser owner,
            LocalDate effectiveFrom,
            WorkTimeAccountingMode accountingMode
    ) {
        this.owner = Objects.requireNonNull(
                owner,
                "Work-time accounting term requires owner"
        );
        this.effectiveFrom = Objects.requireNonNull(
                effectiveFrom,
                "Work-time accounting effective date is required"
        );
        this.accountingMode = Objects.requireNonNull(
                accountingMode,
                "Work-time accounting mode is required"
        );
    }

    public Long getId() { return id; }
    public AppUser getOwner() { return owner; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public WorkTimeAccountingMode getAccountingMode() { return accountingMode; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setAccountingMode(WorkTimeAccountingMode accountingMode) {
        this.accountingMode = Objects.requireNonNull(
                accountingMode,
                "Work-time accounting mode is required"
        );
        this.updatedAt = Instant.now();
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
