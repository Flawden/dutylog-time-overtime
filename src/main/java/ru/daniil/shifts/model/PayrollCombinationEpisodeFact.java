package ru.daniil.shifts.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Locale;
import java.util.Objects;

/**
 * Explicit observed COMBINATION source-line fact.
 *
 * <p>This is evidence, not a formula. The observed payout, source period and
 * qualified minutes may be known while the external reference salary remains
 * unknown. DutyLog therefore never derives {@code amountMinor} from
 * {@code agreedRateBps} and never infers an external reference base.</p>
 *
 * <p>One fact must belong to exactly one payroll month. Cross-month source
 * evidence has to be entered as distinct source lines so no money is silently
 * prorated between accounting months.</p>
 */
@Entity
@Table(
        name = "payroll_combination_episode_facts",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_payroll_combination_episode_owner_component_start",
                columnNames = {
                        "user_id",
                        "component_id",
                        "period_from"
                }
        ),
        indexes = {
                @Index(
                        name = "idx_payroll_combination_episode_owner_component_dates",
                        columnList = "user_id,component_id,period_from,period_to,id"
                ),
                @Index(
                        name = "idx_payroll_combination_episode_owner_month",
                        columnList = "user_id,period_from,component_id,id"
                )
        }
)
public class PayrollCombinationEpisodeFact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser owner;

    /**
     * Stable generic compensation component identity copied as a scalar.
     * Deliberately not a FK: factual history must survive later configuration
     * deletion or replacement just like frozen snapshot component identity.
     */
    @Column(name = "component_id", nullable = false)
    private long componentId;

    @Column(name = "period_from", nullable = false)
    private LocalDate periodFrom;

    @Column(name = "period_to", nullable = false)
    private LocalDate periodTo;

    @Column(name = "qualified_minutes", nullable = false)
    private long qualifiedMinutes;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    /**
     * Optional observed agreement rate. It is explainability only and is never
     * used to reverse-engineer the unknown external salary base.
     */
    @Column(name = "agreed_rate_bps")
    private Integer agreedRateBps;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected PayrollCombinationEpisodeFact() {
    }

    public PayrollCombinationEpisodeFact(
            AppUser owner,
            long componentId,
            LocalDate periodFrom,
            LocalDate periodTo,
            long qualifiedMinutes,
            long amountMinor,
            String currencyCode,
            Integer agreedRateBps
    ) {
        this.owner = Objects.requireNonNull(
                owner,
                "Combination episode fact requires owner"
        );
        this.componentId = componentId;

        apply(
                periodFrom,
                periodTo,
                qualifiedMinutes,
                amountMinor,
                currencyCode,
                agreedRateBps
        );
    }

    public void update(
            LocalDate periodFrom,
            LocalDate periodTo,
            long qualifiedMinutes,
            long amountMinor,
            String currencyCode,
            Integer agreedRateBps
    ) {
        apply(
                periodFrom,
                periodTo,
                qualifiedMinutes,
                amountMinor,
                currencyCode,
                agreedRateBps
        );
        updatedAt = Instant.now();
    }

    private void apply(
            LocalDate periodFrom,
            LocalDate periodTo,
            long qualifiedMinutes,
            long amountMinor,
            String currencyCode,
            Integer agreedRateBps
    ) {
        if (componentId <= 0L) {
            throw new IllegalArgumentException(
                    "Combination episode component id must be positive"
            );
        }

        if (periodFrom == null || periodTo == null) {
            throw new IllegalArgumentException(
                    "Combination episode source period is required"
            );
        }

        if (periodTo.isBefore(periodFrom)) {
            throw new IllegalArgumentException(
                    "Combination episode source period is invalid"
            );
        }

        if (!YearMonth.from(periodFrom).equals(YearMonth.from(periodTo))) {
            throw new IllegalArgumentException(
                    "Combination episode source period must stay within one payroll month"
            );
        }

        if (qualifiedMinutes <= 0L) {
            throw new IllegalArgumentException(
                    "Combination episode qualified minutes must be positive"
            );
        }

        if (amountMinor <= 0L
                || amountMinor > 1_000_000_000_000L) {
            throw new IllegalArgumentException(
                    "Combination episode observed amount is invalid"
            );
        }

        String currency = currencyCode == null
                ? ""
                : currencyCode.trim().toUpperCase(Locale.ROOT);

        if (!currency.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException(
                    "Combination episode currency must contain three letters"
            );
        }

        if (agreedRateBps != null
                && (agreedRateBps < 1 || agreedRateBps > 10_000_000)) {
            throw new IllegalArgumentException(
                    "Combination episode agreed rate is invalid"
            );
        }

        this.periodFrom = periodFrom;
        this.periodTo = periodTo;
        this.qualifiedMinutes = qualifiedMinutes;
        this.amountMinor = amountMinor;
        this.currencyCode = currency;
        this.agreedRateBps = agreedRateBps;
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
                    "Combination episode owner is required"
            );
        }

        try {
            apply(
                    periodFrom,
                    periodTo,
                    qualifiedMinutes,
                    amountMinor,
                    currencyCode,
                    agreedRateBps
            );
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(
                    "Persisted combination episode fact is invalid",
                    ex
            );
        }
    }

    public Long getId() {
        return id;
    }

    public AppUser getOwner() {
        return owner;
    }

    public long getComponentId() {
        return componentId;
    }

    public LocalDate getPeriodFrom() {
        return periodFrom;
    }

    public LocalDate getPeriodTo() {
        return periodTo;
    }

    public long getQualifiedMinutes() {
        return qualifiedMinutes;
    }

    public long getAmountMinor() {
        return amountMinor;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public Integer getAgreedRateBps() {
        return agreedRateBps;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
