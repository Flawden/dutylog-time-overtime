package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskAndShiftEditorsFrontendContractTest {

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }

    @Test
    void taskEditingUsesOneStructuredModalInsteadOfPromptChain() throws Exception {
        String html = read("src/main/resources/static/index.html");
        String tasks = read("src/main/resources/static/js/50-tasks.js");

        assertTrue(html.contains("id=\"taskEditModal\""));
        assertTrue(html.contains("id=\"taskEditText\""));
        assertTrue(html.contains("id=\"taskEditPriority\""));
        assertTrue(html.contains("id=\"taskEditReminderEnabled\""));
        assertTrue(html.contains("id=\"taskEditDueTime\" type=\"time\" step=\"60\""));
        assertTrue(tasks.contains("openAppModal(\"taskEditModal\""));
        assertTrue(tasks.contains("async function saveTaskEditor()"));
        assertFalse(tasks.contains("prompt(\"Текст задачи\""));
        assertFalse(tasks.contains("confirm(t(\"Включить напоминание для этой задачи?\"))"));
    }

    @Test
    void subtasksStayInsideTaskEditorAndUseCompactInlineProgress() throws Exception {
        String html = read("src/main/resources/static/index.html");
        String tasks = read("src/main/resources/static/js/50-tasks.js");
        String data = read("src/main/resources/static/js/20-data.js");
        String css = read("src/main/resources/static/app.css");
        String migration = read("src/main/resources/db/migration/postgresql/V27__task_subtasks.sql");
        String deadlineMigration = read("src/main/resources/db/migration/postgresql/V28__task_subtask_due_date.sql");

        assertTrue(html.contains("id=\"taskEditSubtasks\""));
        assertTrue(html.contains("id=\"taskEditSubtaskList\""));
        assertTrue(html.contains("id=\"taskEditSubtaskAdd\""));
        assertTrue(tasks.contains("collectTaskEditorSubtasks()"));
        assertTrue(tasks.contains("buildTaskSubtasksInline(task)"));
        assertTrue(tasks.contains("async function toggleSubtask("));
        assertTrue(data.contains("updateSubtask(taskId, subtaskId"));
        assertTrue(css.contains(".taskSubtasksInline"));
        assertTrue(css.contains(".taskSubtaskProgressTrack"));
        assertTrue(css.contains(".taskCompletionDivider"));
        assertTrue(tasks.contains("validateTaskEditorDeadlines("));
        assertTrue(tasks.contains("sortedTasksOpenFirst("));
        assertTrue(tasks.contains("dueDate:row.querySelector('input[type=\"date\"]')"));
        assertTrue(migration.contains("CREATE TABLE task_subtasks"));
        assertTrue(deadlineMigration.contains("ADD COLUMN due_date DATE"));
        assertFalse(migration.contains("parent_subtask_id"),
                "DutyLog deliberately supports one checklist level only");
    }

    @Test
    void taskCardsAndOvertimeLedgerKeepActionsInDedicatedLayoutColumns() throws Exception {
        String html = read("src/main/resources/static/index.html");
        String tasks = read("src/main/resources/static/js/50-tasks.js");
        String overtime = read("src/main/resources/static/js/40-overtime.js");
        String css = read("src/main/resources/static/app.css");

        assertTrue(css.contains("grid-template-columns:auto minmax(0,1fr) auto"));
        assertTrue(css.contains(".taskItem > .tinyDel"));
        assertTrue(css.contains(".taskItem > .taskSubtasksInline"));
        assertTrue(tasks.contains("row.append(checkbox, body, remove)"));
        assertTrue(html.contains("class=\"ledgerActionsHead\">Действия</th>"));
        assertTrue(overtime.contains("class=\"ledgerRowActions\""));
        assertTrue(overtime.contains("class=\"ledgerUsageActions\""));
        assertTrue(overtime.contains("Управляется отсутствием"));
        assertTrue(overtime.contains("Перенести старое списание в отсутствия"));
        assertTrue(overtime.contains("Удалить старое списание"));
        assertTrue(overtime.contains("Удалить весь отгул"));
        assertTrue(overtime.contains("usageManagedByAbsence(fullUsage)"));
    }

    @Test
    void shiftTypeManagerLivesBehindCalendarPlusInsteadOfSettingsCard() throws Exception {
        String html = read("src/main/resources/static/index.html");
        String modal = read("frontend/src/features/settings-workspace/components/ShiftTypeManagerModal.vue");
        String settingsWorkspace = read("frontend/src/features/settings-workspace/components/SettingsWorkspace.vue");
        String selectedDay = read("frontend/src/features/calendar-timeline/components/SelectedDayPanel.vue");
        String bridge = read("frontend/src/platform/bridge/legacyBridge.ts");
        String api = read("frontend/src/features/settings-workspace/api/settingsWorkspaceApi.ts");
        String core = read("src/main/resources/static/js/10-core.js");
        String legacySettings = read("src/main/resources/static/js/60-settings.js");

        assertTrue(html.contains("id=\"shiftTypeModal\"")); // recovery fallback before Vue readiness
        assertTrue(modal.contains("id=\"shiftTypeModal\""));
        assertTrue(modal.contains("id=\"shiftTypeForm\""));
        assertTrue(modal.contains("id=\"customList\""));
        assertTrue(modal.contains("function inputText(raw: unknown): string"));
        assertTrue(modal.contains("String(raw ?? \"\").trim()"));
        assertFalse(modal.contains("notificationMinutesBefore.trim()"));
        assertTrue(settingsWorkspace.contains("settingsWorkspace: domain"));
        assertTrue(selectedDay.contains("DutyLogVueDomains?.settingsWorkspace?.openShiftTypeManager()"));
        assertFalse(bridge.contains("openShiftTypeManager"));
        assertTrue(api.contains("client.request(\"createShiftType\""));
        assertTrue(api.contains("client.request(\"updateShiftType\""));
        assertTrue(api.contains("client.request(\"deleteShiftType\""));
        assertTrue(core.contains("document.getElementById(\"shiftTypeModal\")?.remove()"));
        assertTrue(legacySettings.contains("vueManager.openShiftTypeManager"));
        assertFalse(html.contains("id=\"shiftSettingsCard\""));
        assertFalse(html.contains("data-settings-jump=\"shifts\""));
    }
}
