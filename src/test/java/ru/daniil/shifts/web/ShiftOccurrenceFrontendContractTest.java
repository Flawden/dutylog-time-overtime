package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ShiftOccurrenceFrontendContractTest {
    private static final Path STATIC = Path.of("src/main/resources/static");

    @Test
    void calendarUsesProjectedOccurrenceDatesInsteadOfReinterpretingTheTemplate() throws Exception {
        String calendar = Files.readString(STATIC.resolve("js/30-calendar.js"));
        String boot = Files.readString(STATIC.resolve("js/70-user-boot.js"));
        String tasks = Files.readString(STATIC.resolve("js/50-tasks.js"));

        assertTrue(calendar.contains("function occurrenceSegments"));
        assertTrue(calendar.contains("shiftSegmentsByDate"));
        assertTrue(calendar.contains("const projected = primaryShiftSegment(k)"));
        assertTrue(calendar.contains("sourceType && (!sourceType.startTime || !sourceType.endTime)"));
        assertTrue(boot.contains("bundle.shiftOccurrences.map(normalizeShiftOccurrence)"));
        assertTrue(tasks.contains("shiftSourceDateForSelected()"));
    }

    @Test
    void timezoneSettingsExposeLegacyMigrationAndForceAuthoritativeRefresh() throws Exception {
        String html = Files.readString(STATIC.resolve("index.html"));
        String settings = Files.readString(STATIC.resolve("js/60-settings.js"));
        String worker = Files.readString(STATIC.resolve("service-worker.js"));
        String boot = Files.readString(STATIC.resolve("js/70-user-boot.js"));

        assertTrue(html.contains("id=\"legacyShiftModal\""));
        assertTrue(settings.contains("api.previewLegacyShifts"));
        assertTrue(settings.contains("api.migrateLegacyShifts"));
        assertTrue(settings.contains("await loadMonth({ fresh:true })"));
        assertTrue(worker.contains(".then(() => self.clients.claim())"));
        assertTrue(worker.contains("SKIP_WAITING"));
        assertTrue(boot.contains("registration.update()"));
        assertTrue(boot.contains("controllerchange"));
    }

    @Test
    void taskDetailsRemainReadFirstAfterThePwaRefreshHardening() throws Exception {
        String tasks = Files.readString(STATIC.resolve("js/50-tasks.js"));
        assertTrue(tasks.contains("body.addEventListener(\"click\", () => openTaskDetails(task))"));
        assertTrue(tasks.contains("$(\"taskDetailsEdit\")?.addEventListener"));
    }
}
