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
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false) private AppUser owner;
    @Column(name = "period_month", nullable = false) private LocalDate periodMonth;
    @Column(nullable = false) private int revision;
    @Column(name = "currency_code", nullable = false, length = 3) private String currencyCode;
    /** Effective hourly value used for explanation. In SALARY mode it is salary / production norm. */
    @Column(name = "hourly_rate_minor", nullable = false) private long hourlyRateMinor;
    @Column(name = "pay_mode", nullable = false, length = 16) private String payMode;
    @Column(name = "compensation_effective_from", nullable = false) private LocalDate compensationEffectiveFrom;
    @Column(name = "configured_hourly_rate_minor") private Long configuredHourlyRateMinor;
    @Column(name = "monthly_salary_minor") private Long monthlySalaryMinor;
    @Column(name = "production_norm_minutes", nullable = false) private int productionNormMinutes;
    @Column(name = "salary_covered_minutes", nullable = false) private int salaryCoveredMinutes;
    @Column(name = "planned_minutes", nullable = false) private int plannedMinutes;
    @Column(name = "worked_minutes", nullable = false) private int workedMinutes;
    @Column(name = "vacation_minutes", nullable = false) private int vacationMinutes;
    @Column(name = "sick_minutes", nullable = false) private int sickMinutes;
    @Column(name = "overtime_compensated_minutes", nullable = false) private int overtimeCompensatedMinutes;
    @Column(name = "unpaid_minutes", nullable = false) private int unpaidMinutes;
    @Column(name = "time_adjustment_minutes", nullable = false) private int timeAdjustmentMinutes;
    @Column(name = "paid_absence_minutes", nullable = false) private int paidAbsenceMinutes;
    @Column(name = "payable_minutes", nullable = false) private int payableMinutes;
    @Column(name = "hourly_base_payable_minutes", nullable = false) private int hourlyBasePayableMinutes;
    @Column(name = "base_pay_minor", nullable = false) private long basePayMinor;

    /*
     * Ordinary-work premium snapshot.
     *
     * referenceBase is explainability only; ordinary base is already frozen in
     * basePayMinor. ordinaryPremiumPayMinor is the additive NIGHT / HOLIDAY
     * delta. Fingerprint is nullable only when no premium pricing identity
     * participated.
     */
    @Column(name = "ordinary_premium_minutes", nullable = false)
    private int ordinaryPremiumMinutes;

    @Column(name = "ordinary_premium_reference_base_pay_minor", nullable = false)
    private long ordinaryPremiumReferenceBasePayMinor;

    @Column(name = "ordinary_premium_pay_minor", nullable = false)
    private long ordinaryPremiumPayMinor;

    @Column(name = "ordinary_premium_pricing_fingerprint", length = 64)
    private String ordinaryPremiumPricingFingerprint;

    @Column(name = "settlement_count", nullable = false) private int settlementCount;
    @Column(name = "settlement_minutes", nullable = false) private int settlementMinutes;
    @Column(name = "settlement_base_pay_minor", nullable = false) private long settlementBasePayMinor;
    @Column(name = "settlement_premium_pay_minor", nullable = false) private long settlementPremiumPayMinor;
    @Column(name = "settlement_pay_minor", nullable = false) private long settlementPayMinor;
    @Column(name = "settlement_pricing_fingerprint", length = 64) private String settlementPricingFingerprint;
    @Column(name = "additions_minor", nullable = false) private long additionsMinor;
    @Column(name = "deductions_minor", nullable = false) private long deductionsMinor;
    @Column(name = "total_pay_minor", nullable = false) private long totalPayMinor;
    @Column(name = "source_period_closed_at", nullable = false) private Instant sourcePeriodClosedAt;
    @Column(name = "source_integrity_checked_at", nullable = false) private Instant sourceIntegrityCheckedAt;
    @Column(name = "calculation_hash", nullable = false, length = 64) private String calculationHash;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt = Instant.now();
    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "superseded_by_id") private PayrollSnapshot supersededBy;

    protected PayrollSnapshot() {}

    /**
     * Source-compatible constructor for snapshots created before ordinary
     * premium immutable storage was wired into PayrollService.
     *
     * While the temporary Payroll guard is active, any premium-bearing month
     * is rejected before this constructor is reached.
     */
    public PayrollSnapshot(AppUser owner, LocalDate periodMonth, int revision,
                           String currencyCode, long hourlyRateMinor, String payMode,
                           LocalDate compensationEffectiveFrom, Long configuredHourlyRateMinor,
                           Long monthlySalaryMinor, int productionNormMinutes, int salaryCoveredMinutes,
                           int plannedMinutes, int workedMinutes, int vacationMinutes,
                           int sickMinutes, int overtimeCompensatedMinutes, int unpaidMinutes,
                           int timeAdjustmentMinutes, int paidAbsenceMinutes, int payableMinutes,
                           int hourlyBasePayableMinutes, long basePayMinor,
                           int settlementCount, int settlementMinutes,
                           long settlementBasePayMinor, long settlementPremiumPayMinor,
                           long settlementPayMinor, String settlementPricingFingerprint,
                           long additionsMinor, long deductionsMinor, long totalPayMinor,
                           Instant sourcePeriodClosedAt, Instant sourceIntegrityCheckedAt,
                           String calculationHash) {
        this(
                owner,
                periodMonth,
                revision,
                currencyCode,
                hourlyRateMinor,
                payMode,
                compensationEffectiveFrom,
                configuredHourlyRateMinor,
                monthlySalaryMinor,
                productionNormMinutes,
                salaryCoveredMinutes,
                plannedMinutes,
                workedMinutes,
                vacationMinutes,
                sickMinutes,
                overtimeCompensatedMinutes,
                unpaidMinutes,
                timeAdjustmentMinutes,
                paidAbsenceMinutes,
                payableMinutes,
                hourlyBasePayableMinutes,
                basePayMinor,
                0,
                0L,
                0L,
                null,
                settlementCount,
                settlementMinutes,
                settlementBasePayMinor,
                settlementPremiumPayMinor,
                settlementPayMinor,
                settlementPricingFingerprint,
                additionsMinor,
                deductionsMinor,
                totalPayMinor,
                sourcePeriodClosedAt,
                sourceIntegrityCheckedAt,
                calculationHash
        );
    }

    /**
     * Full immutable Payroll snapshot constructor including ordinary premium
     * time, explainability money and deep pricing identity.
     */
    public PayrollSnapshot(AppUser owner, LocalDate periodMonth, int revision,
                           String currencyCode, long hourlyRateMinor, String payMode,
                           LocalDate compensationEffectiveFrom, Long configuredHourlyRateMinor,
                           Long monthlySalaryMinor, int productionNormMinutes, int salaryCoveredMinutes,
                           int plannedMinutes, int workedMinutes, int vacationMinutes,
                           int sickMinutes, int overtimeCompensatedMinutes, int unpaidMinutes,
                           int timeAdjustmentMinutes, int paidAbsenceMinutes, int payableMinutes,
                           int hourlyBasePayableMinutes, long basePayMinor,
                           int ordinaryPremiumMinutes,
                           long ordinaryPremiumReferenceBasePayMinor,
                           long ordinaryPremiumPayMinor,
                           String ordinaryPremiumPricingFingerprint,
                           int settlementCount, int settlementMinutes,
                           long settlementBasePayMinor, long settlementPremiumPayMinor,
                           long settlementPayMinor, String settlementPricingFingerprint,
                           long additionsMinor, long deductionsMinor, long totalPayMinor,
                           Instant sourcePeriodClosedAt, Instant sourceIntegrityCheckedAt,
                           String calculationHash) {
        this.owner = owner;
        this.periodMonth = periodMonth.withDayOfMonth(1);
        this.revision = revision;
        this.currencyCode = currencyCode;
        this.hourlyRateMinor = hourlyRateMinor;
        this.payMode = payMode;
        this.compensationEffectiveFrom =
                compensationEffectiveFrom.withDayOfMonth(1);
        this.configuredHourlyRateMinor = configuredHourlyRateMinor;
        this.monthlySalaryMinor = monthlySalaryMinor;
        this.productionNormMinutes = productionNormMinutes;
        this.salaryCoveredMinutes = salaryCoveredMinutes;
        this.plannedMinutes = plannedMinutes;
        this.workedMinutes = workedMinutes;
        this.vacationMinutes = vacationMinutes;
        this.sickMinutes = sickMinutes;
        this.overtimeCompensatedMinutes = overtimeCompensatedMinutes;
        this.unpaidMinutes = unpaidMinutes;
        this.timeAdjustmentMinutes = timeAdjustmentMinutes;
        this.paidAbsenceMinutes = paidAbsenceMinutes;
        this.payableMinutes = payableMinutes;
        this.hourlyBasePayableMinutes = hourlyBasePayableMinutes;
        this.basePayMinor = basePayMinor;

        if (ordinaryPremiumMinutes < 0
                || ordinaryPremiumReferenceBasePayMinor < 0
                || ordinaryPremiumPayMinor < 0) {
            throw new IllegalArgumentException(
                    "Ordinary premium snapshot values must be non-negative"
            );
        }

        if (ordinaryPremiumMinutes == 0
                && (ordinaryPremiumReferenceBasePayMinor != 0
                || ordinaryPremiumPayMinor != 0
                || ordinaryPremiumPricingFingerprint != null)) {
            throw new IllegalArgumentException(
                    "Empty ordinary premium snapshot cannot contain money or pricing identity"
            );
        }

        if (ordinaryPremiumPricingFingerprint == null) {
            if (ordinaryPremiumPayMinor != 0) {
                throw new IllegalArgumentException(
                        "Positive ordinary premium snapshot requires pricing fingerprint"
                );
            }
        } else if (!ordinaryPremiumPricingFingerprint.matches(
                "[0-9a-f]{64}"
        )) {
            throw new IllegalArgumentException(
                    "Ordinary premium snapshot pricing fingerprint is invalid"
            );
        }

        this.ordinaryPremiumMinutes =
                ordinaryPremiumMinutes;
        this.ordinaryPremiumReferenceBasePayMinor =
                ordinaryPremiumReferenceBasePayMinor;
        this.ordinaryPremiumPayMinor =
                ordinaryPremiumPayMinor;
        this.ordinaryPremiumPricingFingerprint =
                ordinaryPremiumPricingFingerprint;

        this.settlementCount = settlementCount;
        this.settlementMinutes = settlementMinutes;
        this.settlementBasePayMinor = settlementBasePayMinor;
        this.settlementPremiumPayMinor = settlementPremiumPayMinor;
        this.settlementPayMinor = settlementPayMinor;

        if (settlementCount == 0) {
            if (settlementPricingFingerprint != null) {
                throw new IllegalArgumentException(
                        "Empty settlement snapshot cannot contain pricing fingerprint"
                );
            }
        } else if (settlementPricingFingerprint == null
                || !settlementPricingFingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(
                    "Non-empty settlement snapshot requires pricing fingerprint"
            );
        }

        this.settlementPricingFingerprint =
                settlementPricingFingerprint;
        this.additionsMinor = additionsMinor;
        this.deductionsMinor = deductionsMinor;
        this.totalPayMinor = totalPayMinor;
        this.sourcePeriodClosedAt = sourcePeriodClosedAt;
        this.sourceIntegrityCheckedAt = sourceIntegrityCheckedAt;
        this.calculationHash = calculationHash;
    }

    public Long getId() { return id; } public AppUser getOwner() { return owner; }
    public LocalDate getPeriodMonth() { return periodMonth; } public int getRevision() { return revision; }
    public String getCurrencyCode() { return currencyCode; } public long getHourlyRateMinor() { return hourlyRateMinor; }
    public String getPayMode() { return payMode; } public LocalDate getCompensationEffectiveFrom() { return compensationEffectiveFrom; }
    public Long getConfiguredHourlyRateMinor() { return configuredHourlyRateMinor; }
    public Long getMonthlySalaryMinor() { return monthlySalaryMinor; }
    public int getProductionNormMinutes() { return productionNormMinutes; }
    public int getSalaryCoveredMinutes() { return salaryCoveredMinutes; }
    public int getPlannedMinutes() { return plannedMinutes; } public int getWorkedMinutes() { return workedMinutes; }
    public int getVacationMinutes() { return vacationMinutes; } public int getSickMinutes() { return sickMinutes; }
    public int getOvertimeCompensatedMinutes() { return overtimeCompensatedMinutes; }
    public int getUnpaidMinutes() { return unpaidMinutes; } public int getTimeAdjustmentMinutes() { return timeAdjustmentMinutes; }
    public int getPaidAbsenceMinutes() { return paidAbsenceMinutes; } public int getPayableMinutes() { return payableMinutes; }
    public int getHourlyBasePayableMinutes() { return hourlyBasePayableMinutes; }
    public long getBasePayMinor() { return basePayMinor; }
    public int getOrdinaryPremiumMinutes() { return ordinaryPremiumMinutes; }
    public long getOrdinaryPremiumReferenceBasePayMinor() { return ordinaryPremiumReferenceBasePayMinor; }
    public long getOrdinaryPremiumPayMinor() { return ordinaryPremiumPayMinor; }
    public String getOrdinaryPremiumPricingFingerprint() { return ordinaryPremiumPricingFingerprint; }
    public int getSettlementCount() { return settlementCount; }
    public int getSettlementMinutes() { return settlementMinutes; }
    public long getSettlementBasePayMinor() { return settlementBasePayMinor; }
    public long getSettlementPremiumPayMinor() { return settlementPremiumPayMinor; }
    public long getSettlementPayMinor() { return settlementPayMinor; }
    public String getSettlementPricingFingerprint() { return settlementPricingFingerprint; }
    public long getAdditionsMinor() { return additionsMinor; }
    public long getDeductionsMinor() { return deductionsMinor; } public long getTotalPayMinor() { return totalPayMinor; }
    public Instant getSourcePeriodClosedAt() { return sourcePeriodClosedAt; }
    public Instant getSourceIntegrityCheckedAt() { return sourceIntegrityCheckedAt; }
    public String getCalculationHash() { return calculationHash; } public Instant getCreatedAt() { return createdAt; }
    public PayrollSnapshot getSupersededBy() { return supersededBy; }
    public void supersedeWith(PayrollSnapshot replacement) { this.supersededBy = replacement; }
}
