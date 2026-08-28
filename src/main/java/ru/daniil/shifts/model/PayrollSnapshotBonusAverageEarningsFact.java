package ru.daniil.shifts.model;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Locale;
import java.util.Objects;

/**
 * Immutable snapshot-time copy of one explicit paragraph-15 bonus factual row.
 *
 * <p>The row freezes both sides of the authority join: the exact D2 bonus
 * source line (identity, period, money, currency) and the additional F1 facts
 * required by future paragraph-15 policy. It does not calculate inclusion or
 * infer any missing fact.</p>
 */
@Entity
@Table(
        name = "payroll_snapshot_bonus_average_earnings_facts",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_payroll_snapshot_bonus_average_fact_order",
                        columnNames = {"snapshot_id", "fact_index"}
                ),
                @UniqueConstraint(
                        name = "uq_payroll_snapshot_bonus_average_source",
                        columnNames = {"snapshot_id", "bonus_source_fact_id"}
                ),
                @UniqueConstraint(
                        name = "uq_payroll_snapshot_bonus_average_fact_identity",
                        columnNames = {"snapshot_id", "bonus_average_fact_id"}
                )
        },
        indexes = {
                @Index(
                        name = "idx_payroll_snapshot_bonus_average_facts_snapshot",
                        columnList = "snapshot_id,fact_index"
                ),
                @Index(
                        name = "idx_payroll_snapshot_bonus_average_facts_indicator",
                        columnList = "snapshot_id,indicator_key,award_period_from,award_period_to,fact_index"
                )
        }
)
public class PayrollSnapshotBonusAverageEarningsFact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "snapshot_id", nullable = false)
    private PayrollSnapshot snapshot;

    @Column(name = "fact_index", nullable = false)
    private int factIndex;

    @Column(name = "bonus_source_fact_id", nullable = false)
    private long bonusSourceFactId;

    @Column(name = "bonus_average_fact_id", nullable = false)
    private long bonusAverageFactId;

    @Column(name = "component_id", nullable = false)
    private long componentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "earning_kind", nullable = false, length = 32)
    private PayrollEarningKind earningKind;

    @Column(name = "source_period_from", nullable = false)
    private LocalDate sourcePeriodFrom;

    @Column(name = "source_period_to", nullable = false)
    private LocalDate sourcePeriodTo;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "indicator_key", nullable = false, length = 96)
    private String indicatorKey;

    @Column(name = "award_period_from", nullable = false)
    private LocalDate awardPeriodFrom;

    @Column(name = "award_period_to", nullable = false)
    private LocalDate awardPeriodTo;

    @Column(name = "annual_result")
    private Boolean annualResult;

    @Column(name = "accrued_for_actual_work_time")
    private Boolean accruedForActualWorkTime;

    @Column(name = "prorated_for_partial_award_period")
    private Boolean proratedForPartialAwardPeriod;

    protected PayrollSnapshotBonusAverageEarningsFact() {
    }

    public PayrollSnapshotBonusAverageEarningsFact(
            PayrollSnapshot snapshot,
            int factIndex,
            long bonusSourceFactId,
            long bonusAverageFactId,
            long componentId,
            PayrollEarningKind earningKind,
            LocalDate sourcePeriodFrom,
            LocalDate sourcePeriodTo,
            long amountMinor,
            String currencyCode,
            String indicatorKey,
            LocalDate awardPeriodFrom,
            LocalDate awardPeriodTo,
            Boolean annualResult,
            Boolean accruedForActualWorkTime,
            Boolean proratedForPartialAwardPeriod
    ) {
        this.snapshot = Objects.requireNonNull(
                snapshot,
                "Snapshot bonus average-earnings fact requires snapshot"
        );

        if (factIndex < 0
                || bonusSourceFactId <= 0L
                || bonusAverageFactId <= 0L
                || componentId <= 0L) {
            throw new IllegalArgumentException(
                    "Snapshot bonus average-earnings identity is invalid"
            );
        }

        PayrollEarningKind kind = requireBonusKind(earningKind);

        if (sourcePeriodFrom == null
                || sourcePeriodTo == null
                || sourcePeriodTo.isBefore(sourcePeriodFrom)
                || !YearMonth.from(sourcePeriodFrom)
                        .equals(YearMonth.from(sourcePeriodTo))) {
            throw new IllegalArgumentException(
                    "Snapshot bonus source period is invalid"
            );
        }

        if (amountMinor <= 0L || amountMinor > 1_000_000_000_000L) {
            throw new IllegalArgumentException(
                    "Snapshot bonus source amount is invalid"
            );
        }

        String currency = currencyCode == null
                ? ""
                : currencyCode.trim().toUpperCase(Locale.ROOT);

        if (!currency.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException(
                    "Snapshot bonus source currency is invalid"
            );
        }

        String indicator = indicatorKey == null
                ? ""
                : indicatorKey.trim().toUpperCase(Locale.ROOT);

        if (!indicator.matches("[A-Z0-9][A-Z0-9._:-]{0,95}")) {
            throw new IllegalArgumentException(
                    "Snapshot bonus indicator key is invalid"
            );
        }

        if (awardPeriodFrom == null
                || awardPeriodTo == null
                || awardPeriodTo.isBefore(awardPeriodFrom)) {
            throw new IllegalArgumentException(
                    "Snapshot bonus award period is invalid"
            );
        }

        if (Boolean.TRUE.equals(annualResult)) {
            if (kind != PayrollEarningKind.ONE_TIME_BONUS
                    || awardPeriodFrom.getMonthValue() != 1
                    || awardPeriodFrom.getDayOfMonth() != 1
                    || awardPeriodTo.getMonthValue() != 12
                    || awardPeriodTo.getDayOfMonth() != 31
                    || awardPeriodFrom.getYear() != awardPeriodTo.getYear()) {
                throw new IllegalArgumentException(
                        "Snapshot annual-result bonus requires one complete calendar year"
                );
            }
        }

        this.factIndex = factIndex;
        this.bonusSourceFactId = bonusSourceFactId;
        this.bonusAverageFactId = bonusAverageFactId;
        this.componentId = componentId;
        this.earningKind = kind;
        this.sourcePeriodFrom = sourcePeriodFrom;
        this.sourcePeriodTo = sourcePeriodTo;
        this.amountMinor = amountMinor;
        this.currencyCode = currency;
        this.indicatorKey = indicator;
        this.awardPeriodFrom = awardPeriodFrom;
        this.awardPeriodTo = awardPeriodTo;
        this.annualResult = annualResult;
        this.accruedForActualWorkTime = accruedForActualWorkTime;
        this.proratedForPartialAwardPeriod = proratedForPartialAwardPeriod;
    }

    private static PayrollEarningKind requireBonusKind(
            PayrollEarningKind kind
    ) {
        if (kind != PayrollEarningKind.MONTHLY_BONUS
                && kind != PayrollEarningKind.ONE_TIME_BONUS) {
            throw new IllegalArgumentException(
                    "Snapshot bonus average-earnings kind is invalid"
            );
        }
        return kind;
    }

    public Long getId() { return id; }
    public PayrollSnapshot getSnapshot() { return snapshot; }
    public int getFactIndex() { return factIndex; }
    public long getBonusSourceFactId() { return bonusSourceFactId; }
    public long getBonusAverageFactId() { return bonusAverageFactId; }
    public long getComponentId() { return componentId; }
    public PayrollEarningKind getEarningKind() { return earningKind; }
    public LocalDate getSourcePeriodFrom() { return sourcePeriodFrom; }
    public LocalDate getSourcePeriodTo() { return sourcePeriodTo; }
    public long getAmountMinor() { return amountMinor; }
    public String getCurrencyCode() { return currencyCode; }
    public String getIndicatorKey() { return indicatorKey; }
    public LocalDate getAwardPeriodFrom() { return awardPeriodFrom; }
    public LocalDate getAwardPeriodTo() { return awardPeriodTo; }
    public Boolean getAnnualResult() { return annualResult; }
    public Boolean getAccruedForActualWorkTime() { return accruedForActualWorkTime; }
    public Boolean getProratedForPartialAwardPeriod() { return proratedForPartialAwardPeriod; }
}
