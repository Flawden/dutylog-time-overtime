package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OvertimeIntervalEngineFrontendContractTest {

    private static String resource(String relative) throws Exception {
        return Files.readString(Path.of("src/main/resources/static").resolve(relative));
    }

    @Test
    void settingsExposeOneCanonicalTimezoneAndMirrorLegacyWireField() throws Exception {
        String html = resource("index.html");
        String core = resource("js/10-core.js");
        String settings = resource("js/60-settings.js");

        assertTrue(html.contains("id=\"workTimezone\""));
        assertTrue(html.contains("id=\"displayTimezone\" type=\"hidden\""));
        assertFalse(html.contains("id=\"timeDisplayAsWork\""));
        assertTrue(core.contains("merged.displayTimezone = merged.workTimezone"));
        assertTrue(settings.contains("displayTimezone:next.workTimezone"));
    }

    @Test
    void ledgerShowsExactFifoRangesAndReconstructionState() throws Exception {
        String overtime = resource("js/40-overtime.js");
        String css = resource("app.css");

        assertTrue(overtime.contains("function allocationRangeLabels(allocation)"));
        assertTrue(overtime.contains("24:00"));
        assertTrue(overtime.contains("function allocationDetailHtml(allocation)"));
        assertTrue(overtime.contains("allocation.reconstructed"));
        assertTrue(overtime.contains("allocation.displayStart"));
        assertTrue(overtime.contains("allocation.displayEnd"));
        assertTrue(css.contains(".allocationRange"));
        assertTrue(css.contains(".allocationReconstructed"));
    }

    @Test
    void exactAllocationRangesUseTheExistingHumanDateFormatter() throws Exception {
        String overtime = resource("js/40-overtime.js");

        assertTrue(overtime.contains("formatDateHuman(startDate)"));
        assertTrue(overtime.contains("formatDateHuman(endDate)"));
        assertFalse(overtime.contains("formatDate(startDate)"));
        assertFalse(overtime.contains("formatDate(endDate)"));
    }

    @Test
    void legacyMigrationWizardHasPreviewSelectionAndApplyFlow() throws Exception {
        String html = resource("index.html");
        String data = resource("js/20-data.js");
        String overtime = resource("js/40-overtime.js");

        assertTrue(html.contains("id=\"ledgerMigrateLegacy\""));
        assertTrue(html.contains("id=\"legacyOvertimeModal\""));
        assertTrue(html.contains("id=\"legacyOvertimeTimezone\""));
        assertTrue(html.contains("id=\"legacyOvertimeApply\""));
        assertTrue(data.contains("previewLegacyOvertime"));
        assertTrue(data.contains("migrateLegacyOvertime"));
        assertTrue(overtime.contains("refreshLegacyMigrationPreview"));
        assertTrue(overtime.contains("applyLegacyOvertimeMigration"));
    }

    @Test
    void shiftProjectionExplainsNetWorkAndBreakSeparately() throws Exception {
        String tasks = resource("js/50-tasks.js");

        assertTrue(tasks.contains("interval.netMinutes"));
        assertTrue(tasks.contains("interval.breakMinutes"));
        assertTrue(tasks.contains("Рабочее время смены"));
        assertTrue(tasks.contains("Обед в смене"));
        assertFalse(tasks.contains("Фактическая длительность"));
    }

    @Test
    void openApiDocumentsExactMinutesAndMigrationEndpoints() throws Exception {
        String api = resource("openapi/dutylog-v1.yaml");

        assertTrue(api.contains("/api/v1/overtime/legacy-credits/preview:"));
        assertTrue(api.contains("/api/v1/overtime/legacy-credits/migrate:"));
        assertTrue(api.contains("OvertimeAllocation:"));
        assertTrue(api.contains("creditedMinutes:"));
        assertTrue(api.contains("reconstructed:"));
    }
}
