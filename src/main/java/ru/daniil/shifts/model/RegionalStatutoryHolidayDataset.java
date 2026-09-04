package ru.daniil.shifts.model;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

@Entity
@Immutable
@Table(
        name = "regional_statutory_holiday_datasets",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_regional_holiday_dataset_fingerprint",
                columnNames = {"fingerprint"}
        ),
        indexes = @Index(
                name = "idx_regional_holiday_dataset_lookup",
                columnList = "jurisdiction_code,region_code,coverage_from,coverage_to,id"
        )
)
public class RegionalStatutoryHolidayDataset {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "jurisdiction_code", nullable = false, length = 16)
    private String jurisdictionCode;
    @Column(name = "region_code", nullable = false, length = 32)
    private String regionCode;
    @Column(name = "coverage_from", nullable = false)
    private LocalDate coverageFrom;
    @Column(name = "coverage_to", nullable = false)
    private LocalDate coverageTo;
    @Column(name = "legal_regime", nullable = false, length = 160)
    private String legalRegime;
    @Column(name = "legal_basis", nullable = false, length = 500)
    private String legalBasis;
    @Column(name = "source_revision", nullable = false, length = 240)
    private String sourceRevision;
    @Column(name = "source_reference", nullable = false, length = 1000)
    private String sourceReference;
    @Column(name = "complete", nullable = false)
    private boolean complete;
    @Column(name = "fingerprint", nullable = false, length = 64)
    private String fingerprint;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected RegionalStatutoryHolidayDataset() {}

    public RegionalStatutoryHolidayDataset(
            String jurisdictionCode,
            String regionCode,
            LocalDate coverageFrom,
            LocalDate coverageTo,
            String legalRegime,
            String legalBasis,
            String sourceRevision,
            String sourceReference,
            boolean complete,
            String fingerprint
    ) {
        this.jurisdictionCode = requireText(jurisdictionCode, "Regional holiday dataset jurisdiction is required");
        this.regionCode = requireText(regionCode, "Regional holiday dataset region is required");
        this.coverageFrom = Objects.requireNonNull(coverageFrom, "Regional holiday dataset coverage start is required");
        this.coverageTo = Objects.requireNonNull(coverageTo, "Regional holiday dataset coverage end is required");
        if (coverageTo.isBefore(coverageFrom)) throw new IllegalArgumentException("Regional holiday dataset coverage is reversed");
        this.legalRegime = requireText(legalRegime, "Regional holiday dataset legal regime is required");
        this.legalBasis = requireText(legalBasis, "Regional holiday dataset legal basis is required");
        this.sourceRevision = requireText(sourceRevision, "Regional holiday dataset source revision is required");
        this.sourceReference = requireText(sourceReference, "Regional holiday dataset source reference is required");
        this.complete = complete;
        if (fingerprint == null || !fingerprint.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Regional holiday dataset fingerprint must be lowercase SHA-256");
        this.fingerprint = fingerprint;
    }

    public Long getId() { return id; }
    public String getJurisdictionCode() { return jurisdictionCode; }
    public String getRegionCode() { return regionCode; }
    public LocalDate getCoverageFrom() { return coverageFrom; }
    public LocalDate getCoverageTo() { return coverageTo; }
    public String getLegalRegime() { return legalRegime; }
    public String getLegalBasis() { return legalBasis; }
    public String getSourceRevision() { return sourceRevision; }
    public String getSourceReference() { return sourceReference; }
    public boolean isComplete() { return complete; }
    public String getFingerprint() { return fingerprint; }
    public Instant getCreatedAt() { return createdAt; }
    public boolean covers(LocalDate date) {
        Objects.requireNonNull(date, "Regional holiday dataset coverage check requires date");
        return !date.isBefore(coverageFrom) && !date.isAfter(coverageTo);
    }
    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
        return value;
    }
}
