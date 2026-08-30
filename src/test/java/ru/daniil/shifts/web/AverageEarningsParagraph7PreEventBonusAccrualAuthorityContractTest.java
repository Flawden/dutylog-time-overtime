package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AverageEarningsParagraph7PreEventBonusAccrualAuthorityContractTest {
    private static final Path SERVICE = Path.of(
            "src/main/java/ru/daniil/shifts/service/" +
                    "AverageEarningsParagraph7PreEventBonusAccrualAuthorityService.java"
    );

    @Test
    void J3B6B1ReusesCanonicalHistoricalP15DiscoveryForAccrualAuthority() throws Exception {
        String source = Files.readString(SERVICE);
        assertTrue(source.contains("AverageEarningsBonusP15HistoricalFactDiscoveryService"));
        assertTrue(source.contains("historicalDiscovery.resolve("));
        assertTrue(source.contains("snapshotPeriodMonth"));
        assertTrue(source.contains("accrualMonth"));
    }

    @Test
    void J3B6B1RequiresExactIdentityForDirectPreEventBonuses() throws Exception {
        String source = Files.readString(SERVICE);
        assertTrue(source.contains("matchesDirect("));
        assertTrue(source.contains("DIRECT_ACCRUAL_AUTHORITY_MISSING"));
        assertTrue(source.contains("FACT_IDENTITY_MISMATCH"));
        assertTrue(source.contains("bonusNatureFactId"));
        assertTrue(source.contains("bonusSourceFactId"));
        assertTrue(source.contains("bonusAverageFactId"));
    }

    @Test
    void J3B6B1HistoricalDiscoveryAddsOnlyAnnualOrServiceRewardsWithoutPreEventSource() throws Exception {
        String source = Files.readString(SERVICE);
        assertTrue(source.contains("PayrollBonusP15Nature.ANNUAL_RESULT"));
        assertTrue(source.contains("PayrollBonusP15Nature.SERVICE_LENGTH"));
        assertTrue(source.contains("HISTORICAL_ANNUAL_OR_SERVICE_DISCOVERY"));
        assertFalse(source.contains("HISTORICAL_MONTHLY_DISCOVERY"));
        assertFalse(source.contains("HISTORICAL_WORK_PERIOD_DISCOVERY"));
    }

    @Test
    void J3B6B1DoesNotImplementP15PolicyOrBonusFormula() throws Exception {
        String source = Files.readString(SERVICE);
        assertFalse(source.contains("AverageEarningsBonusP15Policy.resolve"));
        assertFalse(source.contains("AverageEarningsBonusP15Formula"));
        assertFalse(source.contains("AverageEarningsBonusP15CalculationPipelineService"));
        assertFalse(source.contains("RoundingMode"));
        assertFalse(source.contains("BigDecimal"));
    }

    @Test
    void J3B6B1DoesNotSelectParagraph8OrAggregateParagraph7Numerator() throws Exception {
        String source = Files.readString(SERVICE);
        assertFalse(source.contains("PARAGRAPH_8"));
        assertFalse(source.contains("Paragraph8"));
        assertFalse(source.contains("VacationAverageDailyEarningsFormula"));
        assertFalse(source.contains("AverageEarningsNumeratorCalculationService"));
        assertFalse(source.contains("numeratorAmountMinor"));
    }
}
