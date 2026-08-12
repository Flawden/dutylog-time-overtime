package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Static architecture contract for v27.40.16 route-entry freshness and note read-your-write ownership. */
class VueRouteEntryFreshnessAndNoteReadYourWriteHotfixTest {

    @Test
    void absenceAndTimeBankRoutesRefreshTheirAuthoritativeReadModelOnEntry() throws Exception {
        String workspace = read("frontend/src/features/absence-time-bank/components/AbsenceTimeBankWorkspace.vue");
        String synchronize = between(workspace, "async function synchronizeRoute(route: string): Promise<void>", "onMounted(() =>");

        assertTrue(synchronize.contains("route !== \"vacation\" && route !== \"overtime\""));
        assertTrue(synchronize.contains("await store.refresh();"));
        assertFalse(synchronize.contains("await store.ensureLoaded();"));
    }

    @Test
    void todayRouteRefreshesTheDashboardBundleInsteadOfReusingBootState() throws Exception {
        String workspace = read("frontend/src/features/calendar-timeline/components/CalendarTimelineWorkspace.vue");
        String synchronize = between(workspace, "async function synchronize(route: string): Promise<void>", "onMounted(() =>");

        assertTrue(synchronize.contains("if (route === \"today\") await store.refresh(true);"));
        assertTrue(synchronize.contains("else await store.ensureLoaded();"));
        assertFalse(synchronize.contains("store.ensureTodayLoaded()"));
    }

    @Test
    void todayWorkspaceCardsAreRenderedByVueFromAppearanceAndModuleState() throws Exception {
        String today = read("frontend/src/features/calendar-timeline/components/TodayPage.vue");

        assertTrue(today.contains("useSettingsWorkspaceStore"));
        assertTrue(today.contains("workspaceDefinition(appearance.value.themeConfig).todayWidgets"));
        assertTrue(today.contains("v-for=\"widget in todayWidgets\""));
        assertTrue(today.contains("modules.value.overtime !== false"));
        assertTrue(today.contains("modules.value.tasks !== false"));
        assertTrue(today.contains("modules.value.important_dates !== false"));
        assertFalse(today.contains("workspaceHidden"));
    }

    @Test
    void createdNotePublishesTheResponseWithoutReloadingOverTheLiveEditorDraft() throws Exception {
        String store = read("frontend/src/features/productivity/stores/productivityStore.ts");
        String createNote = between(store, "async createNote(date?: string, content = \"\"): Promise<void>", "selectNote(id: number): void");

        assertTrue(createNote.contains("const note = await api.createNote(targetDate, content);"));
        assertTrue(createNote.contains("this.selectedNotes = sortDayNotes(["));
        assertTrue(createNote.contains("this.selectedNoteId = note.id;"));
        assertTrue(createNote.contains("await refreshCalendarIfMounted();"));
        assertFalse(createNote.contains("await this.loadSelectedDate(targetDate);\n        if (note)"));
    }

    private static String between(String source, String startToken, String endToken) {
        int start = source.indexOf(startToken);
        int end = source.indexOf(endToken, start + startToken.length());
        if (start < 0 || end < 0) throw new IllegalStateException("Contract surface not found");
        return source.substring(start, end);
    }

    private static String read(String relative) throws Exception {
        return Files.readString(Path.of(relative));
    }
}
