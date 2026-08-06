package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Binding contracts for the Vue Calendar & Timeline migration in v27.37.0. */
class VueCalendarTimelineMigrationFrontendContractTest {

    private static final Path FEATURE = Path.of("frontend/src/features/calendar-timeline");

    @Test
    void appShellInstallsOneVueOwnerForTodayCalendarAndTimeline() throws Exception {
        String shell = read("frontend/src/app/AppShell.vue");
        String workspace = read(FEATURE.resolve("components/CalendarTimelineWorkspace.vue"));

        assertTrue(shell.contains("CalendarTimelineWorkspace"));
        assertTrue(workspace.contains("activeRoute === 'today'"));
        assertTrue(workspace.contains("activeRoute === 'calendar'"));
        assertTrue(workspace.contains("retireDomainOwners(\"calendar-timeline\")"));
        assertTrue(workspace.contains("DutyLogVueDomains"));
    }

    @Test
    void featureUsesGeneratedApiAndNeverReadsLegacyStateOrDom() throws Exception {
        String api = read(FEATURE.resolve("api/calendarTimelineApi.ts"));
        String sources = featureSources();

        assertTrue(api.contains("createGeneratedDutyLogApiClient"));
        assertTrue(api.contains("client.request(\"calendarRange\""));
        assertTrue(api.contains("client.request(\"getTimeContext\""));
        assertTrue(api.contains("client.request(\"updateCalendarLayer\""));
        assertFalse(sources.contains("window.state"));
        assertFalse(sources.contains("jfetch("));
        assertFalse(sources.contains("document.querySelector"));
    }

    @Test
    void storeOwnsRangeModeFocusPersistenceAndStaleReadProtection() throws Exception {
        String store = read(FEATURE.resolve("stores/calendarTimelineStore.ts"));

        assertTrue(store.contains("let readSequence = 0"));
        assertTrue(store.contains("const sequence = ++readSequence"));
        assertTrue(store.contains("if (sequence !== readSequence) return"));
        assertTrue(store.contains("dutylog.calendar.mode.v2"));
        assertTrue(store.contains("dutylog.calendar.focus.v2"));
        assertTrue(store.contains("publishCalendarTimelineProjection"));
    }

    @Test
    void calendarPageOwnsMonthWeekDayTimelineAndLayerControls() throws Exception {
        String page = read(FEATURE.resolve("components/CalendarPage.vue"));

        assertTrue(page.contains("id=\"view-calendar\""));
        assertTrue(page.contains("id=\"grid\""));
        assertTrue(page.contains("data-calendar-mode=\"value\""));
        assertTrue(page.contains("id=\"calendarWeekExperience\""));
        assertTrue(page.contains("id=\"calendarDayExperience\""));
        assertTrue(page.contains("id=\"calendarTimelineCanvas\""));
        assertTrue(page.contains("class=\"calendarLayerToggle\""));
    }

    @Test
    void todayPageOwnsDashboardAndUsesNamedCrossDomainCommands() throws Exception {
        String page = read(FEATURE.resolve("components/TodayPage.vue"));

        assertTrue(page.contains("id=\"view-today\""));
        assertTrue(page.contains("id=\"todayDateStrip\""));
        assertTrue(page.contains("id=\"todayQuickTask\""));
        assertTrue(page.contains("openAbsenceComposer"));
        assertTrue(page.contains("openTaskCreate"));
        assertTrue(page.contains("openQuickActions"));
        assertFalse(page.contains("openModal("));
    }

    @Test
    void legacyPlatformRetiresReadSurfacesButPreservesTheDayEditorIsland() throws Exception {
        String bridge = read("frontend/src/platform/bridge/legacyBridge.ts");
        String legacy = read("src/main/resources/static/js/10-core.js");

        assertTrue(bridge.contains("CALENDAR_TIMELINE_PROJECTION_EVENT"));
        assertTrue(bridge.contains("attachCalendarEditor"));
        assertTrue(legacy.contains("calendar-timeline"));
        assertTrue(legacy.contains("attachCalendarEditor(hostId)"));
        assertTrue(legacy.contains("document.getElementById(String(hostId"));
        assertTrue(legacy.contains("document.body.appendChild(panel)"));
        assertTrue(legacy.contains("openCalendarDay(date)"));
        assertTrue(legacy.contains("data-vue-calendar-timeline"));
    }

    @Test
    void legacyRenderersYieldAfterVueOwnershipIsReady() throws Exception {
        String calendar = read("src/main/resources/static/js/30-calendar.js");
        String today = read("src/main/resources/static/js/35-today.js");

        assertTrue(calendar.contains("requestVueCalendarTimelineRefresh()"));
        assertTrue(today.contains("requestVueCalendarTimelineRefresh()"));
        assertTrue(read("src/main/resources/static/js/10-core.js").contains("vueCalendarTimelineRefreshQueued"));
    }

    @Test
    void unitCoverageLocksDateMathCompositionConcurrencyAndLayerRollback() throws Exception {
        String model = read(FEATURE.resolve("types/model.spec.ts"));
        String store = read(FEATURE.resolve("stores/calendarTimelineStore.spec.ts"));

        assertTrue(model.contains("complete Monday-to-Sunday grid"));
        assertTrue(model.contains("composes one selected-day read model"));
        assertTrue(store.contains("stale month response"));
        assertTrue(store.contains("without reloading the canonical range"));
        assertTrue(store.contains("loads the work-date range for Today"));
        assertTrue(store.contains("rolls back a failed mutation"));
    }

    @Test
    void browserAcceptanceKeepsOneVueOwnerAndTheSelectedDayEditorIsland() throws Exception {
        String browser = read("e2e/vue-calendar-timeline-migration.spec.js");

        assertTrue(browser.contains("data-vue-domain-owner=\"calendar-timeline\""));
        assertTrue(browser.contains("#calendarWeekStrip [data-date]"));
        assertTrue(browser.contains("#calendarTimelineHours span"));
        assertTrue(browser.contains("#calendarLegacyPanelHost > #panel"));
        assertTrue(browser.contains("toHaveCount(1)"));
    }

    @Test
    void pwaUpgradeAndBundleBudgetsBecomeRecurringFrontendGates() throws Exception {
        String pwa = read("e2e/pwa-upgrade.spec.js");
        String audit = read("frontend/scripts/audit-browser-bundle.mjs");
        String budget = read("frontend/browser-bundle-budget.json");
        String adr = read("docs/architecture/adr/ADR-006-pwa-asset-version-upgrade-strategy.md");
        String worker = read("src/main/resources/static/service-worker.js");

        assertTrue(pwa.contains("dutylog-shell-v27.36.8-synthetic-previous"));
        assertTrue(pwa.contains("dutylog-shell-v27.37.0-"));
        assertTrue(audit.contains("gzipSync"));
        assertTrue(audit.contains("budget.maxBytes"));
        assertTrue(budget.contains("\"release\": \"27.37.0\""));
        assertTrue(adr.contains("Status: accepted"));
        assertTrue(adr.contains("network-first"));
        assertTrue(worker.contains("k.startsWith(\"dutylog-shell-\")"));
        assertTrue(worker.contains("k !== CACHE_NAME"));
    }

    @Test
    void migrationDocumentationKeepsSpringBootAsBusinessOwner() throws Exception {
        String manifest = read("docs/migration/calendar-timeline-vue-migration-manifest.md");

        assertTrue(manifest.contains("target_release: \"v27.37.0\""));
        assertTrue(manifest.contains("Spring Boot remains the source of truth"));
        assertTrue(manifest.contains("legacy selected-day editor compatibility island"));
        assertTrue(manifest.contains("Vue owns Today, Month, Week and Day read surfaces"));
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
