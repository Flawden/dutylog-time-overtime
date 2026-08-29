package ru.daniil.shifts.model;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Immutable per-calendar-date scheduled-work FACT for paragraph-15 reference
 * worked-time resolution.
 *
 * <p>This row freezes source truth only. It does not decide the legal unit,
 * proportionality policy, premium amount, numerator or average earnings.</p>
 *
 * <p>workedOutsidePlanMinutes is intentionally separate from
 * plannedAndWorkedMinutes. Future paragraph-15 logic must never let overtime
 * or other off-schedule work erase a scheduled absence.</p>
 */
@Entity
@Table(
        name = "payroll_snapshot_p15_scheduled_work_facts",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_payroll_snapshot_p15_work_fact_order",
                        columnNames = {"snapshot_id", "fact_index"}
                ),
                @UniqueConstraint(
                        name = "uq_payroll_snapshot_p15_work_fact_date",
                        columnNames = {"snapshot_id", "source_date"}
                )
        },
        indexes = @Index(
                name = "idx_payroll_snapshot_p15_work_facts_snapshot",
                columnList = "snapshot_id,fact_index"
        )
)
public class PayrollSnapshotP15ScheduledWorkFact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "snapshot_id", nullable = false)
    private PayrollSnapshot snapshot;

    @Column(name = "fact_index", nullable = false)
    private int factIndex;

    @Column(name = "source_date", nullable = false)
    private LocalDate sourceDate;

    /** Scalar historical identity; deliberately not a FK to mutable F3F1 rows. */
    @Column(name = "work_time_accounting_term_id", nullable = false)
    private long workTimeAccountingTermId;

    @Column(name = "work_time_accounting_effective_from", nullable = false)
    private LocalDate workTimeAccountingEffectiveFrom;

    @Enumerated(EnumType.STRING)
    @Column(name = "accounting_mode", nullable = false, length = 16)
    private WorkTimeAccountingMode accountingMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_kind", nullable = false, length = 24)
    private PayrollSnapshotP15ScheduledWorkSourceKind sourceKind;

    /** Original canonical Payroll source row for the same calendar date. */
    @Column(name = "payroll_planned_minutes", nullable = false)
    private int payrollPlannedMinutes;

    @Column(name = "payroll_worked_minutes", nullable = false)
    private int payrollWorkedMinutes;

    @Column(name = "payroll_hourly_base_worked_minutes", nullable = false)
    private int payrollHourlyBaseWorkedMinutes;

    /** Schedule/fact relation frozen for future P15 use. */
    @Column(name = "schedule_minutes", nullable = false)
    private int scheduleMinutes;

    @Column(name = "planned_and_worked_minutes", nullable = false)
    private int plannedAndWorkedMinutes;

    @Column(name = "planned_not_worked_minutes", nullable = false)
    private int plannedNotWorkedMinutes;

    @Column(name = "worked_outside_plan_minutes", nullable = false)
    private int workedOutsidePlanMinutes;

    /**
     * True only when all clock identities needed for this relation were frozen
     * at source (dated shift occurrence and, when explicit, actual interval).
     */
    @Column(name = "source_identity_exact", nullable = false)
    private boolean sourceIdentityExact;

    /** Canonical comma-separated scalar IDs, never foreign keys to mutable rows. */
    @Column(name = "planned_day_entry_ids", nullable = false, columnDefinition = "text")
    private String plannedDayEntryIds;

    @Column(name = "actual_work_interval_ids", nullable = false, columnDefinition = "text")
    private String actualWorkIntervalIds;

    @Column(name = "source_fingerprint", nullable = false, length = 64)
    private String sourceFingerprint;

    protected PayrollSnapshotP15ScheduledWorkFact() {
    }

    public PayrollSnapshotP15ScheduledWorkFact(
            PayrollSnapshot snapshot,
            int factIndex,
            LocalDate sourceDate,
            long workTimeAccountingTermId,
            LocalDate workTimeAccountingEffectiveFrom,
            WorkTimeAccountingMode accountingMode,
            PayrollSnapshotP15ScheduledWorkSourceKind sourceKind,
            int payrollPlannedMinutes,
            int payrollWorkedMinutes,
            int payrollHourlyBaseWorkedMinutes,
            int scheduleMinutes,
            int plannedAndWorkedMinutes,
            int plannedNotWorkedMinutes,
            int workedOutsidePlanMinutes,
            boolean sourceIdentityExact,
            String plannedDayEntryIds,
            String actualWorkIntervalIds,
            String sourceFingerprint
    ) {
        this.snapshot = Objects.requireNonNull(snapshot, "P15 scheduled-work fact requires snapshot");
        if (factIndex < 0) {
            throw new IllegalArgumentException("P15 scheduled-work fact index must be non-negative");
        }
        this.factIndex = factIndex;
        this.sourceDate = Objects.requireNonNull(sourceDate, "P15 scheduled-work source date is required");
        if (workTimeAccountingTermId <= 0L) {
            throw new IllegalArgumentException("P15 scheduled-work mode term identity must be positive");
        }
        this.workTimeAccountingTermId = workTimeAccountingTermId;
        this.workTimeAccountingEffectiveFrom = Objects.requireNonNull(
                workTimeAccountingEffectiveFrom,
                "P15 scheduled-work mode effective date is required"
        );
        if (workTimeAccountingEffectiveFrom.isAfter(sourceDate)) {
            throw new IllegalArgumentException("P15 scheduled-work mode term cannot start after source date");
        }
        this.accountingMode = Objects.requireNonNull(accountingMode, "P15 scheduled-work accounting mode is required");
        this.sourceKind = Objects.requireNonNull(sourceKind, "P15 scheduled-work source kind is required");

        if (payrollPlannedMinutes < 0
                || payrollWorkedMinutes < 0
                || payrollHourlyBaseWorkedMinutes < 0
                || scheduleMinutes < 0
                || plannedAndWorkedMinutes < 0
                || plannedNotWorkedMinutes < 0
                || workedOutsidePlanMinutes < 0) {
            throw new IllegalArgumentException("P15 scheduled-work minute values must be non-negative");
        }
        if (payrollHourlyBaseWorkedMinutes > payrollWorkedMinutes) {
            throw new IllegalArgumentException("P15 scheduled-work Payroll base minutes cannot exceed worked minutes");
        }
        if (scheduleMinutes != plannedAndWorkedMinutes + plannedNotWorkedMinutes) {
            throw new IllegalArgumentException("P15 scheduled-work schedule relation is inconsistent");
        }

        this.payrollPlannedMinutes = payrollPlannedMinutes;
        this.payrollWorkedMinutes = payrollWorkedMinutes;
        this.payrollHourlyBaseWorkedMinutes = payrollHourlyBaseWorkedMinutes;
        this.scheduleMinutes = scheduleMinutes;
        this.plannedAndWorkedMinutes = plannedAndWorkedMinutes;
        this.plannedNotWorkedMinutes = plannedNotWorkedMinutes;
        this.workedOutsidePlanMinutes = workedOutsidePlanMinutes;
        this.sourceIdentityExact = sourceIdentityExact;
        this.plannedDayEntryIds = canonicalIds(plannedDayEntryIds, "planned DayEntry");
        this.actualWorkIntervalIds = canonicalIds(actualWorkIntervalIds, "actual-work interval");
        if (sourceFingerprint == null || !sourceFingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("P15 scheduled-work source fingerprint must be SHA-256");
        }
        this.sourceFingerprint = sourceFingerprint;
    }

    private static String canonicalIds(String value, String label) {
        String canonical = Objects.requireNonNull(value, label + " identities are required");
        if (!canonical.isEmpty() && !canonical.matches("[1-9][0-9]*(,[1-9][0-9]*)*")) {
            throw new IllegalArgumentException("P15 scheduled-work " + label + " identities are not canonical");
        }
        return canonical;
    }

    public Long getId() { return id; }
    public PayrollSnapshot getSnapshot() { return snapshot; }
    public int getFactIndex() { return factIndex; }
    public LocalDate getSourceDate() { return sourceDate; }
    public long getWorkTimeAccountingTermId() { return workTimeAccountingTermId; }
    public LocalDate getWorkTimeAccountingEffectiveFrom() { return workTimeAccountingEffectiveFrom; }
    public WorkTimeAccountingMode getAccountingMode() { return accountingMode; }
    public PayrollSnapshotP15ScheduledWorkSourceKind getSourceKind() { return sourceKind; }
    public int getPayrollPlannedMinutes() { return payrollPlannedMinutes; }
    public int getPayrollWorkedMinutes() { return payrollWorkedMinutes; }
    public int getPayrollHourlyBaseWorkedMinutes() { return payrollHourlyBaseWorkedMinutes; }
    public int getScheduleMinutes() { return scheduleMinutes; }
    public int getPlannedAndWorkedMinutes() { return plannedAndWorkedMinutes; }
    public int getPlannedNotWorkedMinutes() { return plannedNotWorkedMinutes; }
    public int getWorkedOutsidePlanMinutes() { return workedOutsidePlanMinutes; }
    public boolean isSourceIdentityExact() { return sourceIdentityExact; }
    public String getPlannedDayEntryIds() { return plannedDayEntryIds; }
    public String getActualWorkIntervalIds() { return actualWorkIntervalIds; }
    public String getSourceFingerprint() { return sourceFingerprint; }
}
