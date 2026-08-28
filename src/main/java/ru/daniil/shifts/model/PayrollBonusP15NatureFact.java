package ru.daniil.shifts.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.YearMonth;
import java.util.Objects;

/**
 * Explicit factual authority for paragraph-15 reward nature.
 *
 * <p>F1 already preserves indicator, award period and worked-time facts, but
 * paragraph 15 also names legally distinct reward families. In particular a
 * one-time reward for a work period must not be guessed to be the same thing
 * as a one-time service-length reward merely because both are represented by
 * {@link PayrollEarningKind#ONE_TIME_BONUS}.</p>
 */
@Entity
@Table(
        name = "payroll_bonus_p15_nature_facts",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_payroll_bonus_p15_nature_owner_average_fact",
                        columnNames = {"user_id", "bonus_average_fact_id"}
                ),
                @UniqueConstraint(
                        name = "uq_payroll_bonus_p15_nature_owner_source_fact",
                        columnNames = {"user_id", "bonus_source_fact_id"}
                )
        },
        indexes = @Index(
                name = "idx_payroll_bonus_p15_nature_owner_component",
                columnList = "user_id,component_id,earning_kind,p15_nature,id"
        )
)
public class PayrollBonusP15NatureFact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser owner;

    /** Scalar historical identity; deliberately not a FK to mutable F1. */
    @Column(name = "bonus_average_fact_id", nullable = false)
    private long bonusAverageFactId;

    /** Scalar D2 identity copied from F1 for contradiction detection. */
    @Column(name = "bonus_source_fact_id", nullable = false)
    private long bonusSourceFactId;

    @Column(name = "component_id", nullable = false)
    private long componentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "earning_kind", nullable = false, length = 32)
    private PayrollEarningKind earningKind;

    @Enumerated(EnumType.STRING)
    @Column(name = "p15_nature", nullable = false, length = 32)
    private PayrollBonusP15Nature p15Nature;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected PayrollBonusP15NatureFact() {
    }

    public PayrollBonusP15NatureFact(
            AppUser owner,
            long bonusAverageFactId,
            long bonusSourceFactId,
            long componentId,
            PayrollEarningKind earningKind,
            PayrollBonusP15Nature p15Nature,
            String indicatorKey,
            java.time.LocalDate awardPeriodFrom,
            java.time.LocalDate awardPeriodTo,
            Boolean annualResult
    ) {
        this.owner = Objects.requireNonNull(owner, "P15 nature fact requires owner");
        this.bonusAverageFactId = bonusAverageFactId;
        this.bonusSourceFactId = bonusSourceFactId;
        this.componentId = componentId;
        this.earningKind = earningKind;
        this.p15Nature = p15Nature;
        validateAgainstAverageFact(
                indicatorKey,
                awardPeriodFrom,
                awardPeriodTo,
                annualResult
        );
    }

    public void update(
            PayrollBonusP15Nature p15Nature,
            String indicatorKey,
            java.time.LocalDate awardPeriodFrom,
            java.time.LocalDate awardPeriodTo,
            Boolean annualResult
    ) {
        this.p15Nature = p15Nature;
        validateAgainstAverageFact(
                indicatorKey,
                awardPeriodFrom,
                awardPeriodTo,
                annualResult
        );
        updatedAt = Instant.now();
    }

    public void validateAgainstAverageFact(
            String indicatorKey,
            java.time.LocalDate awardPeriodFrom,
            java.time.LocalDate awardPeriodTo,
            Boolean annualResult
    ) {
        if (bonusAverageFactId <= 0L
                || bonusSourceFactId <= 0L
                || componentId <= 0L) {
            throw new IllegalArgumentException(
                    "P15 nature fact identities must be positive"
            );
        }

        if (earningKind != PayrollEarningKind.MONTHLY_BONUS
                && earningKind != PayrollEarningKind.ONE_TIME_BONUS) {
            throw new IllegalArgumentException(
                    "P15 nature fact requires bonus earning kind"
            );
        }

        Objects.requireNonNull(p15Nature, "P15 nature is required");
        Objects.requireNonNull(awardPeriodFrom, "P15 award period start is required");
        Objects.requireNonNull(awardPeriodTo, "P15 award period end is required");

        if (awardPeriodTo.isBefore(awardPeriodFrom)) {
            throw new IllegalArgumentException("P15 award period is invalid");
        }

        if (indicatorKey == null || indicatorKey.isBlank()) {
            throw new IllegalArgumentException("P15 indicator identity is required");
        }

        switch (p15Nature) {
            case MONTHLY -> {
                if (earningKind != PayrollEarningKind.MONTHLY_BONUS
                        || !YearMonth.from(awardPeriodFrom)
                        .equals(YearMonth.from(awardPeriodTo))
                        || Boolean.TRUE.equals(annualResult)) {
                    throw new IllegalArgumentException(
                            "MONTHLY p15 nature contradicts average fact"
                    );
                }
            }

            case WORK_PERIOD -> {
                if (earningKind != PayrollEarningKind.ONE_TIME_BONUS
                        || Boolean.TRUE.equals(annualResult)
                        || !awardPeriodTo.isAfter(
                        awardPeriodFrom.plusMonths(1).minusDays(1)
                )) {
                    throw new IllegalArgumentException(
                            "WORK_PERIOD p15 nature requires a non-annual bonus for more than one month"
                    );
                }
            }

            case ANNUAL_RESULT -> {
                if (earningKind != PayrollEarningKind.ONE_TIME_BONUS
                        || !Boolean.TRUE.equals(annualResult)) {
                    throw new IllegalArgumentException(
                            "ANNUAL_RESULT p15 nature requires explicit annual-result fact"
                    );
                }
            }

            case SERVICE_LENGTH -> {
                if (earningKind != PayrollEarningKind.ONE_TIME_BONUS
                        || Boolean.TRUE.equals(annualResult)) {
                    throw new IllegalArgumentException(
                            "SERVICE_LENGTH p15 nature contradicts annual-result identity"
                    );
                }
            }
        }
    }

    @PrePersist
    void prePersist() {
        if (owner == null) {
            throw new IllegalStateException("P15 nature owner is required");
        }
        Instant now = Instant.now();
        createdAt = createdAt == null ? now : createdAt;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        if (owner == null || p15Nature == null) {
            throw new IllegalStateException("Persisted P15 nature fact is invalid");
        }
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public AppUser getOwner() { return owner; }
    public long getBonusAverageFactId() { return bonusAverageFactId; }
    public long getBonusSourceFactId() { return bonusSourceFactId; }
    public long getComponentId() { return componentId; }
    public PayrollEarningKind getEarningKind() { return earningKind; }
    public PayrollBonusP15Nature getP15Nature() { return p15Nature; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
