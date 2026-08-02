package ru.daniil.shifts.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

/** Immutable payroll calculation for one closed accounting period and one revision. */
@Entity
@Table(name = "payroll_snapshots", uniqueConstraints =
        @UniqueConstraint(name = "uq_payroll_snapshot_revision", columnNames = {"user_id", "period_month", "revision"}),
        indexes = @Index(name = "idx_payroll_snapshots_owner_month", columnList = "user_id, period_month, revision"))
public class PayrollSnapshot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser owner;

    @Column(name = "period_month", nullable = false)
    private LocalDate periodMonth;

    @Column(nullable = false)
    private int revision;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "hourly_rate_minor", nullable = false)
    private long hourlyRateMinor;

    @Column(name = "planned_minutes", nullable = false)
    private int plannedMinutes;

    @Column(name = "worked_minutes", nullable = false)
    private int workedMinutes;

    @Column(name = "vacation_minutes", nullable = false)
    private int vacationMinutes;

    @Column(name = "sick_minutes", nullable = false)
    private int sickMinutes;

    @Column(name = "overtime_compensated_minutes", nullable = false)
    private int overtimeCompensatedMinutes;

    @Column(name = "unpaid_minutes", nullable = false)
    private int unpaidMinutes;

    @Column(name = "time_adjustment_minutes", nullable = false)
    private int timeAdjustmentMinutes;

    @Column(name = "paid_absence_minutes", nullable = false)
    private int paidAbsenceMinutes;

    @Column(name = "payable_minutes", nullable = false)
    private int payableMinutes;

    @Column(name = "base_pay_minor", nullable = false)
    private long basePayMinor;

    @Column(name = "additions_minor", nullable = false)
    private long additionsMinor;

    @Column(name = "deductions_minor", nullable = false)
    private long deductionsMinor;

    @Column(name = "total_pay_minor", nullable = false)
    private long totalPayMinor;

    @Column(name = "source_period_closed_at", nullable = false)
    private Instant sourcePeriodClosedAt;

    @Column(name = "source_integrity_checked_at", nullable = false)
    private Instant sourceIntegrityCheckedAt;

    @Column(name = "calculation_hash", nullable = false, length = 64)
    private String calculationHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "superseded_by_id")
    private PayrollSnapshot supersededBy;

    protected PayrollSnapshot() {}

    public PayrollSnapshot(AppUser owner, LocalDate periodMonth, int revision,
                           String currencyCode, long hourlyRateMinor,
                           int plannedMinutes, int workedMinutes, int vacationMinutes,
                           int sickMinutes, int overtimeCompensatedMinutes, int unpaidMinutes,
                           int timeAdjustmentMinutes, int paidAbsenceMinutes, int payableMinutes,
                           long basePayMinor, long additionsMinor, long deductionsMinor, long totalPayMinor,
                           Instant sourcePeriodClosedAt, Instant sourceIntegrityCheckedAt,
                           String calculationHash) {
        this.owner = owner;
        this.periodMonth = periodMonth.withDayOfMonth(1);
        this.revision = revision;
        this.currencyCode = currencyCode;
        this.hourlyRateMinor = hourlyRateMinor;
        this.plannedMinutes = plannedMinutes;
        this.workedMinutes = workedMinutes;
        this.vacationMinutes = vacationMinutes;
        this.sickMinutes = sickMinutes;
        this.overtimeCompensatedMinutes = overtimeCompensatedMinutes;
        this.unpaidMinutes = unpaidMinutes;
        this.timeAdjustmentMinutes = timeAdjustmentMinutes;
        this.paidAbsenceMinutes = paidAbsenceMinutes;
        this.payableMinutes = payableMinutes;
        this.basePayMinor = basePayMinor;
        this.additionsMinor = additionsMinor;
        this.deductionsMinor = deductionsMinor;
        this.totalPayMinor = totalPayMinor;
        this.sourcePeriodClosedAt = sourcePeriodClosedAt;
        this.sourceIntegrityCheckedAt = sourceIntegrityCheckedAt;
        this.calculationHash = calculationHash;
    }

    public Long getId() { return id; }
    public AppUser getOwner() { return owner; }
    public LocalDate getPeriodMonth() { return periodMonth; }
    public int getRevision() { return revision; }
    public String getCurrencyCode() { return currencyCode; }
    public long getHourlyRateMinor() { return hourlyRateMinor; }
    public int getPlannedMinutes() { return plannedMinutes; }
    public int getWorkedMinutes() { return workedMinutes; }
    public int getVacationMinutes() { return vacationMinutes; }
    public int getSickMinutes() { return sickMinutes; }
    public int getOvertimeCompensatedMinutes() { return overtimeCompensatedMinutes; }
    public int getUnpaidMinutes() { return unpaidMinutes; }
    public int getTimeAdjustmentMinutes() { return timeAdjustmentMinutes; }
    public int getPaidAbsenceMinutes() { return paidAbsenceMinutes; }
    public int getPayableMinutes() { return payableMinutes; }
    public long getBasePayMinor() { return basePayMinor; }
    public long getAdditionsMinor() { return additionsMinor; }
    public long getDeductionsMinor() { return deductionsMinor; }
    public long getTotalPayMinor() { return totalPayMinor; }
    public Instant getSourcePeriodClosedAt() { return sourcePeriodClosedAt; }
    public Instant getSourceIntegrityCheckedAt() { return sourceIntegrityCheckedAt; }
    public String getCalculationHash() { return calculationHash; }
    public Instant getCreatedAt() { return createdAt; }
    public PayrollSnapshot getSupersededBy() { return supersededBy; }
    public void supersedeWith(PayrollSnapshot replacement) { this.supersededBy = replacement; }
}
