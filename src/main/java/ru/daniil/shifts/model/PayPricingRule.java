package ru.daniil.shifts.model;

import jakarta.persistence.*;

import java.util.Locale;
import java.util.Set;

/**
 * One persisted premium rule inside one effective PayPricingTerm.
 *
 * premiumBps is an additive premium:
 *  5_000 = +0.50x
 * 10_000 = +1.00x
 *
 * The ordinary 1.00x base remains implicit in PayPricingEngine.
 */
@Entity
@Table(
        name = "pay_pricing_rules",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_pay_pricing_rule_term_code",
                columnNames = {
                        "term_id",
                        "rule_code"
                }
        ),
        indexes = @Index(
                name = "idx_pay_pricing_rules_term",
                columnList = "term_id, id"
        )
)
public class PayPricingRule {

    private static final Set<String> DIMENSIONS =
            Set.of(
                    "NIGHT",
                    "HOLIDAY",
                    "OVERTIME"
            );

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "term_id",
            nullable = false
    )
    private PayPricingTerm term;

    @Column(
            name = "rule_code",
            nullable = false,
            length = 80
    )
    private String code;

    @Column(
            nullable = false,
            length = 16
    )
    private String dimension;

    @Column(
            name = "premium_bps",
            nullable = false
    )
    private int premiumBps;

    @Column(
            name = "from_minute",
            nullable = false
    )
    private int fromMinute;

    @Column(
            name = "to_minute_exclusive"
    )
    private Integer toMinuteExclusive;

    @Column(
            name = "exclusive_group",
            length = 80
    )
    private String exclusiveGroup;

    protected PayPricingRule() {}

    PayPricingRule(
            PayPricingTerm term,
            String code,
            String dimension,
            int premiumBps,
            int fromMinute,
            Integer toMinuteExclusive,
            String exclusiveGroup
    ) {
        if (term == null) {
            throw new IllegalArgumentException(
                    "Pricing rule requires term"
            );
        }

        String cleanCode =
                code == null
                        ? ""
                        : code.trim();

        if (cleanCode.isEmpty()
                || cleanCode.length() > 80) {
            throw new IllegalArgumentException(
                    "Pricing rule code must contain 1..80 characters"
            );
        }

        String cleanDimension =
                dimension == null
                        ? ""
                        : dimension.trim()
                                .toUpperCase(
                                        Locale.ROOT
                                );

        if (!DIMENSIONS.contains(
                cleanDimension
        )) {
            throw new IllegalArgumentException(
                    "Pricing rule dimension must be NIGHT, HOLIDAY or OVERTIME"
            );
        }

        if (premiumBps < 0
                || premiumBps > 10_000_000) {
            throw new IllegalArgumentException(
                    "Pricing premium basis points out of range"
            );
        }

        if ("OVERTIME".equals(
                cleanDimension
        )) {
            if (fromMinute < 0) {
                throw new IllegalArgumentException(
                        "Overtime tier start cannot be negative"
                );
            }

            if (toMinuteExclusive != null
                    && toMinuteExclusive
                    <= fromMinute) {
                throw new IllegalArgumentException(
                        "Overtime tier end must be after start"
                );
            }
        } else if (fromMinute != 0
                || toMinuteExclusive != null) {
            throw new IllegalArgumentException(
                    "Only OVERTIME rules may define minute ranges"
            );
        }

        String cleanGroup =
                exclusiveGroup == null
                        ? null
                        : exclusiveGroup.trim();

        if (cleanGroup != null
                && cleanGroup.isEmpty()) {
            cleanGroup = null;
        }

        if (cleanGroup != null
                && cleanGroup.length() > 80) {
            throw new IllegalArgumentException(
                    "Pricing exclusive group is too long"
            );
        }

        this.term = term;
        this.code = cleanCode;
        this.dimension = cleanDimension;
        this.premiumBps = premiumBps;
        this.fromMinute = fromMinute;
        this.toMinuteExclusive =
                toMinuteExclusive;
        this.exclusiveGroup =
                cleanGroup;
    }

    public Long getId() {
        return id;
    }

    public PayPricingTerm getTerm() {
        return term;
    }

    public String getCode() {
        return code;
    }

    public String getDimension() {
        return dimension;
    }

    public int getPremiumBps() {
        return premiumBps;
    }

    public int getFromMinute() {
        return fromMinute;
    }

    public Integer getToMinuteExclusive() {
        return toMinuteExclusive;
    }

    public String getExclusiveGroup() {
        return exclusiveGroup;
    }
}
