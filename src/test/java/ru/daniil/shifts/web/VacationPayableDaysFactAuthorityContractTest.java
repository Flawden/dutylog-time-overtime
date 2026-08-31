package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class VacationPayableDaysFactAuthorityContractTest {
    @Test
    void LOwnsArticle120PayableDayFactOnly() throws IOException {
        String source = source("src/main/java/ru/daniil/shifts/service/VacationPayableDaysFactService.java");
        assertTrue(source.contains("RULE_ID = \"TK_RF_ARTICLE_120\""));
        assertTrue(source.contains("payableDates"));
        assertTrue(source.contains("excludedHolidayDates"));
        assertTrue(source.contains("physicalSpanDates"));
    }

    @Test
    void LRequiresCanonicalAnnualVacationIdentity() throws IOException {
        String source = source("src/main/java/ru/daniil/shifts/service/VacationPayableDaysFactService.java");
        assertTrue(source.contains("VACATION.equals(type.getSystemCode())"));
        assertTrue(source.contains("VACATION_DAYS.equals(type.getBalancePolicy())"));
        assertTrue(source.contains("VACATION_ALLOWANCE.equals(period.getCompensationPolicy())"));
        assertTrue(source.contains("FULL_DAY.equals(period.getCoverage())"));
    }

    @Test
    void LRequiresPostedLedgerAuthority() throws IOException {
        String source = source("src/main/java/ru/daniil/shifts/service/VacationPayableDaysFactService.java");
        assertTrue(source.contains("ledgerIntegrity.posts(period.getStatus())"));
        assertTrue(source.contains("POSTED_STATUS_REQUIRED"));
        assertFalse(source.contains("consumesBalance(period.getStatus())"));
    }

    @Test
    void LDoesNotUseVacationPlannerCountMode() throws IOException {
        String source = source("src/main/java/ru/daniil/shifts/service/VacationPayableDaysFactService.java");
        assertFalse(source.contains("VacationSettings"));
        assertFalse(source.contains("getCountMode"));
        assertFalse(source.contains("countedByMode"));
        assertFalse(source.contains("plannedDays"));
    }

    @Test
    void LDoesNotProduceMoneyOrConsumeK() throws IOException {
        String source = source("src/main/java/ru/daniil/shifts/service/VacationPayableDaysFactService.java");
        assertFalse(source.contains("VacationAverageUnifiedDailyResolver"));
        assertFalse(source.contains("ExactMoneyPerDay"));
        assertFalse(source.contains("BigDecimal"));
        assertFalse(source.contains("RoundingMode"));
        assertFalse(source.contains("currencyCode"));
    }

    @Test
    void SharedHolidayPolicyOwnsFederalAndConfiguredHolidayTruth() throws IOException {
        String source = source("src/main/java/ru/daniil/shifts/service/AnnualPaidVacationHolidayPolicy.java");
        String reference = source("src/main/java/ru/daniil/shifts/service/VacationAverageReferenceCalendarService.java");
        assertTrue(source.contains("FEDERAL_NON_WORKING_HOLIDAYS"));
        assertTrue(source.contains("boolean holiday = HOLIDAY.equals(production.dayKind())"));
        assertTrue(source.contains("productionCalendar.resolvedDay(user, date)"));
        assertTrue(reference.contains("AnnualPaidVacationHolidayPolicy.classify("));
        assertFalse(reference.contains("FEDERAL_NON_WORKING_HOLIDAYS"));
    }

    private String source(String path) throws IOException {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
