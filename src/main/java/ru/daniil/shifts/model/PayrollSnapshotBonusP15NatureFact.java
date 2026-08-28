package ru.daniil.shifts.model;

import jakarta.persistence.*;

import java.util.Objects;

/** Immutable snapshot-time copy of one explicit F3A paragraph-15 reward-nature fact. */
@Entity
@Table(
        name = "payroll_snapshot_bonus_p15_nature_facts",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_payroll_snapshot_bonus_p15_nature_order", columnNames = {"snapshot_id", "fact_index"}),
                @UniqueConstraint(name = "uq_payroll_snapshot_bonus_p15_nature_source", columnNames = {"snapshot_id", "bonus_source_fact_id"}),
                @UniqueConstraint(name = "uq_payroll_snapshot_bonus_p15_nature_average", columnNames = {"snapshot_id", "bonus_average_fact_id"}),
                @UniqueConstraint(name = "uq_payroll_snapshot_bonus_p15_nature_identity", columnNames = {"snapshot_id", "bonus_nature_fact_id"})
        },
        indexes = @Index(name = "idx_payroll_snapshot_bonus_p15_nature_snapshot", columnList = "snapshot_id,fact_index")
)
public class PayrollSnapshotBonusP15NatureFact {

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

    @Column(name = "bonus_nature_fact_id", nullable = false)
    private long bonusNatureFactId;

    @Column(name = "component_id", nullable = false)
    private long componentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "earning_kind", nullable = false, length = 32)
    private PayrollEarningKind earningKind;

    @Enumerated(EnumType.STRING)
    @Column(name = "p15_nature", nullable = false, length = 32)
    private PayrollBonusP15Nature p15Nature;

    protected PayrollSnapshotBonusP15NatureFact() {
    }

    public PayrollSnapshotBonusP15NatureFact(
            PayrollSnapshot snapshot,
            int factIndex,
            long bonusSourceFactId,
            long bonusAverageFactId,
            long bonusNatureFactId,
            long componentId,
            PayrollEarningKind earningKind,
            PayrollBonusP15Nature p15Nature
    ) {
        this.snapshot = Objects.requireNonNull(snapshot, "Snapshot P15 nature fact requires snapshot");
        if (factIndex < 0
                || bonusSourceFactId <= 0L
                || bonusAverageFactId <= 0L
                || bonusNatureFactId <= 0L
                || componentId <= 0L) {
            throw new IllegalArgumentException("Snapshot P15 nature fact identity is invalid");
        }
        this.earningKind = Objects.requireNonNull(earningKind, "Snapshot P15 nature earning kind is required");
        this.p15Nature = Objects.requireNonNull(p15Nature, "Snapshot P15 nature is required");
        validateCoarseIdentity(this.earningKind, this.p15Nature);
        this.factIndex = factIndex;
        this.bonusSourceFactId = bonusSourceFactId;
        this.bonusAverageFactId = bonusAverageFactId;
        this.bonusNatureFactId = bonusNatureFactId;
        this.componentId = componentId;
    }

    static void validateCoarseIdentity(PayrollEarningKind kind, PayrollBonusP15Nature nature) {
        if (nature == PayrollBonusP15Nature.MONTHLY) {
            if (kind != PayrollEarningKind.MONTHLY_BONUS) {
                throw new IllegalArgumentException("MONTHLY P15 nature requires MONTHLY_BONUS");
            }
            return;
        }
        if (kind != PayrollEarningKind.ONE_TIME_BONUS) {
            throw new IllegalArgumentException("Non-monthly P15 nature requires ONE_TIME_BONUS");
        }
    }

    public Long getId() { return id; }
    public PayrollSnapshot getSnapshot() { return snapshot; }
    public int getFactIndex() { return factIndex; }
    public long getBonusSourceFactId() { return bonusSourceFactId; }
    public long getBonusAverageFactId() { return bonusAverageFactId; }
    public long getBonusNatureFactId() { return bonusNatureFactId; }
    public long getComponentId() { return componentId; }
    public PayrollEarningKind getEarningKind() { return earningKind; }
    public PayrollBonusP15Nature getP15Nature() { return p15Nature; }
}
