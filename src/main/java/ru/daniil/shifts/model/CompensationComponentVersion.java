package ru.daniil.shifts.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;

/**
 * Effective-month business definition of one stable compensation component.
 *
 * displayName is user-owned presentation text.
 * Calculation enums describe mathematical semantics only.
 *
 * Product labels such as "harmfulness", "regional coefficient" or
 * "professional allowance" deliberately do not exist as mandatory enums.
 */
@Entity
@Table(
        name = "compensation_component_versions",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_compensation_component_version",
                columnNames = {
                        "component_id",
                        "effective_from"
                }
        ),
        indexes = @Index(
                name = "idx_compensation_component_versions_effective",
                columnList = "component_id, effective_from, id"
        )
)
public class CompensationComponentVersion {

    public enum CalculationType {
        FIXED_AMOUNT,
        PERCENT_OF_BASE
    }

    public enum CalculationBase {
        NOMINAL_SALARY,
        EARNED_BASE_PAY,
        /**
         * Explicit machine-owned eligible-earnings base resolved from
         * PayrollEarningBaseEligibility for the component's earningKind.
         *
         * v27.48.0 initially wires this base only for
         * REGIONAL_COEFFICIENT. Other targets fail closed until their own
         * production authority is proven.
         */
        LOCAL_ELIGIBLE_EARNINGS
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "component_id", nullable = false)
    private CompensationComponent component;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    /**
     * Optional machine-owned semantic identity.
     *
     * NULL means explicitly unclassified historical/configuration state.
     * displayName must never be used to infer this value.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "earning_kind", length = 40)
    private PayrollEarningKind earningKind;

    @Enumerated(EnumType.STRING)
    @Column(name = "calculation_type", nullable = false, length = 24)
    private CalculationType calculationType;

    @Enumerated(EnumType.STRING)
    @Column(name = "calculation_base", length = 32)
    private CalculationBase calculationBase;

    @Column(name = "rate_bps")
    private Integer rateBps;

    @Column(name = "amount_minor")
    private Long amountMinor;

    @Column(name = "currency_code", length = 3)
    private String currencyCode;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected CompensationComponentVersion() {}

    public CompensationComponentVersion(
            CompensationComponent component,
            LocalDate effectiveFrom,
            String displayName,
            CalculationType calculationType,
            CalculationBase calculationBase,
            Integer rateBps,
            Long amountMinor,
            String currencyCode,
            boolean enabled
    ) {
        if (component == null) {
            throw new IllegalArgumentException(
                    "Compensation component version requires component"
            );
        }

        if (effectiveFrom == null) {
            throw new IllegalArgumentException(
                    "Compensation component effective month is required"
            );
        }

        this.component = component;
        this.effectiveFrom =
                effectiveFrom.withDayOfMonth(1);

        update(
                displayName,
                calculationType,
                calculationBase,
                rateBps,
                amountMinor,
                currencyCode,
                enabled
        );

        this.createdAt = Instant.now();
    }

    public void update(
            String displayName,
            CalculationType calculationType,
            CalculationBase calculationBase,
            Integer rateBps,
            Long amountMinor,
            String currencyCode,
            boolean enabled
    ) {
        String cleanName =
                displayName == null
                        ? ""
                        : displayName.trim();

        if (cleanName.isEmpty()
                || cleanName.length() > 120) {
            throw new IllegalArgumentException(
                    "Compensation component name must contain 1..120 characters"
            );
        }

        if (calculationType == null) {
            throw new IllegalArgumentException(
                    "Compensation component calculation type is required"
            );
        }

        String cleanCurrency =
                currencyCode == null
                        ? null
                        : currencyCode.trim()
                                .toUpperCase(Locale.ROOT);

        switch (calculationType) {
            case FIXED_AMOUNT -> {
                if (calculationBase != null
                        || rateBps != null
                        || amountMinor == null
                        || amountMinor < 1L
                        || amountMinor > 1_000_000_000_000L
                        || cleanCurrency == null
                        || !cleanCurrency.matches("[A-Z]{3}")) {
                    throw new IllegalArgumentException(
                            "Fixed component requires amount and currency only"
                    );
                }
            }

            case PERCENT_OF_BASE -> {
                if (calculationBase == null
                        || rateBps == null
                        || rateBps < 1
                        || rateBps > 10_000_000
                        || amountMinor != null
                        || cleanCurrency != null) {
                    throw new IllegalArgumentException(
                            "Percentage component requires semantic base and positive rate only"
                    );
                }
            }
        }

        this.displayName = cleanName;
        this.calculationType = calculationType;
        this.calculationBase = calculationBase;
        this.rateBps = rateBps;
        this.amountMinor = amountMinor;
        this.currencyCode = cleanCurrency;
        this.enabled = enabled;
        this.updatedAt = Instant.now();
    }

    public void updateEarningKind(
            PayrollEarningKind earningKind
    ) {
        if (earningKind != null
                && !earningKind
                        .isGenericCompensationComponentKind()) {
            throw new IllegalArgumentException(
                    "Unsupported generic compensation component earning kind"
            );
        }

        this.earningKind = earningKind;
        this.updatedAt = Instant.now();
    }

    @PrePersist
    @PreUpdate
    void validatePersistentShape() {
        if (effectiveFrom == null) {
            throw new IllegalStateException(
                    "Compensation component effective month is missing"
            );
        }

        if (earningKind != null
                && !earningKind
                        .isGenericCompensationComponentKind()) {
            throw new IllegalStateException(
                    "Unsupported generic compensation component earning kind"
            );
        }

        effectiveFrom =
                effectiveFrom.withDayOfMonth(1);

        update(
                displayName,
                calculationType,
                calculationBase,
                rateBps,
                amountMinor,
                currencyCode,
                enabled
        );
    }

    public Long getId() {
        return id;
    }

    public CompensationComponent getComponent() {
        return component;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public String getDisplayName() {
        return displayName;
    }

    public PayrollEarningKind getEarningKind() {
        return earningKind;
    }

    public CalculationType getCalculationType() {
        return calculationType;
    }

    public CalculationBase getCalculationBase() {
        return calculationBase;
    }

    public Integer getRateBps() {
        return rateBps;
    }

    public Long getAmountMinor() {
        return amountMinor;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
