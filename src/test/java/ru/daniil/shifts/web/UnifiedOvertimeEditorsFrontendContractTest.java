package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnifiedOvertimeEditorsFrontendContractTest {

    private static String resource(String relativePath) throws IOException {
        return Files.readString(Path.of("src/main/resources/static").resolve(relativePath), StandardCharsets.UTF_8);
    }

    @Test
    void selectedDayUsesCompactActionsInsteadOfTheLegacyInlineForm() throws Exception {
        String html = resource("index.html");

        assertTrue(html.contains("id=\"dayAddCredit\""));
        assertTrue(html.contains("id=\"dayAddUsage\""));
        assertTrue(html.contains("id=\"otDayDetails\""));
        assertFalse(html.contains("class=\"quickScenarioPanel\""));
    }

    @Test
    void calendarAndLedgerRouteNewUsageThroughTheUnifiedAbsenceComposer() throws Exception {
        String html = resource("index.html");
        String js = resource("js/40-overtime.js");

        assertTrue(html.contains("id=\"ledgerAddCredit\""));
        assertTrue(html.contains("id=\"ledgerAddUsage\""));
        assertTrue(html.contains("id=\"overtimeCreditForm\""));
        assertFalse(html.contains("id=\"overtimeUsageForm\""));
        assertTrue(html.contains("id=\"legacyUsageMigrationModal\""));
        assertTrue(js.contains("openOvertimeCreditModal(state.selected)"));
        assertTrue(js.contains("openOvertimeUsageModal(state.selected)"));
        assertTrue(js.contains("openOvertimeCreditModal(state.selected || todayKey())"));
        assertTrue(js.contains("openOvertimeUsageModal(state.selected || todayKey())"));
        assertTrue(js.contains("openAbsenceComposer({ date:overtimeDefaultDate(date), systemCode:\"TIME_OFF\", source:\"overtime\" })"));
        assertTrue(html.contains("id=\"absenceComposerModal\""));
    }

    @Test
    void creditUsesScenarioDropdownAndLegacyUsagesExposeMigrationPreview() throws Exception {
        String html = resource("index.html");
        String js = resource("js/40-overtime.js");
        String css = resource("app.css");

        assertTrue(html.contains("id=\"creditScenarioSelect\""));
        assertTrue(html.contains("id=\"legacyUsageMigrationList\""));
        assertTrue(html.contains("id=\"legacyUsageMigrationApply\""));
        assertTrue(js.contains("function renderLegacyUsageMigrationPreview()"));
        assertTrue(js.contains("api.previewLegacyOvertimeUsages"));
        assertTrue(js.contains("function renderQuickScenarios()"));
        assertTrue(css.contains("body.app-modal-open .tabbar"));
    }
}
