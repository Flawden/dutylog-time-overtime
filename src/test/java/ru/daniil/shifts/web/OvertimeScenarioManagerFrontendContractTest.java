package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OvertimeScenarioManagerFrontendContractTest {

    private static String resource(String relativePath) throws IOException {
        return Files.readString(Path.of("src/main/resources/static").resolve(relativePath), StandardCharsets.UTF_8);
    }

    @Test
    void scenariosNoLongerOccupyASettingsCard() throws Exception {
        String html = resource("index.html");
        assertFalse(html.contains("data-settings-jump=\"scenarios\""));
        assertFalse(html.contains("id=\"quickScenarioSettingsCard\""));
        assertFalse(html.contains("id=\"settings-scenarios\""));
    }

    @Test
    void sharedCreditModalContainsScenarioSelectionAndSingleWindowManager() throws Exception {
        String html = resource("index.html");
        assertTrue(html.contains("id=\"creditScenarioSelect\""));
        assertTrue(html.contains("id=\"creditScenarioManage\""));
        assertTrue(html.contains("id=\"creditScenarioSaveCurrent\""));
        assertTrue(html.contains("id=\"scenarioManagerView\""));
        assertTrue(html.contains("id=\"scenarioManagerForm\""));
        assertTrue(html.contains("id=\"scenarioManagerBack\""));
    }

    @Test
    void scenarioCrudAndDraftConversionStayInsideTheOvertimeEditor() throws Exception {
        String js = resource("js/40-overtime.js");
        assertTrue(js.contains("function openScenarioManager"));
        assertTrue(js.contains("function showCreditEditorView"));
        assertTrue(js.contains("function scenarioDraftFromCreditForm"));
        assertTrue(js.contains("api.createQuickScenario(payload)"));
        assertTrue(js.contains("api.updateQuickScenario(editingId, payload)"));
        assertTrue(js.contains("openScenarioManager({ draft, origin:\"credit-draft\" })"));
    }
}
