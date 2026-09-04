package ru.daniil.shifts.model;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

@Entity
@Immutable
@Table(
        name = "regional_statutory_holiday_date_facts",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_regional_holiday_dataset_date",
                columnNames = {"dataset_id", "holiday_date"}
        ),
        indexes = @Index(
                name = "idx_regional_holiday_date_fact_lookup",
                columnList = "dataset_id,holiday_date,id"
        )
)
public class RegionalStatutoryHolidayDateFact {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dataset_id", nullable = false)
    private RegionalStatutoryHolidayDataset dataset;
    @Column(name = "holiday_date", nullable = false)
    private LocalDate holidayDate;
    @Column(name = "holiday_code", nullable = false, length = 96)
    private String holidayCode;
    @Column(name = "holiday_label", length = 240)
    private String holidayLabel;
    @Column(name = "legal_basis", nullable = false, length = 500)
    private String legalBasis;
    @Column(name = "source_reference", nullable = false, length = 1000)
    private String sourceReference;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected RegionalStatutoryHolidayDateFact() {}

    public RegionalStatutoryHolidayDateFact(
            RegionalStatutoryHolidayDataset dataset,
            LocalDate holidayDate,
            String holidayCode,
            String holidayLabel,
            String legalBasis,
            String sourceReference
    ) {
        this.dataset = Objects.requireNonNull(dataset, "Regional holiday date fact requires dataset");
        this.holidayDate = Objects.requireNonNull(holidayDate, "Regional holiday date fact requires date");
        if (!dataset.covers(holidayDate)) throw new IllegalArgumentException("Regional holiday date fact is outside dataset coverage");
        this.holidayCode = requireText(holidayCode, "Regional holiday date fact code is required");
        this.holidayLabel = cleanOptional(holidayLabel);
        this.legalBasis = requireText(legalBasis, "Regional holiday date fact legal basis is required");
        this.sourceReference = requireText(sourceReference, "Regional holiday date fact source reference is required");
    }

    public Long getId() { return id; }
    public RegionalStatutoryHolidayDataset getDataset() { return dataset; }
    public LocalDate getHolidayDate() { return holidayDate; }
    public String getHolidayCode() { return holidayCode; }
    public String getHolidayLabel() { return holidayLabel; }
    public String getLegalBasis() { return legalBasis; }
    public String getSourceReference() { return sourceReference; }
    public Instant getCreatedAt() { return createdAt; }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
        return value;
    }
    private static String cleanOptional(String value) {
        if (value == null) return null;
        String cleaned = value.trim();
        return cleaned.isEmpty() ? null : cleaned;
    }
}
