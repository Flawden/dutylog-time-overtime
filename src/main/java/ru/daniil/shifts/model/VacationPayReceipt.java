package ru.daniil.shifts.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Immutable record of one actual incoming vacation-pay bank transfer.
 * Calculation evidence is stored separately and may be shared by many transfers.
 */
@Entity
@Table(name = "vacation_pay_receipts")
public class VacationPayReceipt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private AppUser owner;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "absence_period_id", nullable = false, updatable = false)
    private AbsencePeriod absencePeriod;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "calculation_snapshot_id", updatable = false)
    private VacationPayCalculationSnapshot calculationSnapshot;

    @Column(name = "received_date", nullable = false, updatable = false)
    private LocalDate receivedDate;

    @Column(name = "actual_received_amount_minor", nullable = false, updatable = false)
    private long actualReceivedAmountMinor;

    /** Currency of the actual net amount credited to the user's account. */
    @Column(name = "currency_code", nullable = false, length = 3, updatable = false)
    private String currencyCode;

    @Column(name = "absence_status", nullable = false, length = 20, updatable = false)
    private String absenceStatus;

    @Column(name = "absence_type_system_code", nullable = false, length = 30, updatable = false)
    private String absenceTypeSystemCode;

    @Column(name = "absence_type_balance_policy", nullable = false, length = 30, updatable = false)
    private String absenceTypeBalancePolicy;

    @Column(name = "absence_coverage", nullable = false, length = 20, updatable = false)
    private String absenceCoverage;

    @Column(name = "absence_compensation_policy", nullable = false, length = 30, updatable = false)
    private String absenceCompensationPolicy;

    @Column(name = "calculation_event_date", nullable = false, updatable = false)
    private LocalDate calculationEventDate;

    @Column(name = "vacation_from", nullable = false, updatable = false)
    private LocalDate vacationFrom;

    @Column(name = "vacation_to", nullable = false, updatable = false)
    private LocalDate vacationTo;

    @Column(name = "receipt_fingerprint", nullable = false, length = 64, updatable = false)
    private String receiptFingerprint;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected VacationPayReceipt() {
    }

    public VacationPayReceipt(
            AppUser owner,
            AbsencePeriod absencePeriod,
            VacationPayCalculationSnapshot calculationSnapshot,
            LocalDate receivedDate,
            long actualReceivedAmountMinor,
            String currencyCode,
            String absenceStatus,
            String absenceTypeSystemCode,
            String absenceTypeBalancePolicy,
            String absenceCoverage,
            String absenceCompensationPolicy,
            LocalDate calculationEventDate,
            LocalDate vacationFrom,
            LocalDate vacationTo,
            String receiptFingerprint
    ) {
        this.owner = Objects.requireNonNull(owner, "Vacation receipt owner is required");
        this.absencePeriod = Objects.requireNonNull(
                absencePeriod,
                "Vacation receipt absence period is required"
        );
        this.calculationSnapshot = calculationSnapshot;
        this.receivedDate = Objects.requireNonNull(receivedDate, "Vacation receipt date is required");
        if (actualReceivedAmountMinor <= 0L) {
            throw new IllegalArgumentException("Actual received amount must be positive");
        }
        this.actualReceivedAmountMinor = actualReceivedAmountMinor;
        if (currencyCode == null || !currencyCode.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("Receipt currency must be a three-letter uppercase code");
        }
        this.currencyCode = currencyCode;
        this.absenceStatus = requireText(absenceStatus, "Absence status is required");
        this.absenceTypeSystemCode = requireText(
                absenceTypeSystemCode,
                "Absence system code is required"
        );
        this.absenceTypeBalancePolicy = requireText(
                absenceTypeBalancePolicy,
                "Absence balance policy is required"
        );
        this.absenceCoverage = requireText(absenceCoverage, "Absence coverage is required");
        this.absenceCompensationPolicy = requireText(
                absenceCompensationPolicy,
                "Absence compensation policy is required"
        );
        if (!("APPROVED".equals(this.absenceStatus) || "COMPLETED".equals(this.absenceStatus))
                || !"VACATION".equals(this.absenceTypeSystemCode)
                || !"VACATION_DAYS".equals(this.absenceTypeBalancePolicy)
                || !"FULL_DAY".equals(this.absenceCoverage)
                || !"VACATION_ALLOWANCE".equals(this.absenceCompensationPolicy)) {
            throw new IllegalArgumentException(
                    "Receipt must reference canonical approved annual paid vacation"
            );
        }
        this.calculationEventDate = Objects.requireNonNull(
                calculationEventDate,
                "Vacation event date is required"
        );
        this.vacationFrom = Objects.requireNonNull(vacationFrom, "Vacation start date is required");
        this.vacationTo = Objects.requireNonNull(vacationTo, "Vacation end date is required");
        if (vacationTo.isBefore(vacationFrom) || !calculationEventDate.equals(vacationFrom)) {
            throw new IllegalArgumentException("Vacation receipt identity is inconsistent");
        }
        if (calculationSnapshot != null
                && (!Objects.equals(calculationSnapshot.getAbsencePeriod().getId(), absencePeriod.getId())
                || !Objects.equals(calculationSnapshot.getOwner().getId(), owner.getId())
                || !Objects.equals(calculationSnapshot.getCalculationEventDate(), calculationEventDate)
                || !Objects.equals(calculationSnapshot.getVacationFrom(), vacationFrom)
                || !Objects.equals(calculationSnapshot.getVacationTo(), vacationTo))) {
            throw new IllegalArgumentException("Calculation snapshot belongs to a different vacation identity");
        }
        if (receiptFingerprint == null || !receiptFingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Receipt fingerprint must be a lowercase SHA-256 digest");
        }
        this.receiptFingerprint = receiptFingerprint;
        this.createdAt = Instant.now();
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
        return value;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public AppUser getOwner() { return owner; }
    public AbsencePeriod getAbsencePeriod() { return absencePeriod; }
    public VacationPayCalculationSnapshot getCalculationSnapshot() { return calculationSnapshot; }
    public LocalDate getReceivedDate() { return receivedDate; }
    public long getActualReceivedAmountMinor() { return actualReceivedAmountMinor; }
    public String getCurrencyCode() { return currencyCode; }
    public String getAbsenceStatus() { return absenceStatus; }
    public String getAbsenceTypeSystemCode() { return absenceTypeSystemCode; }
    public String getAbsenceTypeBalancePolicy() { return absenceTypeBalancePolicy; }
    public String getAbsenceCoverage() { return absenceCoverage; }
    public String getAbsenceCompensationPolicy() { return absenceCompensationPolicy; }
    public LocalDate getCalculationEventDate() { return calculationEventDate; }
    public LocalDate getVacationFrom() { return vacationFrom; }
    public LocalDate getVacationTo() { return vacationTo; }
    public String getReceiptFingerprint() { return receiptFingerprint; }
    public Instant getCreatedAt() { return createdAt; }
}
