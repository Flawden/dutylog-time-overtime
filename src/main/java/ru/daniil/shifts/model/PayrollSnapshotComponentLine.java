package ru.daniil.shifts.model;

import jakarta.persistence.*;

import java.time.LocalDate;

/**
 * Frozen explainability line belonging to one immutable Payroll snapshot.
 *
 * componentId/versionId are copied scalar provenance, intentionally not
 * foreign keys to mutable effective-dated configuration. Historical Payroll
 * explanation therefore survives later configuration changes.
 */
@Entity
@Table(
        name = "payroll_snapshot_compensation_component_lines",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_payroll_snapshot_component_line_order",
                        columnNames = {"snapshot_id", "line_index"}
                ),
                @UniqueConstraint(
                        name = "uq_payroll_snapshot_component_identity",
                        columnNames = {"snapshot_id", "component_id"}
                )
        },
        indexes = @Index(
                name = "idx_payroll_snapshot_component_lines_snapshot",
                columnList = "snapshot_id,line_index"
        )
)
public class PayrollSnapshotComponentLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "snapshot_id", nullable = false)
    private PayrollSnapshot snapshot;

    @Column(name = "line_index", nullable = false)
    private int lineIndex;

    @Column(name = "component_id", nullable = false)
    private long componentId;

    @Column(name = "version_id", nullable = false)
    private long versionId;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    /**
     * Immutable machine semantic provenance.
     * NULL means the source component was explicitly UNCLASSIFIED.
     */
    @jakarta.persistence.Enumerated(
            jakarta.persistence.EnumType.STRING
    )
    @Column(name = "earning_kind", length = 40)
    private PayrollEarningKind earningKind;

    @Column(name = "calculation_type", nullable = false, length = 24)
    private String calculationType;

    @Column(name = "calculation_base", length = 32)
    private String calculationBase;

    @Column(name = "rate_bps")
    private Integer rateBps;

    @Column(name = "configured_amount_minor")
    private Long configuredAmountMinor;

    @Column(name = "configured_currency_code", length = 3)
    private String configuredCurrencyCode;

    @Column(name = "reference_base_minor", nullable = false)
    private long referenceBaseMinor;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    protected PayrollSnapshotComponentLine() {}

    public PayrollSnapshotComponentLine(
            PayrollSnapshot snapshot,
            int lineIndex,
            long componentId,
            long versionId,
            LocalDate effectiveFrom,
            String displayName,
            String calculationType,
            String calculationBase,
            Integer rateBps,
            Long configuredAmountMinor,
            String configuredCurrencyCode,
            long referenceBaseMinor,
            long amountMinor
    ) {
        this(
                snapshot,
                lineIndex,
                componentId,
                versionId,
                effectiveFrom,
                displayName,
                null,
                calculationType,
                calculationBase,
                rateBps,
                configuredAmountMinor,
                configuredCurrencyCode,
                referenceBaseMinor,
                amountMinor
        );
    }

    public PayrollSnapshotComponentLine(
            PayrollSnapshot snapshot,
            int lineIndex,
            long componentId,
            long versionId,
            LocalDate effectiveFrom,
            String displayName,
            PayrollEarningKind earningKind,
            String calculationType,
            String calculationBase,
            Integer rateBps,
            Long configuredAmountMinor,
            String configuredCurrencyCode,
            long referenceBaseMinor,
            long amountMinor
    ) {
        if (snapshot == null) {
            throw new IllegalArgumentException(
                    "Snapshot component line requires snapshot"
            );
        }

        if (lineIndex < 0
                || componentId <= 0
                || versionId <= 0
                || effectiveFrom == null
                || effectiveFrom.getDayOfMonth() != 1
                || referenceBaseMinor < 0
                || amountMinor < 0) {
            throw new IllegalArgumentException(
                    "Snapshot component line identity or money is invalid"
            );
        }

        String name =
                displayName == null
                        ? ""
                        : displayName.trim();

        if (name.isEmpty()
                || name.length() > 120) {
            throw new IllegalArgumentException(
                    "Snapshot component line name must contain 1..120 characters"
            );
        }

        if (earningKind != null
                && !earningKind
                        .isGenericCompensationComponentKind()) {
            throw new IllegalArgumentException(
                    "Unsupported snapshot compensation earning kind"
            );
        }

        String type =
                calculationType == null
                        ? ""
                        : calculationType.trim();

        if ("FIXED_AMOUNT".equals(type)) {
            if (calculationBase != null
                    || rateBps != null
                    || configuredAmountMinor == null
                    || configuredAmountMinor < 1
                    || configuredAmountMinor > 1_000_000_000_000L
                    || configuredCurrencyCode == null
                    || !configuredCurrencyCode.matches("[A-Z]{3}")
                    || referenceBaseMinor != 0
                    || amountMinor != configuredAmountMinor) {
                throw new IllegalArgumentException(
                        "Invalid fixed snapshot component line"
                );
            }
        } else if ("PERCENT_OF_BASE".equals(type)) {
            if (!"NOMINAL_SALARY".equals(calculationBase)
                    && !"EARNED_BASE_PAY".equals(calculationBase)
                    && !"LOCAL_ELIGIBLE_EARNINGS".equals(calculationBase)) {
                throw new IllegalArgumentException(
                        "Invalid percentage snapshot component base"
                );
            }

            if (rateBps == null
                    || rateBps < 1
                    || rateBps > 10_000_000
                    || configuredAmountMinor != null
                    || configuredCurrencyCode != null) {
                throw new IllegalArgumentException(
                        "Invalid percentage snapshot component line"
                );
            }
        } else {
            throw new IllegalArgumentException(
                    "Unsupported snapshot component calculation type"
            );
        }

        this.snapshot = snapshot;
        this.lineIndex = lineIndex;
        this.componentId = componentId;
        this.versionId = versionId;
        this.effectiveFrom = effectiveFrom.withDayOfMonth(1);
        this.displayName = name;
        this.earningKind = earningKind;
        this.calculationType = type;
        this.calculationBase = calculationBase;
        this.rateBps = rateBps;
        this.configuredAmountMinor = configuredAmountMinor;
        this.configuredCurrencyCode = configuredCurrencyCode;
        this.referenceBaseMinor = referenceBaseMinor;
        this.amountMinor = amountMinor;
    }

    public Long getId() { return id; }
    public PayrollSnapshot getSnapshot() { return snapshot; }
    public int getLineIndex() { return lineIndex; }
    public long getComponentId() { return componentId; }
    public long getVersionId() { return versionId; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public String getDisplayName() { return displayName; }
    public PayrollEarningKind getEarningKind() { return earningKind; }
    public String getCalculationType() { return calculationType; }
    public String getCalculationBase() { return calculationBase; }
    public Integer getRateBps() { return rateBps; }
    public Long getConfiguredAmountMinor() { return configuredAmountMinor; }
    public String getConfiguredCurrencyCode() { return configuredCurrencyCode; }
    public long getReferenceBaseMinor() { return referenceBaseMinor; }
    public long getAmountMinor() { return amountMinor; }
}
