package ru.daniil.shifts.model;

import jakarta.persistence.*;

import java.time.LocalDate;

/**
 * Frozen machine-owned semantic earning belonging to one immutable Payroll
 * snapshot revision.
 *
 * The parent snapshot defines the posting/accounting month.
 *
 * earningPeriod:
 *     optional business period for which the earning was earned
 *     (important for bonuses and other period-based earnings).
 *
 * coverage:
 *     optional period whose time the earning pays/preserves.
 *     This is intentionally distinct from posting month and earning period.
 *
 * displayName is deliberately absent. Historical financial semantics come
 * only from PayrollEarningKind.
 */
@Entity
@Table(
        name = "payroll_snapshot_earning_lines",
        uniqueConstraints =
        @UniqueConstraint(
                name = "uq_payroll_snapshot_earning_line_order",
                columnNames = {
                        "snapshot_id",
                        "line_index"
                }
        ),
        indexes =
        @Index(
                name = "idx_payroll_snapshot_earning_lines_snapshot",
                columnList = "snapshot_id,line_index"
        )
)
public class PayrollSnapshotEarningLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "snapshot_id",
            nullable = false
    )
    private PayrollSnapshot snapshot;

    @Column(
            name = "line_index",
            nullable = false
    )
    private int lineIndex;

    @Column(
            name = "earning_kind",
            nullable = false,
            length = 48
    )
    private String earningKind;

    /*
     * Freeze the phase that belonged to the machine semantic kind when the
     * snapshot was created. The constructor never accepts phase independently.
     */
    @Column(
            name = "earning_phase",
            nullable = false,
            length = 48
    )
    private String earningPhase;

    @Column(
            name = "amount_minor",
            nullable = false
    )
    private long amountMinor;

    @Column(
            name = "qualified_quantity_value"
    )
    private Long qualifiedQuantityValue;

    @Column(
            name = "qualified_quantity_unit",
            length = 32
    )
    private String qualifiedQuantityUnit;

    @Column(
            name = "earning_period_from"
    )
    private LocalDate earningPeriodFrom;

    @Column(
            name = "earning_period_to"
    )
    private LocalDate earningPeriodTo;

    @Column(
            name = "coverage_from"
    )
    private LocalDate coverageFrom;

    @Column(
            name = "coverage_to"
    )
    private LocalDate coverageTo;

    protected PayrollSnapshotEarningLine() {
    }

    public PayrollSnapshotEarningLine(
            PayrollSnapshot snapshot,
            int lineIndex,
            PayrollEarningKind earningKind,
            long amountMinor,
            PayrollQualifiedQuantity qualifiedQuantity,
            LocalDate earningPeriodFrom,
            LocalDate earningPeriodTo,
            LocalDate coverageFrom,
            LocalDate coverageTo
    ) {
        if (snapshot == null) {
            throw new IllegalArgumentException(
                    "Snapshot earning line requires snapshot"
            );
        }

        if (lineIndex < 0) {
            throw new IllegalArgumentException(
                    "Snapshot earning line index must be non-negative"
            );
        }

        if (earningKind == null) {
            throw new IllegalArgumentException(
                    "Snapshot earning line requires machine earning kind"
            );
        }

        if (amountMinor < 0) {
            throw new IllegalArgumentException(
                    "Snapshot earning amount must be non-negative"
            );
        }

        requireOrderedPair(
                earningPeriodFrom,
                earningPeriodTo,
                "earning period"
        );

        requireOrderedPair(
                coverageFrom,
                coverageTo,
                "coverage period"
        );

        this.snapshot = snapshot;
        this.lineIndex = lineIndex;
        this.earningKind = earningKind.name();
        this.earningPhase =
                earningKind
                        .phase()
                        .name();

        this.amountMinor = amountMinor;

        if (qualifiedQuantity == null) {
            this.qualifiedQuantityValue = null;
            this.qualifiedQuantityUnit = null;
        } else {
            this.qualifiedQuantityValue =
                    qualifiedQuantity.value();

            this.qualifiedQuantityUnit =
                    qualifiedQuantity
                            .unit()
                            .name();
        }

        this.earningPeriodFrom =
                earningPeriodFrom;

        this.earningPeriodTo =
                earningPeriodTo;

        this.coverageFrom =
                coverageFrom;

        this.coverageTo =
                coverageTo;
    }

    private static void requireOrderedPair(
            LocalDate from,
            LocalDate to,
            String label
    ) {
        if ((from == null) != (to == null)) {
            throw new IllegalArgumentException(
                    "Snapshot earning "
                            + label
                            + " must be either fully present or absent"
            );
        }

        if (from != null
                && to.isBefore(
                        from
                )) {
            throw new IllegalArgumentException(
                    "Snapshot earning "
                            + label
                            + " is reversed"
            );
        }
    }

    public Long getId() {
        return id;
    }

    public PayrollSnapshot getSnapshot() {
        return snapshot;
    }

    public int getLineIndex() {
        return lineIndex;
    }

    public String getEarningKind() {
        return earningKind;
    }

    public String getEarningPhase() {
        return earningPhase;
    }

    public long getAmountMinor() {
        return amountMinor;
    }

    public Long getQualifiedQuantityValue() {
        return qualifiedQuantityValue;
    }

    public String getQualifiedQuantityUnit() {
        return qualifiedQuantityUnit;
    }

    public LocalDate getEarningPeriodFrom() {
        return earningPeriodFrom;
    }

    public LocalDate getEarningPeriodTo() {
        return earningPeriodTo;
    }

    public LocalDate getCoverageFrom() {
        return coverageFrom;
    }

    public LocalDate getCoverageTo() {
        return coverageTo;
    }
}
