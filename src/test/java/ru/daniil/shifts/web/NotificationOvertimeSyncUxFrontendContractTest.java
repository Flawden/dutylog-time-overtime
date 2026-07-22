package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

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
        String html = resource("/static/index.html");
        String data = resource("/static/js/20-data.js");

        assertTrue(html.contains("id=\"offlineSyncFeedback\" role=\"status\" aria-live=\"polite\""));
        assertTrue(data.contains("setOfflineSyncButtonBusy(true)"));
        assertTrue(data.contains("setOfflineSyncFeedback(t(\"Синхронизация…\")"));
        assertTrue(data.contains("setOfflineSyncFeedback(t(\"Нет изменений\")"));
        assertTrue(data.contains("setOfflineSyncFeedback(t(\"Синхронизация завершена\")"));
    }

    private static String resource(String path) throws IOException {
        try (var in = NotificationOvertimeSyncUxFrontendContractTest.class.getResourceAsStream(path)) {
            if (in == null) throw new IOException("Missing classpath resource: " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
