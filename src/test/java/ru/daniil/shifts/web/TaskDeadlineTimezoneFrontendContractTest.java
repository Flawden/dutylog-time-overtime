package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskDeadlineTimezoneFrontendContractTest {
    private static final Path STATIC = Path.of("src/main/resources/static");

    @Test
    void taskDetailsExposeProjectedAndSourceDeadlines() throws Exception {
        String tasks = Files.readString(STATIC.resolve("js/50-tasks.js"));
        String dto = Files.readString(Path.of("src/main/java/ru/daniil/shifts/dto/Dtos.java"));

        assertTrue(tasks.contains("task.deadlineAbsolute"));
        assertTrue(tasks.contains("task.dueSourceTimezone"));
        assertTrue(tasks.contains("taskDetailsFact(\"Исходный срок\""));
        assertTrue(dto.contains("boolean deadlineAbsolute"));
        assertTrue(dto.contains("String dueSourceTimezone"));
    }

    @Test
    void timezoneSaveRefreshesTasksAndAbsoluteReminderProjection() throws Exception {
        String settings = Files.readString(STATIC.resolve("js/60-settings.js"));
        String profile = Files.readString(Path.of("src/main/java/ru/daniil/shifts/web/ProfileController.java"));
        String timezoneChange = Files.readString(Path.of("src/main/java/ru/daniil/shifts/service/WorkTimezoneChangeService.java"));
        String reminders = Files.readString(Path.of("src/main/java/ru/daniil/shifts/service/NotificationService.java"));

        assertTrue(settings.contains("await loadTaskBoard(true)"));
        assertTrue(settings.contains("await showMonthNotifications()"));
        assertTrue(profile.contains("workTimezoneChangeService.upsertAndReconcile"));
        assertTrue(timezoneChange.contains("taskService.rebaseForTimezoneChange"));
        assertTrue(reminders.contains("task.getDueInstant()"));
        assertTrue(reminders.contains("reminderAtInstant"));
    }

    @Test
    void legacyTimedTaskWizardRequiresAnExplicitSourceTimezone() throws Exception {
        String html = Files.readString(STATIC.resolve("index.html"));
        String data = Files.readString(STATIC.resolve("js/20-data.js"));
        String settings = Files.readString(STATIC.resolve("js/60-settings.js"));

        assertTrue(html.contains("id=\"legacyTaskDeadlineModal\""));
        assertTrue(html.contains("id=\"legacyTaskDeadlineTimezone\""));
        assertTrue(data.contains("previewLegacyTaskDeadlines"));
        assertTrue(data.contains("migrateLegacyTaskDeadlines"));
        assertTrue(settings.contains("refreshLegacyTaskDeadlineIndicator"));
        assertTrue(settings.contains("sourceTimezone, taskIds:ids"));
    }
}
