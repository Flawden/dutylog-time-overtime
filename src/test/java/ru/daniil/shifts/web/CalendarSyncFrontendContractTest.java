package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CalendarSyncFrontendContractTest {

    @Test
    void settingsExposeRangeExportAndOneTimePrivateSubscriptionUrl() throws IOException {
        String html = resource("/static/index.html");
        String js = resource("/static/js/55-calendar-sync.js");
        assertTrue(html.contains("id=\"calendarSyncCard\"")
                && html.contains("id=\"calendarExportRange\"")
                && html.contains("id=\"calendarSyncIssue\"")
                && html.contains("id=\"calendarSyncRevoke\"")
                && html.contains("id=\"calendarSyncUrl\"")
                && html.contains("id=\"importantDetailsExportIcs\""));
        assertTrue(js.contains("issueCalendarSubscription")
                && js.contains("state.calendarSyncIssuedUrl = issued.subscriptionUrl")
                && js.contains("state.calendarSyncIssuedUrl = null")
                && js.contains("/api/calendar-sync/export?from=")
                && js.contains("/api/calendar-sync/events/"));
    }

    @Test
    void moduleVisibilitySeparatesCalendarSyncFromCalendarData() throws IOException {
        String data = resource("/static/js/20-data.js");
        String core = resource("/static/js/10-core.js");
        assertTrue(core.contains("calendar_sync:true") && core.contains("calendarSyncIssuedUrl"));
        assertTrue(data.contains("moduleEnabled(\"calendar_sync\")")
                && data.contains("calendarSyncCard")
                && data.contains("importantDetailsExportIcs")
                && data.contains("#settings-calendar-sync")
                && data.contains("calendarSyncStatus")
                && data.contains("else { state.vacationPlanner = null; state.absenceOccurrences = []; state.absencesByDate = {}; }")
                && data.contains("else { state.calendarSync = null; state.calendarSyncIssuedUrl = null; }"));
    }

    @Test
    void rangeDefaultsUseTheCanonicalCalendarDateKeyHelper() throws IOException {
        String js = resource("/static/js/55-calendar-sync.js");
        assertTrue(js.contains("from:keyOf(start.getFullYear(), start.getMonth(), start.getDate())")
                && js.contains("to:keyOf(end.getFullYear(), end.getMonth(), end.getDate())"));
        assertTrue(!js.contains("localDateKey("));
    }

    @Test
    void externalCalendarPanelIsResponsiveAndLoadedBeforeSettingsBoot() throws IOException {
        String html = resource("/static/index.html");
        String css = resource("/static/app.css");
        int sync = html.indexOf("js/55-calendar-sync.js?v=");
        int settings = html.indexOf("js/60-settings.js?v=");
        int boot = html.indexOf("js/70-user-boot.js?v=");
        assertTrue(sync > 0 && settings > sync && boot > settings);
        assertTrue(css.contains(".calendarSyncGrid")
                && css.contains(".calendarSyncSecret")
                && css.contains("@media(max-width:760px)"));
    }

    private static String resource(String path) throws IOException {
        try (var in = CalendarSyncFrontendContractTest.class.getResourceAsStream(path)) {
            if (in == null) throw new IOException("Missing classpath resource: " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
