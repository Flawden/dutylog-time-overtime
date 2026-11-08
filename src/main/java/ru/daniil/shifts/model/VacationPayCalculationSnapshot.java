package ru.daniil.shifts.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Immutable, versioned calculation evidence for one annual-paid-vacation absence.
 * Multiple actual transfers may reference the same frozen calculation snapshot.
 */
@Entity
@Table(name = "vacation_pay_calculation_snapshots")
public class VacationPayCalculationSnapshot {

    public static final String CALCULATION_NOT_REQUESTED = "NOT_REQUESTED";
    public static final String CALCULATION_READY = "READY";
    public static final String CALCULATION_BLOCKED = "BLOCKED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private AppUser owner;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "absence_period_id", nullable = false, updatable = false)
    private AbsencePeriod absencePeriod;

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

    @Column(name = "calculation_status", nullable = false, length = 20, updatable = false)
    private String calculationStatus;

    @Column(name = "calculated_gross_amount_minor", updatable = false)
    private Long calculatedGrossAmountMinor;

    @Column(name = "payable_calendar_days", updatable = false)
    private Integer payableCalendarDays;

    @Column(name = "calculation_schema_version", length = 60, updatable = false)
    private String calculationSchemaVersion;

    /** Currency of the optional gross calculation, before withholding. */
    @Column(name = "calculation_currency_code", length = 3, updatable = false)
    private String calculationCurrencyCode;

    @Column(name = "calculation_payload_json", columnDefinition = "TEXT", updatable = false)
    private String calculationPayloadJson;

    @Column(name = "calculation_blocking_reason", length = 160, updatable = false)
    private String calculationBlockingReason;

    @Column(name = "snapshot_fingerprint", nullable = false, length = 64, updatable = false)
    private String snapshotFingerprint;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected VacationPayCalculationSnapshot() {
    }

    public VacationPayCalculationSnapshot(
            AppUser owner,
            AbsencePeriod absencePeriod,
            String absenceStatus,
            String absenceTypeSystemCode,
            String absenceTypeBalancePolicy,
            String absenceCoverage,
            String absenceCompensationPolicy,
            LocalDate calculationEventDate,
            LocalDate vacationFrom,
            LocalDate vacationTo,
            String calculationStatus,
            Long calculatedGrossAmountMinor,
            Integer payableCalendarDays,
            String calculationSchemaVersion,
            String calculationCurrencyCode,
            String calculationPayloadJson,
            String calculationBlockingReason,
            String snapshotFingerprint
    ) {
        this.owner = Objects.requireNonNull(owner, "Calculation snapshot owner is required");
        this.absencePeriod = Objects.requireNonNull(
                absencePeriod,
                "Calculation snapshot absence period is required"
        );
        this.absenceStatus = requireText(absenceStatus, "Absence status is required");
        this.absenceTypeSystemCode = requireText(absenceTypeSystemCode, "Absence system code is required");
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
            throw new IllegalArgumentException("Calculation snapshot must reference canonical annual paid vacation");
        }
        this.calculationEventDate = Objects.requireNonNull(
                calculationEventDate,
                "Calculation event date is required"
        );
        this.vacationFrom = Objects.requireNonNull(vacationFrom, "Vacation start date is required");
        this.vacationTo = Objects.requireNonNull(vacationTo, "Vacation end date is required");
        if (vacationTo.isBefore(vacationFrom) || !calculationEventDate.equals(vacationFrom)) {
            throw new IllegalArgumentException("Calculation snapshot vacation identity is inconsistent");
        }
        this.calculationStatus = requireText(calculationStatus, "Calculation status is required");
        this.calculatedGrossAmountMinor = calculatedGrossAmountMinor;
        this.payableCalendarDays = payableCalendarDays;
        this.calculationSchemaVersion = calculationSchemaVersion;
        this.calculationCurrencyCode = calculationCurrencyCode;
        this.calculationPayloadJson = calculationPayloadJson;
        this.calculationBlockingReason = calculationBlockingReason;
        validateCalculationState();
        if (snapshotFingerprint == null || !snapshotFingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Snapshot fingerprint must be a lowercase SHA-256 digest");
        }
        this.snapshotFingerprint = snapshotFingerprint;
        this.createdAt = Instant.now();
    }

    private void validateCalculationState() {
        boolean schemaPresent = calculationSchemaVersion != null && !calculationSchemaVersion.isBlank();
        boolean payloadPresent = calculationPayloadJson != null && !calculationPayloadJson.isBlank();
        boolean blockerPresent = calculationBlockingReason != null && !calculationBlockingReason.isBlank();

        switch (calculationStatus) {
            case CALCULATION_BLOCKED -> {
                if (calculatedGrossAmountMinor != null || payableCalendarDays != null
                        || !schemaPresent || calculationCurrencyCode != null
                        || !payloadPresent || !blockerPresent) {
                    throw new IllegalArgumentException("Blocked calculation snapshot is incomplete");
                }
            }
            case CALCULATION_READY -> {
                if (calculatedGrossAmountMinor == null || calculatedGrossAmountMinor < 0L
                        || payableCalendarDays == null || payableCalendarDays < 0
                        || !schemaPresent || calculationCurrencyCode == null
                        || !calculationCurrencyCode.matches("[A-Z]{3}")
                        || !payloadPresent || blockerPresent) {
                    throw new IllegalArgumentException("Ready calculation snapshot is incomplete");
                }
            }
            default -> throw new IllegalArgumentException("Unsupported calculation snapshot status");
        }
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
    public String getAbsenceStatus() { return absenceStatus; }
    public String getAbsenceTypeSystemCode() { return absenceTypeSystemCode; }
    public String getAbsenceTypeBalancePolicy() { return absenceTypeBalancePolicy; }
    public String getAbsenceCoverage() { return absenceCoverage; }
    public String getAbsenceCompensationPolicy() { return absenceCompensationPolicy; }
    public LocalDate getCalculationEventDate() { return calculationEventDate; }
    public LocalDate getVacationFrom() { return vacationFrom; }
    public LocalDate getVacationTo() { return vacationTo; }
    public String getCalculationStatus() { return calculationStatus; }
    public Long getCalculatedGrossAmountMinor() { return calculatedGrossAmountMinor; }
    public Integer getPayableCalendarDays() { return payableCalendarDays; }
    public String getCalculationSchemaVersion() { return calculationSchemaVersion; }
    public String getCalculationCurrencyCode() { return calculationCurrencyCode; }
    public String getCalculationPayloadJson() { return calculationPayloadJson; }
    public String getCalculationBlockingReason() { return calculationBlockingReason; }
    public String getSnapshotFingerprint() { return snapshotFingerprint; }
    public Instant getCreatedAt() { return createdAt; }
}
