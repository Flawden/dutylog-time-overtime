package ru.daniil.shifts.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Effective-dated pricing rule version.
 *
 * The row becomes active from effectiveFrom until the next term.
 *
 * Unlike CompensationTerm this boundary is not month-normalized:
 * pricing policy may legitimately change on any calendar date.
 *
 * This entity stores configuration only. It does not select the valuation
 * date, resolve factual provenance or calculate money.
 */
@Entity
@Table(
        name = "pay_pricing_terms",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_pay_pricing_term_owner_effective",
                columnNames = {
                        "user_id",
                        "effective_from"
                }
        ),
        indexes = @Index(
                name = "idx_pay_pricing_terms_owner_effective",
                columnList = "user_id, effective_from"
        )
)
public class PayPricingTerm {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "user_id",
            nullable = false
    )
    private AppUser owner;

    @Column(
            name = "effective_from",
            nullable = false
    )
    private LocalDate effectiveFrom;

    @OneToMany(
            mappedBy = "term",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    @OrderBy("code ASC, id ASC")
    private List<PayPricingRule> rules =
            new ArrayList<>();

    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private Instant createdAt =
            Instant.now();

    @Column(
            name = "updated_at",
            nullable = false
    )
    private Instant updatedAt =
            Instant.now();

    protected PayPricingTerm() {}

    public PayPricingTerm(
            AppUser owner,
            LocalDate effectiveFrom
    ) {
        if (owner == null) {
            throw new IllegalArgumentException(
                    "Pricing term requires owner"
            );
        }

        if (effectiveFrom == null) {
            throw new IllegalArgumentException(
                    "Pricing term effective date is required"
            );
        }

        this.owner = owner;
        this.effectiveFrom = effectiveFrom;
    }

    public PayPricingRule addRule(
            String code,
            String dimension,
            int premiumBps,
            int fromMinute,
            Integer toMinuteExclusive,
            String exclusiveGroup
    ) {
        String cleanCode =
                code == null
                        ? ""
                        : code.trim();

        boolean duplicate =
                rules.stream()
                        .anyMatch(item ->
                                item.getCode()
                                        .equals(cleanCode)
                        );

        if (duplicate) {
            throw new IllegalArgumentException(
                    "Duplicate pricing rule code: "
                            + cleanCode
            );
        }

        PayPricingRule rule =
                new PayPricingRule(
                        this,
                        cleanCode,
                        dimension,
                        premiumBps,
                        fromMinute,
                        toMinuteExclusive,
                        exclusiveGroup
                );

        rules.add(rule);
        updatedAt = Instant.now();

        return rule;
    }

    public void clearRules() {
        rules.clear();
        updatedAt = Instant.now();
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

    public List<PayPricingRule> getRules() {
        return List.copyOf(rules);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
