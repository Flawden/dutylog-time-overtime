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
        assertTrue(boot.contains("navigator.serviceWorker.getRegistration(\"/\")"));
        assertTrue(boot.contains("if (existingRegistration) await registration.update()"));
        assertTrue(boot.contains("controllerchange"));
        assertTrue(boot.contains("window.DutyLogPwaRuntime = Object.freeze"));
        assertTrue(boot.contains("state.profile?.onboardingCompleted === true"));
        assertTrue(boot.contains("void registerDutyLogServiceWorker()"));
        assertTrue(Files.readString(STATIC.resolve("js/20-data.js")).contains("window.DutyLogPwaRuntime?.register?.()"));
        assertTrue(!Files.readString(STATIC.resolve("js/login.js")).contains("serviceWorker.register"));
    }

    @Test
    void timezoneSettingsAndReminderEngineShareTheOccurrenceProjection() throws Exception {
        String settings = Files.readString(STATIC.resolve("js/60-settings.js"));
        String boot = Files.readString(STATIC.resolve("js/70-user-boot.js"));
        String profile = Files.readString(Path.of("src/main/java/ru/daniil/shifts/web/ProfileController.java"));
        String timezoneChange = Files.readString(Path.of("src/main/java/ru/daniil/shifts/service/WorkTimezoneChangeService.java"));
        String reminders = Files.readString(Path.of("src/main/java/ru/daniil/shifts/service/NotificationService.java"));

        assertTrue(settings.contains("function syncTimeSettingsFromBuiltins"));
        assertTrue(settings.contains("syncTimeSettingsFromBuiltins();"));
        assertTrue(settings.contains("shiftTemplateZoneHint"));
        assertTrue(boot.contains("syncTimeSettingsFromBuiltins"));
        assertTrue(profile.contains("workTimezoneChangeService.upsertAndReconcile"));
        assertTrue(timezoneChange.contains("shiftTypeService.rebaseForTimezoneChange"));
        assertTrue(reminders.contains("d.hasShiftOccurrenceSnapshot()"));
        assertTrue(reminders.contains("reminderAtInstant"));
        assertTrue(reminders.contains("displayedStart.toLocalDate()"));
    }

    @Test
    void taskDetailsRemainReadFirstAfterThePwaRefreshHardening() throws Exception {
        String tasks = Files.readString(STATIC.resolve("js/50-tasks.js"));
        assertTrue(tasks.contains("body.addEventListener(\"click\", () => openTaskDetails(task))"));
        assertTrue(tasks.contains("$(\"taskDetailsEdit\")?.addEventListener"));
    }
}
