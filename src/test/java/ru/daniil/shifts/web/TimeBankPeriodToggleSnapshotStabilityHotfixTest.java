package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Static period-toggle snapshot contract for v27.36.7. */
class TimeBankPeriodToggleSnapshotStabilityHotfixTest {

    @Test
    void periodLoaderFetchesOnlyRangeDependentReadModels() throws IOException {
        String api = compact(read("frontend/src/features/absence-time-bank/api/absenceTimeBankApi.ts"));
        String block = functionBlock(api, "async function loadPeriod", "return Object.freeze");

        assertTrue(block.contains("timeCompensationSummary"));
        assertTrue(block.contains("inspectLedgerIntegrity"));
        assertTrue(block.contains("listActualWorkIntervals"));
        assertFalse(block.contains("overtimeAccount"));
        assertFalse(block.contains("getVacationPlanner"));
        assertFalse(block.contains("quickScenarios"));
    }

    @Test
    void fullRefreshStillLoadsTheCanonicalAccountAndPeriodProjection() throws IOException {
        String api = compact(read("frontend/src/features/absence-time-bank/api/absenceTimeBankApi.ts"));
        String block = functionBlock(api, "async load(referenceDate?", "loadPeriod,");

        assertTrue(block.contains("const accountRequest = client.request(\"overtimeAccount\")"));
        assertTrue(block.contains("const periodRequest = loadPeriod(rangeMode)"));
        assertTrue(block.contains("account: asOvertimeAccount(account)"));
        assertTrue(block.contains("...period"));
    }

    @Test
    void periodToggleNeverReplacesTheCanonicalAccountSnapshot() throws IOException {
        String store = compact(read("frontend/src/features/absence-time-bank/stores/absenceTimeBankStore.ts"));
        String block = functionBlock(store, "async setRangeMode", "openGuide(): void");

        assertTrue(block.contains("const result = await api.loadPeriod(mode)"));
        assertTrue(block.contains("this.compensation = result.compensation"));
        assertTrue(block.contains("this.integrity = result.integrity"));
        assertTrue(block.contains("this.actualWork = result.actualWork"));
        assertTrue(block.contains("this.range = result.range"));
        assertFalse(block.contains("this.account ="));
        assertFalse(block.contains("const result = await api.load(todayIso(), mode)"));
    }

    @Test
    void fullAndPeriodReadsShareOneLatestResponseSequence() throws IOException {
        String store = read("frontend/src/features/absence-time-bank/stores/absenceTimeBankStore.ts");

        assertTrue(store.contains("let readSequence = 0;"));
        assertTrue(count(store, "const sequence = ++readSequence;") >= 2);
        assertTrue(store.contains("sequence !== readSequence || this.rangeMode !== mode"));
        assertFalse(store.contains("refreshSequence"));
    }

    @Test
    void vitestLocksIdentityToggleRacesAndFullRefreshSupersession() throws IOException {
        String spec = read("frontend/src/features/absence-time-bank/stores/absenceTimeBankStore.spec.ts");

        assertTrue(spec.contains("without replacing the canonical account snapshot"));
        assertTrue(spec.contains("keeps the newest period response when month and year toggles race"));
        assertTrue(spec.contains("lets a later full refresh supersede an in-flight period-only request"));
        assertTrue(spec.contains("expect(store.account).toBe(account)"));
        assertTrue(spec.contains("expect(load).toHaveBeenCalledTimes(1)"));
    }

    @Test
    void releaseKeepsTheStrictUsageDateLocatorAndBackendContract() throws IOException {
        String e2e = read("e2e/overtime-next.spec.js");
        String release = read("docs/TIME_BANK_PERIOD_TOGGLE_SNAPSHOT_STABILITY_HOTFIX_V27.36.7.md");

        assertTrue(e2e.contains("[data-series-key=\"${usageDate}\"]"));
        assertTrue(e2e.contains("toHaveAttribute('title', /−4/)"));
        assertTrue(release.contains("No Spring Boot, OpenAPI, generated transport, PostgreSQL or Flyway change"));
    }

    private static int count(String source, String needle) {
        int count = 0;
        int index = 0;
        while ((index = source.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
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
