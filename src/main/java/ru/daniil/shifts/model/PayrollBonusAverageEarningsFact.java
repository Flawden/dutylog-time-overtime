package ru.daniil.shifts.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;

/**
 * Explicit factual authority required before paragraph-15 premium treatment.
 *
 * <p>This row is deliberately separate from {@link PayrollBonusSourceFact}.
 * The source fact answers "what bonus line/period/money was observed". This
 * authority answers only the additional facts paragraph 15 needs: stable
 * indicator identity, the work period for which the bonus was accrued, annual
 * result identity, and whether the employer already accrued the premium by
 * actual/proportional worked time.</p>
 *
 * <p>No paragraph-15 inclusion formula lives here. In particular, source
 * period, posting month, component effectiveFrom and display labels are never
 * promoted into award-period or annual-result semantics.</p>
 */
@Entity
@Table(
        name = "payroll_bonus_average_earnings_facts",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_payroll_bonus_average_owner_source_fact",
                columnNames = {"user_id", "bonus_source_fact_id"}
        ),
        indexes = {
                @Index(
                        name = "idx_payroll_bonus_average_owner_indicator",
                        columnList = "user_id,indicator_key,award_period_from,award_period_to,id"
                ),
                @Index(
                        name = "idx_payroll_bonus_average_owner_component",
                        columnList = "user_id,component_id,earning_kind,id"
                )
        }
)
public class PayrollBonusAverageEarningsFact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser owner;

    /** Historical scalar identity; deliberately not a FK to mutable fact/config. */
    @Column(name = "bonus_source_fact_id", nullable = false)
    private long bonusSourceFactId;

    /** Frozen source component identity for contradiction detection. */
    @Column(name = "component_id", nullable = false)
    private long componentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "earning_kind", nullable = false, length = 32)
    private PayrollEarningKind earningKind;

    /** Stable machine identity for the paragraph-15 "each indicator" rule. */
    @Column(name = "indicator_key", nullable = false, length = 96)
    private String indicatorKey;

    /** Period of work for which this bonus was accrued; not source/posting period. */
    @Column(name = "award_period_from", nullable = false)
    private LocalDate awardPeriodFrom;

    @Column(name = "award_period_to", nullable = false)
    private LocalDate awardPeriodTo;

    /** Explicit fact that this is a result-of-calendar-year reward. */
    @Column(name = "annual_result", nullable = false)
    private Boolean annualResult;

    /**
     * Explicit source fact for paragraph 15's exception from later proportional
     * reduction when the premium itself was accrued for actually worked time.
     */
    @Column(name = "accrued_for_actual_work_time", nullable = false)
    private Boolean accruedForActualWorkTime;

    /**
     * Explicit source fact for paragraph 15's incomplete award-period rule:
     * the employer already accrued the premium proportionally to worked time.
     */
    @Column(name = "prorated_for_partial_award_period", nullable = false)
    private Boolean proratedForPartialAwardPeriod;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected PayrollBonusAverageEarningsFact() {
    }

    public PayrollBonusAverageEarningsFact(
            AppUser owner,
            long bonusSourceFactId,
            long componentId,
            PayrollEarningKind earningKind,
            String indicatorKey,
            LocalDate awardPeriodFrom,
            LocalDate awardPeriodTo,
            Boolean annualResult,
            Boolean accruedForActualWorkTime,
            Boolean proratedForPartialAwardPeriod
    ) {
        this.owner = Objects.requireNonNull(
                owner,
                "Bonus average-earnings fact requires owner"
        );
        this.bonusSourceFactId = bonusSourceFactId;
        this.componentId = componentId;
        this.earningKind = requireBonusKind(earningKind);
        apply(
                indicatorKey,
                awardPeriodFrom,
                awardPeriodTo,
                annualResult,
                accruedForActualWorkTime,
                proratedForPartialAwardPeriod
        );
    }

    public void update(
            String indicatorKey,
            LocalDate awardPeriodFrom,
            LocalDate awardPeriodTo,
            Boolean annualResult,
            Boolean accruedForActualWorkTime,
            Boolean proratedForPartialAwardPeriod
    ) {
        apply(
                indicatorKey,
                awardPeriodFrom,
                awardPeriodTo,
                annualResult,
                accruedForActualWorkTime,
                proratedForPartialAwardPeriod
        );
        updatedAt = Instant.now();
    }

    private void apply(
            String indicatorKey,
            LocalDate awardPeriodFrom,
            LocalDate awardPeriodTo,
            Boolean annualResult,
            Boolean accruedForActualWorkTime,
            Boolean proratedForPartialAwardPeriod
    ) {
        if (bonusSourceFactId <= 0L || componentId <= 0L) {
            throw new IllegalArgumentException(
                    "Bonus average-earnings source/component identity must be positive"
            );
        }

        requireBonusKind(earningKind);

        String normalizedIndicator = indicatorKey == null
                ? ""
                : indicatorKey.trim().toUpperCase(Locale.ROOT);

        if (!normalizedIndicator.matches("[A-Z0-9][A-Z0-9._:-]{0,95}")) {
            throw new IllegalArgumentException(
                    "Bonus average-earnings indicator key is invalid"
            );
        }

        if (awardPeriodFrom == null || awardPeriodTo == null
                || awardPeriodTo.isBefore(awardPeriodFrom)) {
            throw new IllegalArgumentException(
                    "Bonus average-earnings award period is invalid"
            );
        }

        if (Boolean.TRUE.equals(annualResult)) {
            if (earningKind != PayrollEarningKind.ONE_TIME_BONUS
                    || awardPeriodFrom.getMonthValue() != 1
                    || awardPeriodFrom.getDayOfMonth() != 1
                    || awardPeriodTo.getMonthValue() != 12
                    || awardPeriodTo.getDayOfMonth() != 31
                    || awardPeriodFrom.getYear() != awardPeriodTo.getYear()) {
                throw new IllegalArgumentException(
                        "Annual-result bonus requires one complete calendar year"
                );
            }
        }

        this.indicatorKey = normalizedIndicator;
        this.awardPeriodFrom = awardPeriodFrom;
        this.awardPeriodTo = awardPeriodTo;
        this.annualResult = annualResult;
        this.accruedForActualWorkTime = accruedForActualWorkTime;
        this.proratedForPartialAwardPeriod = proratedForPartialAwardPeriod;
    }

    private static PayrollEarningKind requireBonusKind(
            PayrollEarningKind earningKind
    ) {
        if (earningKind != PayrollEarningKind.MONTHLY_BONUS
                && earningKind != PayrollEarningKind.ONE_TIME_BONUS) {
            throw new IllegalArgumentException(
                    "Bonus average-earnings earning kind must be a bonus kind"
            );
        }
        return earningKind;
    }

    @PrePersist
    void prePersist() {
        validatePersistentShape();
        Instant now = Instant.now();
        createdAt = createdAt == null ? now : createdAt;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        validatePersistentShape();
        updatedAt = Instant.now();
    }

    private void validatePersistentShape() {
        if (owner == null) {
            throw new IllegalStateException(
                    "Bonus average-earnings owner is required"
            );
        }

        try {
            apply(
                    indicatorKey,
                    awardPeriodFrom,
                    awardPeriodTo,
                    annualResult,
                    accruedForActualWorkTime,
                    proratedForPartialAwardPeriod
            );
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(
                    "Persisted bonus average-earnings fact is invalid",
                    ex
            );
        }
    }

    public Long getId() { return id; }
    public AppUser getOwner() { return owner; }
    public long getBonusSourceFactId() { return bonusSourceFactId; }
    public long getComponentId() { return componentId; }
    public PayrollEarningKind getEarningKind() { return earningKind; }
    public String getIndicatorKey() { return indicatorKey; }
    public LocalDate getAwardPeriodFrom() { return awardPeriodFrom; }
    public LocalDate getAwardPeriodTo() { return awardPeriodTo; }
    public Boolean getAnnualResult() { return annualResult; }
    public Boolean getAccruedForActualWorkTime() { return accruedForActualWorkTime; }
    public Boolean getProratedForPartialAwardPeriod() { return proratedForPartialAwardPeriod; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
