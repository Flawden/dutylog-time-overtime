package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationOvertimeSyncUxFrontendContractTest {

    @Test
    void taskReminderAllowsAnyWholeMinuteAndOvertimeUsesExplicitStartAndEnd() throws IOException {
        String html = resource("/static/index.html");
        String tasks = resource("/static/js/50-tasks.js");
        String overtime = resource("/static/js/40-overtime.js");

        assertTrue(html.contains("id=\"taskEditReminderBefore\" max=\"10080\" min=\"0\" step=\"1\""));
        assertTrue(tasks.contains("Number.isInteger(reminderMinutesBefore)"));
        assertFalse(html.contains("id=\"creditTimeRange\""));
        assertFalse(html.contains("Короткий интервал"));
        assertFalse(overtime.contains("parseManualTimeRange"));
        assertTrue(html.contains("id=\"creditStart\"") && html.contains("id=\"creditEnd\""));
    }

    @Test
    void manualSyncHasAnAccessibleProgressAndResultSurface() throws IOException {
        String modal = Files.readString(Path.of("frontend/src/app/OfflineSyncModal.vue"), StandardCharsets.UTF_8);
        String bridge = Files.readString(Path.of("frontend/src/platform/bridge/legacyBridge.ts"), StandardCharsets.UTF_8);

        assertTrue(modal.contains("id=\"offlineSyncFeedback\""));
        assertTrue(modal.contains("role=\"status\" aria-live=\"polite\""));
        assertTrue(modal.contains("syncing.value = true"));
        assertTrue(modal.contains("text.value.syncing"));
        assertTrue(modal.contains("text.value.noChanges"));
        assertTrue(modal.contains("text.value.syncComplete"));
        assertTrue(bridge.contains("async offlineSync()"));

        String css = Files.readString(Path.of("frontend/src/styles/design-system.css"), StandardCharsets.UTF_8);
        assertTrue(css.contains(".vue-offline-sync__feedback"));
        assertTrue(css.contains(".vue-shell-sync-status"));
    }

    private static String resource(String path) throws IOException {
        try (var in = NotificationOvertimeSyncUxFrontendContractTest.class.getResourceAsStream(path)) {
            if (in == null) throw new IOException("Missing classpath resource: " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
