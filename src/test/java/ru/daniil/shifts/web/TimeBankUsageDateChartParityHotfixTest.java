package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Static chart-parity contract for v27.36.6. */
class TimeBankUsageDateChartParityHotfixTest {

    @Test
    void earnedSeriesUsesCanonicalProjectedDayTotals() throws IOException {
        String model = compact(read("frontend/src/features/absence-time-bank/types/model.ts"));
        String dayTotalsBlock = functionBlock(model, "export function dayCreditTotals", "export function ledgerChartColumns");
        String chartBlock = functionBlock(model, "export function ledgerChartColumns", "export function scenarioDescription");

        assertTrue(dayTotalsBlock.contains("serverProjection?.dayEarnedHours"));
        assertTrue(chartBlock.contains("for (const [date, totals] of dayCreditTotals(account.credits))"));
        assertTrue(chartBlock.contains("rowFor(date).earnedHours += totals.earned;"));
        assertFalse(chartBlock.contains("rowFor(credit.workedDate).earnedHours += Number(credit.hours ?? 0);"));
    }

    @Test
    void usedSeriesUsesActualUsageDates() throws IOException {
        String model = compact(read("frontend/src/features/absence-time-bank/types/model.ts"));
        String block = functionBlock(model, "export function ledgerChartColumns", "export function scenarioDescription");

        assertTrue(block.contains("for (const usage of account.usages)"));
        assertTrue(block.contains("rowFor(usage.usageDate).usedHours += Number(usage.hours ?? 0);"));
    }

    @Test
    void chartDoesNotDoubleCountCreditAllocationTotalsAsUsageEvents() throws IOException {
        String model = compact(read("frontend/src/features/absence-time-bank/types/model.ts"));
        String block = functionBlock(model, "export function ledgerChartColumns", "export function scenarioDescription");

        assertFalse(block.contains("credit.usedHours"));
        assertFalse(block.contains("projection?.sourceUsedHours"));
    }

    @Test
    void vitestLocksDailyUsageDateAndYearlyMonthFolding() throws IOException {
        String spec = read("frontend/src/features/absence-time-bank/types/model.spec.ts");

        assertTrue(spec.contains("plots time-bank usage on the actual usage date without double-counting credit usedHours"));
        assertTrue(spec.contains("folds earned work dates and actual usage dates into the same yearly month bucket"));
        assertTrue(spec.contains("key: \"2026-08-03\", earnedHours: 0, usedHours: 4"));
        assertTrue(spec.contains("key: \"2026-08\", earnedHours: 5, usedHours: 4"));
    }

    @Test
    void releaseKeepsTheStrictChromiumLocatorAndBackendContractUnchanged() throws IOException {
        String e2e = read("e2e/overtime-next.spec.js");
        String release = read("docs/TIME_BANK_USAGE_DATE_CHART_PARITY_HOTFIX_V27.36.6.md");

        assertTrue(e2e.contains("[data-series-key=\"${usageDate}\"]"));
        assertTrue(e2e.contains("toHaveAttribute('title', /−4/)"));
        assertTrue(release.contains("No Spring Boot, OpenAPI, PostgreSQL or Flyway change"));
    }

    private static String functionBlock(String source, String startMarker, String endMarker) {
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker, start + startMarker.length());
        assertTrue(start >= 0 && end > start);
        return source.substring(start, end);
    }

    private static String compact(String value) {
        return value.replaceAll("\\s+", " ").trim();
    }

    private static String read(String path) throws IOException {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
