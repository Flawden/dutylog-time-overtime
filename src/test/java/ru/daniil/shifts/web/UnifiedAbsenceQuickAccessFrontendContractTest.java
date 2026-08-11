package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnifiedAbsenceQuickAccessFrontendContractTest {

    private static String resource(String path) throws Exception {
        return Files.readString(Path.of("src/main/resources/static").resolve(path), StandardCharsets.UTF_8);
    }

    @Test
    void globalQuickAddExposesTheNeutralComposerWithoutDependingOnOvertime() throws Exception {
        String html = resource("index.html");
        String data = resource("js/20-data.js");
        String tasks = resource("js/50-tasks.js");
        String quickActions = Files.readString(Path.of("frontend/src/features/productivity/components/QuickActionsModal.vue"), StandardCharsets.UTF_8);

        assertTrue(html.contains("id=\"quickActionUsage\"")
                && html.contains("Оформить отсутствие")
                && html.contains("отпуск, отгул, больничный"));
        assertTrue(data.contains("toggle($(\"quickActionUsage\"), moduleEnabled(\"vacation\"))"));
        assertTrue(quickActions.contains("id=\"quickActionUsage\"")
                && quickActions.contains("openAbsenceComposer({ date, source: \"quick-add\" })"));
        assertTrue(tasks.contains("DutyLogVueDomains?.productivity?.openQuickActions?.(state.selected || todayKey())"));
        assertFalse(quickActions.contains("systemCode: \"TIME_OFF\""));
    }

    @Test
    void todayHasADirectAbsenceActionForTheCurrentDate() throws Exception {
        String html = resource("index.html");
        String today = resource("js/35-today.js");

        assertTrue(html.contains("id=\"todayQuickAbsence\"")
                && html.contains("<b>Оформить отсутствие</b>"));
        assertTrue(today.contains("$(\"todayQuickAbsence\").hidden = !moduleEnabled(\"vacation\")"));
        assertTrue(today.contains("openAbsenceComposer({ date:todayKey(), source:\"today\" })"));
        assertFalse(today.contains("todayQuickCredit"));
    }

    @Test
    void contextualOvertimeEntryStillPinsTimeOffAndTheGlobalFocusCanReachAbsence() throws Exception {
        String tasks = resource("js/50-tasks.js");
        String overtime = resource("js/40-overtime.js");

        assertTrue(tasks.contains("if (moduleEnabled(\"vacation\")) return \"quickActionUsage\";"));
        assertTrue(overtime.contains("systemCode:\"TIME_OFF\"")
                && overtime.contains("source:\"overtime\""));
    }
}
