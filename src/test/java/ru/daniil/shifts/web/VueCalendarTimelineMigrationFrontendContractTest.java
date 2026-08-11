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
        assertTrue(workspace.indexOf("DutyLogVueDomains") < workspace.indexOf("retireDomainOwners(\"calendar-timeline\")"));
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
        String domain = read(FEATURE.resolve("types/domain.ts"));
        assertTrue(domain.contains("export type CalendarTask = DutyLogApiSchemas.Task;"));
        assertFalse(domain.contains("interface CalendarTask extends"));
    }

    @Test
    void storeOwnsRangeModeFocusPersistenceAndStaleReadProtection() throws Exception {
        String store = read(FEATURE.resolve("stores/calendarTimelineStore.ts"));

        assertTrue(store.contains("let readSequence = 0"));
        assertTrue(store.contains("const sequence = ++readSequence"));
        assertTrue(store.contains("if (sequence !== readSequence) return"));
        assertTrue(store.contains("dutylog.calendar.mode.v2"));
        assertTrue(store.contains("export function installCalendarTimelineOfflineSource"));
        assertTrue(store.contains("canUseOfflineFallback(error) ? await loadOfflineCalendar(fallbackFocus) : null"));
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
        assertTrue(page.contains("class=\"absenceFact\""));
        assertTrue(page.contains("class=\"partialAbsenceBar\""));
        assertTrue(page.contains("<SelectedDayPanel v-if=\"dayPanelOpen\""));
    }

    @Test
    void todayPageOwnsDashboardAndUsesNamedCrossDomainCommands() throws Exception {
        String page = read(FEATURE.resolve("components/TodayPage.vue"));

        assertTrue(page.contains("id=\"view-today\""));
        assertTrue(page.contains("id=\"todayDateStrip\""));
        assertTrue(page.contains("id=\"todayQuickTask\""));
        assertTrue(page.contains("openAbsenceComposer"));
        assertTrue(page.contains("openTaskCreate"));
        assertTrue(page.contains("function openTaskDetails(id: number)"));
        assertTrue(page.contains("function openImportantDetails(id: number)"));
        assertTrue(page.contains("openQuickActions"));
        assertFalse(page.contains("@click=\"window.DutyLogVueDomains"));
        assertFalse(page.contains("openModal("));
    }

    @Test
    void vueOwnsSelectedDayPanelWhileLegacyDataLayerRemainsTheSingleOfflineWriter() throws Exception {
        String bridge = read("frontend/src/platform/bridge/legacyBridge.ts");
        String page = read(FEATURE.resolve("components/CalendarPage.vue"));
        String panel = read(FEATURE.resolve("components/SelectedDayPanel.vue"));
        String legacy = read("src/main/resources/static/js/10-core.js");
        String boot = read("src/main/resources/static/js/70-user-boot.js");
        String calendar = read("src/main/resources/static/js/30-calendar.js");

        assertTrue(bridge.contains("CALENDAR_TIMELINE_PROJECTION_EVENT"));
        assertTrue(bridge.contains("writeCalendarDay(date: string, patch: Record<string, unknown>)"));
        assertFalse(bridge.contains("attachCalendarEditor"));
        assertFalse(bridge.contains("parkCalendarEditor"));
        assertFalse(bridge.contains("openCalendarDay"));
        assertFalse(bridge.contains("openCalendarSection"));
        assertFalse(bridge.contains("closeCalendarDay"));
        assertTrue(page.contains("SelectedDayPanel"));
        assertTrue(page.contains("store.openDayPanel"));
        assertTrue(page.contains("<SelectedDayPanel v-if=\"dayPanelOpen\""));
        assertTrue(panel.contains("id=\"panel\""));
        assertTrue(panel.contains("data-vue-selected-day-panel"));
        assertTrue(panel.contains("id=\"chips\""));
        assertTrue(panel.contains("id=\"dayEmojiApply\""));
        assertTrue(panel.contains("id=\"accVacation\""));
        assertTrue(panel.contains("id=\"dayAddCredit\""));
        assertTrue(panel.contains("props.bridge.writeCalendarDay"));
        assertTrue(panel.contains("sourceRange: dateTimeRange(occurrence.sourceStart, occurrence.sourceEnd)"));
        assertTrue(panel.contains("Рабочее время смены"));
        assertTrue(panel.contains("allocationRangeLabels(allocation)"));
        assertTrue(panel.contains("24:00"));
        assertTrue(legacy.contains("calendar-timeline"));
        assertTrue(legacy.contains("async writeCalendarDay(date, patch)"));
        assertTrue(legacy.contains("dataLayer.putDay(key, next)"));
        assertTrue(legacy.contains("data-vue-calendar-selected-day"));
        assertFalse(legacy.contains("attachCalendarEditor(hostId)"));
        assertFalse(legacy.contains("parkCalendarEditor()"));
        assertFalse(legacy.contains("document.body.appendChild(panel)"));
        assertTrue(boot.contains("document.querySelectorAll(\".nav #prev, .nav #todayBtn, .nav #next\")"));
        assertFalse(boot.contains("document.querySelector(\".nav #prev\").style.visibility"));
        assertFalse(boot.contains("document.querySelector(\".nav #todayBtn\").style.visibility"));
        assertFalse(boot.contains("document.querySelector(\".nav #next\").style.visibility"));
        assertTrue(calendar.contains("dataset.vueCalendarSelectedDay === \"ready\""));
        assertTrue(calendar.contains("calendarTimeline?.openDay"));
    }

    @Test
    void legacyRenderersYieldAfterVueOwnershipIsReady() throws Exception {
        String calendar = read("src/main/resources/static/js/30-calendar.js");
        String today = read("src/main/resources/static/js/35-today.js");
        String experience = read("src/main/resources/static/js/37-calendar-experience.js");
        String layers = read("src/main/resources/static/js/38-schedule-layers.js");

        assertTrue(calendar.contains("requestVueCalendarTimelineRefresh()"));
        assertTrue(today.contains("requestVueCalendarTimelineRefresh()"));
        assertTrue(experience.contains("document.documentElement.dataset.vueCalendarTimeline === \"ready\""));
        assertTrue(layers.contains("if (document.documentElement.dataset.vueCalendarTimeline === \"ready\") return;"));
        assertTrue(read("src/main/resources/static/js/10-core.js").contains("vueCalendarTimelineRefreshQueued"));
    }

    @Test
    void unitCoverageLocksDateMathCompositionConcurrencyAndLayerRollback() throws Exception {
        String model = read(FEATURE.resolve("types/model.spec.ts"));
        String store = read(FEATURE.resolve("stores/calendarTimelineStore.spec.ts"));

        assertTrue(model.contains("complete Monday-to-Sunday grid"));
        assertTrue(model.contains("composes one selected-day read model"));
        assertTrue(model.contains("scheduledEndDate: \"2026-08-05\""));
        assertTrue(model.contains("dayFacts(crossMidnight, \"2026-08-05\").tasks"));
        assertTrue(read(FEATURE.resolve("types/model.ts")).contains("dateSpanContains"));
        assertTrue(store.contains("stale month response"));
        assertTrue(store.contains("without reloading the canonical range"));
        assertTrue(store.contains("loads the work-date range for Today"));
        assertTrue(store.contains("rolls back a failed mutation"));
    }

    @Test
    void browserAcceptanceKeepsOneVueOwnerAndNativeSelectedDayPanel() throws Exception {
        String browser = read("e2e/vue-calendar-timeline-migration.spec.js");
        String helpers = read("e2e/helpers.js");

        assertTrue(browser.contains("data-vue-domain-owner=\"calendar-timeline\""));
        assertTrue(browser.contains("#calendarWeekStrip [data-date]"));
        assertTrue(browser.contains("#calendarTimelineHours span"));
        assertTrue(browser.contains("#panel[data-vue-selected-day-panel]"));
        assertTrue(browser.contains("#calendarLegacyPanelHost"));
        assertTrue(browser.contains("toHaveCount(1)"));
        assertTrue(helpers.contains("Clicking an already-focused"));
        assertFalse(helpers.contains("not.toHaveClass(/sel/)"));
    }

    @Test
    void pwaUpgradeAndBundleBudgetsBecomeRecurringFrontendGates() throws Exception {
        String pwa = read("e2e/pwa-upgrade.spec.js");
        String audit = read("frontend/scripts/audit-browser-bundle.mjs");
        String budget = read("frontend/browser-bundle-budget.json");
        String adr = read("docs/architecture/adr/ADR-006-pwa-asset-version-upgrade-strategy.md");
        String worker = read("src/main/resources/static/service-worker.js");
        String boot = read("src/main/resources/static/js/70-user-boot.js");

        assertTrue(pwa.contains("dutylog-shell-v27.38.15-synthetic-previous"));
        String releaseVersion = projectVersion();

        assertTrue(pwa.contains("dutylog-shell-v" + releaseVersion + "-"));
        assertTrue(audit.contains("gzipSync"));
        assertTrue(audit.contains("budget.maxBytes"));
        assertTrue(budget.contains("\"release\": \"" + releaseVersion + "\""));
        assertTrue(adr.contains("Status: accepted"));
        assertTrue(adr.contains("network-first"));
        assertTrue(worker.contains("k.startsWith(\"dutylog-shell-\")"));
        assertTrue(worker.contains("k !== CACHE_NAME"));
        assertTrue(boot.contains("let hasController = Boolean(navigator.serviceWorker.controller)"));
        assertTrue(boot.contains("if (!hasController) { hasController = true; return; }"));
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


    private static String projectVersion() throws Exception {
        String pom = read("pom.xml");
        var matcher = java.util.regex.Pattern.compile(
                "<artifactId>dutylog</artifactId>\\s*<version>([^<]+)</version>"
        ).matcher(pom);
        if (!matcher.find()) {
            throw new IllegalStateException("DutyLog project version not found in pom.xml");
        }
        return matcher.group(1).trim();
    }

    private static String read(String path) throws Exception { return read(Path.of(path)); }
    private static String read(Path path) throws Exception { return Files.readString(path, StandardCharsets.UTF_8); }
}
