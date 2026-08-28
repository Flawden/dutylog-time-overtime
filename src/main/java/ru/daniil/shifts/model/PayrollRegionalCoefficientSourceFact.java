package ru.daniil.shifts.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Locale;
import java.util.Objects;

/**
 * Explicit observed REGIONAL_COEFFICIENT source-line fact.
 *
 * <p>This is earning-period evidence, not a base formula. The regional money
 * and source period may be known while the underlying eligible earnings are
 * calculated separately by the machine-owned LOCAL_ELIGIBLE_EARNINGS policy.
 * DutyLog therefore never reconstructs or allocates that base from this row.</p>
 *
 * <p>One fact must remain inside one payroll month. Missing facts mean the
 * exact source period is unknown; they never authorize using posting month or
 * effective-dated configuration as earning provenance.</p>
 */
@Entity
@Table(
        name = "payroll_regional_coefficient_source_facts",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_payroll_regional_source_owner_component_start",
                columnNames = {
                        "user_id",
                        "component_id",
                        "period_from"
                }
        ),
        indexes = {
                @Index(
                        name = "idx_payroll_regional_source_owner_component_dates",
                        columnList = "user_id,component_id,period_from,period_to,id"
                ),
                @Index(
                        name = "idx_payroll_regional_source_owner_month",
                        columnList = "user_id,period_from,component_id,id"
                )
        }
)
public class PayrollRegionalCoefficientSourceFact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser owner;

    /** Stable historical generic-component identity; deliberately not a FK. */
    @Column(name = "component_id", nullable = false)
    private long componentId;

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

    protected PayrollRegionalCoefficientSourceFact() {
    }

    public PayrollRegionalCoefficientSourceFact(
            AppUser owner,
            long componentId,
            LocalDate periodFrom,
            LocalDate periodTo,
            long amountMinor,
            String currencyCode
    ) {
        this.owner = Objects.requireNonNull(
                owner,
                "Regional source fact requires owner"
        );
        this.componentId = componentId;
        apply(
                periodFrom,
                periodTo,
                amountMinor,
                currencyCode
        );
    }

    public void update(
            LocalDate periodFrom,
            LocalDate periodTo,
            long amountMinor,
            String currencyCode
    ) {
        apply(
                periodFrom,
                periodTo,
                amountMinor,
                currencyCode
        );
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
                    "Regional source component id must be positive"
            );
        }

        if (periodFrom == null || periodTo == null) {
            throw new IllegalArgumentException(
                    "Regional source period is required"
            );
        }

        if (periodTo.isBefore(periodFrom)) {
            throw new IllegalArgumentException(
                    "Regional source period is invalid"
            );
        }

        if (!YearMonth.from(periodFrom).equals(YearMonth.from(periodTo))) {
            throw new IllegalArgumentException(
                    "Regional source period must stay within one payroll month"
            );
        }

        if (amountMinor <= 0L
                || amountMinor > 1_000_000_000_000L) {
            throw new IllegalArgumentException(
                    "Regional source observed amount is invalid"
            );
        }

        String currency = currencyCode == null
                ? ""
                : currencyCode.trim().toUpperCase(Locale.ROOT);

        if (!currency.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException(
                    "Regional source currency must contain three letters"
            );
        }

        this.periodFrom = periodFrom;
        this.periodTo = periodTo;
        this.amountMinor = amountMinor;
        this.currencyCode = currency;
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
                    "Regional source owner is required"
            );
        }

        try {
            apply(
                    periodFrom,
                    periodTo,
                    amountMinor,
                    currencyCode
            );
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(
                    "Persisted regional source fact is invalid",
                    ex
            );
        }
    }

    public Long getId() { return id; }
    public AppUser getOwner() { return owner; }
    public long getComponentId() { return componentId; }
    public LocalDate getPeriodFrom() { return periodFrom; }
    public LocalDate getPeriodTo() { return periodTo; }
    public long getAmountMinor() { return amountMinor; }
    public String getCurrencyCode() { return currencyCode; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
