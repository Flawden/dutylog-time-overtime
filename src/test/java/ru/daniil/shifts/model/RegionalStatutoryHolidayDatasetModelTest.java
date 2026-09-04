package ru.daniil.shifts.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class RegionalStatutoryHolidayDatasetModelTest {
    private static final String SHA = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    @Test
    void datasetCoverageIncludesBothBoundaries() {
        RegionalStatutoryHolidayDataset dataset = dataset(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        assertTrue(dataset.covers(LocalDate.of(2026, 1, 1)));
        assertTrue(dataset.covers(LocalDate.of(2026, 12, 31)));
        assertFalse(dataset.covers(LocalDate.of(2025, 12, 31)));
        assertFalse(dataset.covers(LocalDate.of(2027, 1, 1)));
        assertNotNull(dataset.getCreatedAt());
    }

    @Test
    void datasetRejectsReversedCoverage() {
        assertThrows(IllegalArgumentException.class,
                () -> dataset(LocalDate.of(2026, 12, 31), LocalDate.of(2026, 1, 1)));
    }

    @Test
    void datasetRejectsInvalidFingerprintAndBlankIdentity() {
        assertThrows(IllegalArgumentException.class,
                () -> new RegionalStatutoryHolidayDataset("RU", "RU-KYA", LocalDate.of(2026,1,1), LocalDate.of(2026,12,31),
                        "REGIME", "BASIS", "REVISION", "SOURCE", true, "ABC"));
        assertThrows(IllegalArgumentException.class,
                () -> new RegionalStatutoryHolidayDataset(" ", "RU-KYA", LocalDate.of(2026,1,1), LocalDate.of(2026,12,31),
                        "REGIME", "BASIS", "REVISION", "SOURCE", true, SHA));
    }

    @Test
    void dateFactRejectsOutsideCoverage() {
        RegionalStatutoryHolidayDataset dataset = dataset(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        assertThrows(IllegalArgumentException.class,
                () -> new RegionalStatutoryHolidayDateFact(dataset, LocalDate.of(2027,1,1), "HOLIDAY", null, "BASIS", "SOURCE"));
    }

    @Test
    void dateFactNormalizesBlankOptionalLabelToNull() {
        RegionalStatutoryHolidayDataset dataset = dataset(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        RegionalStatutoryHolidayDateFact fact = new RegionalStatutoryHolidayDateFact(
                dataset, LocalDate.of(2026,6,24), "HOLIDAY", "   ", "BASIS", "SOURCE");
        assertNull(fact.getHolidayLabel());
        assertEquals("HOLIDAY", fact.getHolidayCode());
        assertSame(dataset, fact.getDataset());
        assertNotNull(fact.getCreatedAt());
    }

    @Test
    void dateFactRejectsBlankRequiredFields() {
        RegionalStatutoryHolidayDataset dataset = dataset(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        assertThrows(IllegalArgumentException.class,
                () -> new RegionalStatutoryHolidayDateFact(dataset, LocalDate.of(2026,6,24), " ", null, "BASIS", "SOURCE"));
    }

    private RegionalStatutoryHolidayDataset dataset(LocalDate from, LocalDate to) {
        return new RegionalStatutoryHolidayDataset("RU", "RU-KYA", from, to, "REGIME", "BASIS", "REVISION", "SOURCE", true, SHA);
    }
}
