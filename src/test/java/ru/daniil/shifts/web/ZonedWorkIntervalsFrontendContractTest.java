package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZonedWorkIntervalsFrontendContractTest {

    @Test
    void selectedDayShowsWorkAndDisplayShiftProjection() throws Exception {
        String html = Files.readString(Path.of("src/main/resources/static/index.html"));
        String calendar = Files.readString(Path.of("src/main/resources/static/js/30-calendar.js"));
        String tasks = Files.readString(Path.of("src/main/resources/static/js/50-tasks.js"));

        assertTrue(html.contains("id=\"shiftProjection\""));
        assertTrue(calendar.contains("shiftIntervalRange"));
        assertTrue(tasks.contains("renderShiftProjection"));
        assertTrue(tasks.contains("interval.displayTimezone"));
        assertTrue(tasks.contains("interval.workTimezone"));
    }

    @Test
    void openApiAttachesShiftIntervalToDayRatherThanInbox() throws Exception {
        String openApi = Files.readString(Path.of("src/main/resources/static/openapi/dutylog-v1.yaml"));
        String inbox = openApi.substring(openApi.indexOf("    InboxItem:"), openApi.indexOf("    InboxCreateRequest:"));
        String day = openApi.substring(openApi.indexOf("    Day:"), openApi.indexOf("    MobileBootstrap:"));

        assertFalse(inbox.contains("shiftInterval"));
        assertTrue(day.contains("shiftInterval:"));
        assertTrue(day.contains("#/components/schemas/ShiftInterval"));
    }

    @Test
    void overtimeLedgerPrefersAbsoluteDisplayProjectionButKeepsWorkRange() throws Exception {
        String overtime = Files.readString(Path.of("src/main/resources/static/js/40-overtime.js"));

        assertTrue(overtime.contains("overtimeCreditDisplayRange"));
        assertTrue(overtime.contains("credit.displayStart"));
        assertTrue(overtime.contains("credit.sourceTimezone"));
        assertTrue(overtime.contains("ledgerTimeSecondary"));
        assertTrue(overtime.contains("Исходный часовой пояс"));
    }
}
