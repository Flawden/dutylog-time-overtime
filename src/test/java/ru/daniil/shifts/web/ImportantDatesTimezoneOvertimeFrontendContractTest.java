package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImportantDatesTimezoneOvertimeFrontendContractTest {

    private static String resource(String relativePath) throws IOException {
        return Files.readString(Path.of("src/main/resources/static").resolve(relativePath), StandardCharsets.UTF_8);
    }

    @Test
    void importantDatesHaveAStandaloneWorkspaceInsteadOfASettingsCard() throws Exception {
        String html = resource("index.html");
        String js = resource("js/50-tasks.js");

        assertTrue(html.contains("data-view=\"important\""));
        assertTrue(html.contains("id=\"view-important\""));
        assertTrue(html.contains("id=\"importantBoardList\""));
        assertFalse(html.contains("id=\"importantSettingsCard\""));
        assertTrue(js.contains("function renderImportantBoard()"));
        assertTrue(js.contains("async function saveImportantBoardItem()"));
    }

    @Test
    void overtimeEditTargetsExactRowsAndOpensSharedEditorsDirectly() throws Exception {
        String html = resource("index.html");
        String js = resource("js/40-overtime.js");
        String css = resource("app.css");

        assertTrue(html.contains("id=\"overtimeCreditModal\""));
        assertTrue(html.contains("id=\"overtimeUsageModal\""));
        assertFalse(html.contains("id=\"overtimeBackToLedger\""));
        assertFalse(js.contains("openOvertimeEditorForDate"));
        assertTrue(js.contains("openAppModal(\"overtimeCreditModal\""));
        assertTrue(js.contains("openAppModal(\"overtimeUsageModal\""));
        assertTrue(js.contains("Number(state.editingCreditId) === Number(c.id)"));
        assertTrue(js.contains("usageIds.includes(Number(state.editingUsageId))"));
        assertTrue(js.contains("tr.classList.toggle(\"ledgerEditingRow\", editingCredit || editingUsage)"));
        assertTrue(css.contains(".ledgerEditingRow"));
    }

    @Test
    void timezoneIsSentThroughProfileAndUsedForBrowserToday() throws Exception {
        String core = resource("js/10-core.js");
        String settings = resource("js/60-settings.js");
        String boot = resource("js/70-user-boot.js");

        assertTrue(core.contains("dateKeyInTimeZone(state.timeSettings?.workTimezone"));
        assertTrue(settings.contains("workTimezone"));
        assertTrue(settings.contains("jfetch(\"/api/profile\""));
        assertTrue(settings.contains("workTimezone:next.workTimezone"));
        assertTrue(boot.contains("workTimezone:p.workTimezone"));
    }
}
