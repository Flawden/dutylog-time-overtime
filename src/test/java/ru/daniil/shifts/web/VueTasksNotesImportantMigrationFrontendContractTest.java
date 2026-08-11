package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Binding contracts for the Vue Tasks, Notes & Important Days migration in v27.38.0. */
class VueTasksNotesImportantMigrationFrontendContractTest {

    private static final Path FEATURE = Path.of("frontend/src/features/productivity");

    @Test
    void appShellInstallsOneVueProductivityOwnerForTasksImportantAndSelectedDayModules() throws Exception {
        String shell = read("frontend/src/app/AppShell.vue");
        String shellStore = read("frontend/src/app/shellStore.ts");
        String workspace = read(FEATURE.resolve("components/ProductivityWorkspace.vue"));
        String core = read("src/main/resources/static/js/10-core.js");

        assertTrue(shell.contains("ProductivityWorkspace"));
        assertTrue(workspace.contains("retireDomainOwners(\"productivity\")"));
        assertTrue(workspace.contains("<TasksPage"));
        assertTrue(workspace.contains("<ImportantPage"));
        assertTrue(workspace.contains("vueSelectedDayTasksMount"));
        assertTrue(workspace.contains("vueSelectedDayNotesMount"));
        assertTrue(workspace.contains("vueSelectedDayImportantMount"));
        assertTrue(workspace.contains("DutyLogVueDomains"));
        assertTrue(workspace.indexOf("DutyLogVueDomains") < workspace.indexOf("retireDomainOwners(\"productivity\")"));
        assertTrue(workspace.contains("modulesLoaded, onboardingCompleted"));
        assertTrue(workspace.contains("const productivityReadable = computed(() => modulesLoaded.value && (onboardingCompleted.value || !online.value))"));
        assertTrue(workspace.contains("watch([modulesLoaded, onboardingCompleted, modules]"));
        assertTrue(shellStore.contains("function booleanMapEquals"));
        assertTrue(shellStore.contains("!booleanMapEquals(this.modules, snapshot.modules)"));
        assertTrue(core.contains("onboardingCompleted:profile.onboardingCompleted === true"));
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
        String data = read("src/main/resources/static/js/20-data.js");

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
        assertTrue(store.contains("async ensureLoaded(date?: string)"));
        assertTrue(store.contains("const targetDate = date ?? this.selectedDate"));
        assertFalse(store.contains("date = this.selectedDate"));
        assertTrue(store.contains("const optimistic: DayNote"));
        assertTrue(store.contains("content: patch.content ?? row.content"));
        assertTrue(store.contains("this.workTimezone = context?.workTimezone"));
        assertTrue(store.contains("this.workDate = validDate(context?.workDate"));
        assertTrue(store.contains("this.loaded = selectedOk && boardOk && importantOk && inboxOk"));
        assertTrue(store.contains("addMinutesToDateTime"));
        assertTrue(store.contains("taskDraftSnapshot(this.taskDraft)"));
        assertTrue(store.contains("importantDraftSnapshot(this.importantDraft)"));
        assertFalse(store.contains("structuredClone(this.taskDraft)"));
        assertFalse(store.contains("structuredClone(this.importantDraft)"));
        assertTrue(store.contains("const offline = typeof navigator !== \"undefined\" && !navigator.onLine && bridge !== null"));
        assertTrue(store.contains("tasksEnabled && !offline ? this.loadBoard() : Promise.resolve(true)"));
        assertTrue(store.contains("importantEnabled && !offline ? this.loadImportantDays() : Promise.resolve(true)"));
        assertTrue(store.contains("tasksEnabled && !offline ? this.loadInbox() : Promise.resolve(true)"));
        assertTrue(store.contains("function runtimeModuleEnabled(key: string): boolean"));
        assertTrue(store.contains("const snapshot = bridge?.snapshot()"));
        assertTrue(count(store, "!runtimeModuleEnabled(\"tasks\")") >= 2);
        assertTrue(store.contains("!runtimeModuleEnabled(\"important_dates\")"));
        assertTrue(store.contains("!runtimeModuleEnabled(\"notes\")"));
        int optimisticModuleGate = data.indexOf("if (!enabled) setModuleList(optimisticList)");
        int modulePatch = data.indexOf("api.updateModules({ [key]: !!enabled })", optimisticModuleGate);
        assertTrue(optimisticModuleGate >= 0 && modulePatch > optimisticModuleGate);
        assertTrue(data.contains("setModuleList(previousList)"));
        assertTrue(store.contains("const taskReadYourWrite = new Map<number, Task>()"));
        assertTrue(store.contains("taskReadYourWrite.set(saved.id, saved)"));
        assertTrue(store.contains("for (const saved of taskReadYourWrite.values())"));
        assertTrue(store.contains("publishSavedTask(saved: Task)"));
        assertTrue(store.contains("defaultBoardAccepts(saved, this)"));
        assertTrue(store.contains("taskDisplayDate(saved) === this.selectedDate"));
        assertTrue(store.contains("items[boardIndex] = saved"));
        assertTrue(store.contains("taskReadYourWrite.delete(saved.id)"));
        assertTrue(count(store, "this.publishSavedTask(saved)") >= 3);
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
        assertTrue(notes.contains("ReturnType<typeof globalThis.setTimeout>"));
        assertTrue(notes.contains("await calendar.openDate(date, \"month\")"));
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
        assertTrue(page.contains("deadlineLabel(task)"));
        assertTrue(page.contains("task.dueTime"));
        assertTrue(modal.contains("id=\"taskDetailsModal\""));
        assertTrue(modal.contains("id=\"taskEditModal\""));
        assertTrue(modal.contains("v-for=\"minutes in [15,30,45,60,90,120]\""));
        assertTrue(modal.contains(":data-task-duration=\"minutes\""));
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
        String data = read("src/main/resources/static/js/20-data.js");
        String workspace = read(FEATURE.resolve("components/ProductivityWorkspace.vue"));
        String notes = read(FEATURE.resolve("components/SelectedDayNotes.vue"));

        assertTrue(bridge.contains("dataLayer.updateDayNote"));
        assertTrue(bridge.contains("dataLayer.setTaskDone"));
        assertTrue(bridge.contains("dataLayer.captureInbox"));
        assertTrue(bridge.contains("dataLayer.syncQueue"));
        assertTrue(bridge.contains("dataLayer.readSnapshot"));
        assertTrue(data.contains("dutylog:offline-sync-complete"));
        assertTrue(workspace.contains("OFFLINE_SYNC_COMPLETE_EVENT"));
        assertTrue(workspace.contains("window.addEventListener(OFFLINE_SYNC_COMPLETE_EVENT, handleOfflineSyncComplete)"));
        assertFalse(workspace.contains("window.addEventListener(\"online\", reconnect)"));
        assertFalse(workspace.contains("store.flushOfflineQueue()"));
        assertTrue(notes.contains(":disabled=\"!shell.online\""));
        assertTrue(notes.contains("if (timer != null && currentNote.value)"));
        assertTrue(notes.contains("must not resubmit the already queued/current note beside dataLayer.syncQueue()"));
    }

    @Test
    void legacyOwnersYieldAndMigrationDocumentationLocksTheBoundary() throws Exception {
        String core = read("src/main/resources/static/js/10-core.js");
        String calendar = read("src/main/resources/static/js/30-calendar.js");
        String tasks = read("src/main/resources/static/js/50-tasks.js");
        String workspace = read(FEATURE.resolve("components/ProductivityWorkspace.vue"));
        String selectedDayPanel = read("frontend/src/features/calendar-timeline/components/SelectedDayPanel.vue");
        String manifest = read("docs/migration/tasks-notes-important-vue-migration-manifest.md");

        assertTrue(core.contains("domain === \"productivity\""));
        assertFalse(core.contains("vueSelectedDayTasksMount"));
        assertFalse(core.contains("vueSelectedDayNotesMount"));
        assertFalse(core.contains("vueSelectedDayImportantMount"));
        assertTrue(selectedDayPanel.contains("vueSelectedDayTasksMount"));
        assertTrue(selectedDayPanel.contains("vueSelectedDayNotesMount"));
        assertTrue(selectedDayPanel.contains("vueSelectedDayImportantMount"));
        assertTrue(core.contains("setAttribute(\"data-vue-productivity\", \"ready\")"));
        assertTrue(tasks.contains("document.documentElement.dataset.vueProductivity === \"ready\""));
        assertTrue(tasks.contains("productivity?.openTaskCreate"));
        assertTrue(tasks.contains("productivity?.openImportantCreate"));
        assertTrue(tasks.contains("if (document.documentElement.dataset.vueProductivity === \"ready\") return;"));
        assertTrue(tasks.contains("function vueOwnsProductivityUi()"));
        assertTrue(tasks.contains("function renderTaskMetadataSuggestions"));
        assertTrue(tasks.contains("const metadata = await api.taskMetadata();\n    if (vueOwnsProductivityUi()) return;"));
        assertTrue(tasks.contains("function renderInbox"));
        assertTrue(tasks.contains("function renderTaskBoardCategoryFilter"));
        assertTrue(tasks.contains("function renderTaskBoardProjectFilter"));
        assertTrue(tasks.contains("function syncTaskBoardFiltersToInputs"));
        assertTrue(tasks.contains("function renderTaskBoard"));
        assertTrue(count(tasks, "if (vueOwnsProductivityUi()) return;") >= 14);
        assertFalse(core.contains("openCalendarSection(section)"));
        assertTrue(workspace.contains("calendar.openDayPanel(targetDate, \"notes\")"));
        assertTrue(calendar.contains("const vueOwnsProductivitySummaries = document.documentElement.dataset.vueProductivity === \"ready\""));
        assertTrue(calendar.contains("if ($(\"sumTasks\") && !vueOwnsProductivitySummaries)"));
        assertTrue(calendar.contains("if ($(\"sumImp\") && !vueOwnsProductivitySummaries)"));
        assertTrue(calendar.contains("if (!vueOwnsProductivitySummaries)"));
        assertTrue(workspace.contains("data-vue-productivity-summary=\"tasks\""));
        assertTrue(workspace.contains("data-vue-productivity-summary=\"notes\""));
        assertTrue(workspace.contains("data-vue-productivity-summary=\"important\""));
        assertTrue(manifest.contains("target_release: \"v27.38.0\""));
        assertTrue(manifest.contains("Spring Boot remains the source of truth"));
        assertTrue(manifest.toLowerCase(Locale.ROOT).contains("offline/reconnect"));
    }

    private static int count(String value, String needle) {
        int count = 0;
        int index = 0;
        while ((index = value.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
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
