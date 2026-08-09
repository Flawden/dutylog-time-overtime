package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Binding contracts for the Vue Tasks, Notes & Important Days migration in v27.38.0. */
class VueTasksNotesImportantMigrationFrontendContractTest {

    private static final Path FEATURE = Path.of("frontend/src/features/productivity");

    @Test
    void appShellInstallsOneVueProductivityOwnerForTasksImportantAndSelectedDayModules() throws Exception {
        String shell = read("frontend/src/app/AppShell.vue");
        String workspace = read(FEATURE.resolve("components/ProductivityWorkspace.vue"));

        assertTrue(shell.contains("ProductivityWorkspace"));
        assertTrue(workspace.contains("retireDomainOwners(\"productivity\")"));
        assertTrue(workspace.contains("<TasksPage"));
        assertTrue(workspace.contains("<ImportantPage"));
        assertTrue(workspace.contains("vueSelectedDayTasksMount"));
        assertTrue(workspace.contains("vueSelectedDayNotesMount"));
        assertTrue(workspace.contains("vueSelectedDayImportantMount"));
        assertTrue(workspace.contains("DutyLogVueDomains"));
    }

    @Test
    void productivityUsesGeneratedApiAndDoesNotReintroduceLegacyTransportOrDomReads() throws Exception {
        String api = read(FEATURE.resolve("api/productivityApi.ts"));
        String sources = featureSources();

        assertTrue(api.contains("createGeneratedDutyLogApiClient"));
        assertTrue(api.contains("client.request(\"taskBoard\""));
        assertTrue(api.contains("client.request(\"createTask\""));
        assertTrue(api.contains("client.request(\"createDayNote\""));
        assertTrue(api.contains("client.request(\"createImportantDay\""));
        assertTrue(api.contains("client.request(\"convertInboxItemToTask\""));
        assertFalse(sources.contains("jfetch("));
        assertFalse(sources.contains("window.state"));
        assertFalse(sources.contains("document.querySelector"));
    }

    @Test
    void storeProtectsConcurrentReadsDoubleSubmitConflictRefreshAndOfflineSafeMutations() throws Exception {
        String store = read(FEATURE.resolve("stores/productivityStore.ts"));

        assertTrue(store.contains("let boardReadSequence = 0"));
        assertTrue(store.contains("let selectedReadSequence = 0"));
        assertTrue(store.contains("const sequence = ++selectedReadSequence"));
        assertTrue(store.contains("if (sequence !== selectedReadSequence) return"));
        assertTrue(store.contains("if (this.mutationPending) return"));
        assertTrue(store.contains("error.status === 409"));
        assertTrue(store.contains("await this.refreshAll(this.selectedDate)"));
        assertTrue(store.contains("bridge.offlineUpdateNote"));
        assertTrue(store.contains("bridge.offlineSetTaskDone"));
        assertTrue(store.contains("bridge.offlineCaptureInbox"));
    }

    @Test
    void selectedDayParityKeepsTaskNoteAndImportantPublicInteractionContracts() throws Exception {
        String tasks = read(FEATURE.resolve("components/SelectedDayTasks.vue"));
        String notes = read(FEATURE.resolve("components/SelectedDayNotes.vue"));
        String important = read(FEATURE.resolve("components/SelectedDayImportant.vue"));

        assertTrue(tasks.contains("id=\"taskCreateForDay\""));
        assertTrue(tasks.contains("id=\"taskList\""));
        assertTrue(tasks.contains("taskCompletionDivider"));
        assertTrue(notes.contains("id=\"noteAdd\""));
        assertTrue(notes.contains("id=\"noteList\""));
        assertTrue(notes.contains("id=\"noteEdit\""));
        assertTrue(notes.contains("dayNoteCardPin"));
        assertTrue(important.contains("id=\"impDate\""));
        assertTrue(important.contains("id=\"impTitle\""));
        assertTrue(important.contains("id=\"impAdd\""));
    }

    @Test
    void taskBoardInboxAndTaskModalsAreVueOwnedWithoutChangingBackendBusinessRules() throws Exception {
        String page = read(FEATURE.resolve("components/TasksPage.vue"));
        String modal = read(FEATURE.resolve("components/TaskModalLayer.vue"));
        String store = read(FEATURE.resolve("stores/productivityStore.ts"));

        assertTrue(page.contains("id=\"view-tasks\""));
        assertTrue(page.contains("data-vue-domain-owner=\"productivity\""));
        assertTrue(page.contains("id=\"taskBoardFilters\""));
        assertTrue(page.contains("id=\"taskInboxCard\""));
        assertTrue(modal.contains("id=\"taskDetailsModal\""));
        assertTrue(modal.contains("id=\"taskEditModal\""));
        assertTrue(modal.contains("data-task-duration=\"45\""));
        assertTrue(store.contains("completeSubtasks: true"));
        assertFalse(store.contains("FIFO"));
    }

    @Test
    void importantBoardAndEditorKeepFloatingDateTimedEventAndReminderSemantics() throws Exception {
        String page = read(FEATURE.resolve("components/ImportantPage.vue"));
        String modal = read(FEATURE.resolve("components/ImportantModalLayer.vue"));
        String model = read(FEATURE.resolve("types/model.ts"));

        assertTrue(page.contains("id=\"view-important\""));
        assertTrue(page.contains("data-important-edit"));
        assertTrue(modal.contains("id=\"importantEditModal\""));
        assertTrue(modal.contains("importantEditEndDateField"));
        assertTrue(modal.contains("importantDraft.eventType === 'IMPORTANT_DATE'"));
        assertTrue(model.contains("const importantDate = draft.eventType === \"IMPORTANT_DATE\""));
        assertTrue(model.contains("sourceTimezone: allDay ? null"));
    }

    @Test
    void offlineReconnectUsesExistingDataLayerQueueAndCachedSelectedDayWithoutInventingASecondStore() throws Exception {
        String bridge = read("src/main/resources/static/js/10-core.js");
        String workspace = read(FEATURE.resolve("components/ProductivityWorkspace.vue"));
        String notes = read(FEATURE.resolve("components/SelectedDayNotes.vue"));

        assertTrue(bridge.contains("dataLayer.updateDayNote"));
        assertTrue(bridge.contains("dataLayer.setTaskDone"));
        assertTrue(bridge.contains("dataLayer.captureInbox"));
        assertTrue(bridge.contains("dataLayer.syncQueue"));
        assertTrue(bridge.contains("dataLayer.readSnapshot"));
        assertTrue(workspace.contains("window.addEventListener(\"online\", reconnect)"));
        assertTrue(workspace.contains("store.flushOfflineQueue()"));
        assertTrue(notes.contains(":disabled=\"!shell.online\""));
    }

    @Test
    void legacyOwnersYieldAndMigrationDocumentationLocksTheBoundary() throws Exception {
        String core = read("src/main/resources/static/js/10-core.js");
        String tasks = read("src/main/resources/static/js/50-tasks.js");
        String manifest = read("docs/migration/tasks-notes-important-vue-migration-manifest.md");

        assertTrue(core.contains("domain === \"productivity\""));
        assertTrue(core.contains("vueSelectedDayTasksMount"));
        assertTrue(core.contains("vueSelectedDayNotesMount"));
        assertTrue(core.contains("vueSelectedDayImportantMount"));
        assertTrue(tasks.contains("data-vue-productivity"));
        assertTrue(tasks.contains("productivity.openTaskCreate"));
        assertTrue(tasks.contains("productivity.openImportantCreate"));
        assertTrue(manifest.contains("target_release: \"v27.38.0\""));
        assertTrue(manifest.contains("Spring Boot remains the source of truth"));
        assertTrue(manifest.contains("offline/reconnect"));
    }

    private static String featureSources() throws Exception {
        var result = new StringBuilder();
        try (var paths = Files.walk(FEATURE)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                result.append(Files.readString(path, StandardCharsets.UTF_8)).append('\n');
            }
        }
        return result.toString();
    }

    private static String read(String path) throws Exception { return read(Path.of(path)); }
    private static String read(Path path) throws Exception { return Files.readString(path, StandardCharsets.UTF_8); }
}
