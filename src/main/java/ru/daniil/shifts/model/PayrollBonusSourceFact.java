package ru.daniil.shifts.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Locale;
import java.util.Objects;

/**
 * Explicit observed source-line fact for a bonus earning.
 *
 * <p>This authority stores only facts supplied by a trusted source: stable
 * component identity, observed semantic bonus kind, source period, money and
 * currency. It does not decide average-earnings paragraph 15 treatment and it
 * does not infer an earning period from posting month or effective-dated
 * component configuration.</p>
 *
 * <p>MONTHLY_BONUS money may be machine-calculated separately from
 * LOCAL_ELIGIBLE_EARNINGS. ONE_TIME_BONUS may have no generic percentage-base
 * authority at all. In both cases this row is source provenance only.</p>
 */
@Entity
@Table(
        name = "payroll_bonus_source_facts",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_payroll_bonus_source_owner_component_kind_start",
                columnNames = {
                        "user_id",
                        "component_id",
                        "earning_kind",
                        "period_from"
                }
        ),
        indexes = {
                @Index(
                        name = "idx_payroll_bonus_source_owner_component_kind_dates",
                        columnList = "user_id,component_id,earning_kind,period_from,period_to,id"
                ),
                @Index(
                        name = "idx_payroll_bonus_source_owner_month",
                        columnList = "user_id,period_from,earning_kind,component_id,id"
                )
        }
)
public class PayrollBonusSourceFact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser owner;

    /** Stable historical generic-component identity; deliberately not a FK. */
    @Column(name = "component_id", nullable = false)
    private long componentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "earning_kind", nullable = false, length = 32)
    private PayrollEarningKind earningKind;

    @Column(name = "period_from", nullable = false)
    private LocalDate periodFrom;

    @Column(name = "period_to", nullable = false)
    private LocalDate periodTo;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected PayrollBonusSourceFact() {
    }

    public PayrollBonusSourceFact(
            AppUser owner,
            long componentId,
            PayrollEarningKind earningKind,
            LocalDate periodFrom,
            LocalDate periodTo,
            long amountMinor,
            String currencyCode
    ) {
        this.owner = Objects.requireNonNull(
                owner,
                "Bonus source fact requires owner"
        );
        this.componentId = componentId;
        this.earningKind = requireBonusKind(earningKind);
        apply(periodFrom, periodTo, amountMinor, currencyCode);
    }

    public void update(
            LocalDate periodFrom,
            LocalDate periodTo,
            long amountMinor,
            String currencyCode
    ) {
        apply(periodFrom, periodTo, amountMinor, currencyCode);
        updatedAt = Instant.now();
    }

    private void apply(
            LocalDate periodFrom,
            LocalDate periodTo,
            long amountMinor,
            String currencyCode
    ) {
        if (componentId <= 0L) {
            throw new IllegalArgumentException(
                    "Bonus source component id must be positive"
            );
        }

        requireBonusKind(earningKind);

        if (periodFrom == null || periodTo == null) {
            throw new IllegalArgumentException(
                    "Bonus source period is required"
            );
        }

        if (periodTo.isBefore(periodFrom)) {
            throw new IllegalArgumentException(
                    "Bonus source period is invalid"
            );
        }

        if (!YearMonth.from(periodFrom).equals(YearMonth.from(periodTo))) {
            throw new IllegalArgumentException(
                    "Bonus source period must stay within one payroll month"
            );
        }

        if (amountMinor <= 0L || amountMinor > 1_000_000_000_000L) {
            throw new IllegalArgumentException(
                    "Bonus source observed amount is invalid"
            );
        }

        String currency = currencyCode == null
                ? ""
                : currencyCode.trim().toUpperCase(Locale.ROOT);

        if (!currency.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException(
                    "Bonus source currency must contain three letters"
            );
        }

        this.periodFrom = periodFrom;
        this.periodTo = periodTo;
        this.amountMinor = amountMinor;
        this.currencyCode = currency;
    }

    private static PayrollEarningKind requireBonusKind(
            PayrollEarningKind earningKind
    ) {
        if (earningKind != PayrollEarningKind.MONTHLY_BONUS
                && earningKind != PayrollEarningKind.ONE_TIME_BONUS) {
            throw new IllegalArgumentException(
                    "Bonus source earning kind must be MONTHLY_BONUS or ONE_TIME_BONUS"
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
                    "Bonus source owner is required"
            );
        }

        try {
            apply(periodFrom, periodTo, amountMinor, currencyCode);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(
                    "Persisted bonus source fact is invalid",
                    ex
            );
        }
    }

    public Long getId() { return id; }
    public AppUser getOwner() { return owner; }
    public long getComponentId() { return componentId; }
    public PayrollEarningKind getEarningKind() { return earningKind; }
    public LocalDate getPeriodFrom() { return periodFrom; }
    public LocalDate getPeriodTo() { return periodTo; }
    public long getAmountMinor() { return amountMinor; }
    public String getCurrencyCode() { return currencyCode; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
