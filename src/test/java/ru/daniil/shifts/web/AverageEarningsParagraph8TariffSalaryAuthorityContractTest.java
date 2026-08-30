package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AverageEarningsParagraph8TariffSalaryAuthorityContractTest {

    private static final Path SERVICE = Path.of(
            "src/main/java/ru/daniil/shifts/service/",
            "AverageEarningsParagraph8TariffSalaryAuthorityService.java"
    );

    @Test
    void J4AnchorsHistoricalLookupToEventMonthStart() throws Exception {
        String compact = compact(Files.readString(SERVICE));
        assertTrue(compact.contains("YearMonth eventMonth = YearMonth.from(eventDate);"));
        assertTrue(compact.contains("LocalDate compensationBoundary = eventMonth.atDay(1);"));
        assertTrue(compact.contains(
                "findFirstByOwnerAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc( user, compensationBoundary )"
        ));
        assertFalse(compact.contains("LocalDate.now("));
    }

    @Test
    void J4HourlyAuthorityUsesConfiguredTariffRateDirectly() throws Exception {
        String source = Files.readString(SERVICE);
        assertTrue(source.contains("term.getHourlyRateMinor()"));
        assertTrue(source.contains("EstablishedBasis.HOURLY_TARIFF_RATE"));
        assertFalse(source.contains("moneyForMinutes"));
    }

    @Test
    void J4SalaryAuthorityUsesConfiguredMonthlyOfficialSalaryDirectly() throws Exception {
        String source = Files.readString(SERVICE);
        assertTrue(source.contains("term.getMonthlySalaryMinor()"));
        assertTrue(source.contains("EstablishedBasis.MONTHLY_OFFICIAL_SALARY"));
        assertFalse(source.contains("productionNormMinutes"));
    }

    @Test
    void J4NeverUsesDerivedHistoricalHourlyRateAuthority() throws Exception {
        String source = Files.readString(SERVICE);
        assertFalse(source.contains("HistoricalCompensationRateService"));
        assertFalse(source.contains("CompensationCalculationService"));
        assertFalse(source.contains("effectiveHourlyRateMinor"));
        assertFalse(source.contains("ProductionCalendarService"));
    }

    @Test
    void J4DoesNotReadPayrollOrCalendarFacts() throws Exception {
        String source = Files.readString(SERVICE);
        assertFalse(source.contains("TimeCompensationService"));
        assertFalse(source.contains("PayrollSnapshot"));
        assertFalse(source.contains("PayrollHistoricalSemanticEarningsService"));
        assertFalse(source.contains("payrollSource("));
    }

    @Test
    void J4DoesNotDecideParagraph7ExhaustionOrFallbackSelection() throws Exception {
        String source = Files.readString(SERVICE);
        assertFalse(source.contains("AverageEarningsParagraph6ReferenceResolver"));
        assertFalse(source.contains("AverageEarningsParagraph7PreEvent"));
        assertFalse(source.contains("workedTimePresent"));
        assertFalse(source.contains("accruedWagePresent"));
    }

    @Test
    void J4ContainsNoAverageEarningsMoneyFormulaOrRounding() throws Exception {
        String source = Files.readString(SERVICE);
        assertFalse(source.contains("BigDecimal"));
        assertFalse(source.contains("RoundingMode"));
        assertFalse(source.contains("Math.multiplyExact"));
        assertFalse(source.contains("Math.addExact"));
    }

    @Test
    void J4BlockedAuthorityCannotExposePartialCompensationIdentity() throws Exception {
        String compact = compact(Files.readString(SERVICE));
        assertTrue(compact.contains(
                "Blocked paragraph-8 authority cannot expose partial compensation identity"
        ));
        assertTrue(compact.contains(
                "compensationEffectiveFrom != null || establishedBasis != null || payMode != null || currencyCode != null || hourlyTariffRateMinor != null || monthlyOfficialSalaryMinor != null"
        ));
    }

    @Test
    void J4CarriesExplicitPp540Paragraph8RuleIdentity() throws Exception {
        String source = Files.readString(SERVICE);
        assertTrue(source.contains("RULE_ID = \"PP_540_P8\""));
        assertTrue(source.contains("AverageEarningsLegalPolicy.requireRegime(eventDate)"));
    }

    private static String compact(String value) {
        return value.replaceAll("\\s+", " ").trim();
    }
}
