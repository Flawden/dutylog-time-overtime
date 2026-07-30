package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ScheduleTemplatesCalendarLayersFrontendContractTest {

    @Test
    void unifiedShellLoadsScheduleLayerCompositionBeforeFeatureBundlesAndBoot() throws IOException {
        String html = resource("/static/index.html");
        int calendar = html.indexOf("js/37-calendar-experience.js?v=");
        int schedules = html.indexOf("js/38-schedule-layers.js?v=");
        int overtime = html.indexOf("js/40-overtime.js?v=");
        int settings = html.indexOf("js/60-settings.js?v=");
        int boot = html.indexOf("js/70-user-boot.js?v=");

        assertTrue(calendar >= 0 && schedules > calendar && overtime > schedules && settings > overtime && boot > settings,
                "schedule/layer composition must load after calendar experience and before feature boot");
        assertTrue(html.contains("id=\"calendarLayerBar\"")
                        && html.contains("id=\"scheduleTemplateList\"")
                        && html.contains("id=\"calendarLayerList\"")
                        && html.contains("id=\"schedulePreview\""),
                "calendar and settings must expose the schedule-template/layer surfaces");
    }

    @Test
    void browserContractsKeepSafePreviewReadOnlyLayersAndModeComposition() throws IOException {
        String data = resource("/static/js/20-data.js");
        String calendar = resource("/static/js/30-calendar.js");
        String layers = resource("/static/js/38-schedule-layers.js");
        String settings = resource("/static/js/60-settings.js");

        assertTrue(data.contains("scheduleTemplates:")
                        && data.contains("previewScheduleTemplate")
                        && data.contains("applyScheduleTemplate")
                        && data.contains("calendarLayers:"),
                "API adapter must expose authoritative schedule and layer resources");
        assertTrue(calendar.contains("overwriteExistingShift:!!$(\"tplOverwrite\").checked")
                        && calendar.contains("previewScheduleTemplateSelection")
                        && calendar.contains("SKIP_CONFLICT"),
                "calendar fill must preview first and keep overwrite explicit");
        assertTrue(layers.contains("calendarLayerToggle")
                        && layers.contains("calendarLayerEntriesByDate")
                        && layers.contains("calendarExperienceTimelineEvents")
                        && layers.contains("calendarExperienceRenderAllDay")
                        && layers.contains("calendarExperienceRenderWeek"),
                "read-only layers must compose into month, week and hourly day without becoming owner entries");
        assertTrue(settings.contains("typeof renderScheduleLayerSettings === \"function\""),
                "settings must render schedule/layer management after the composition bundle is available");
    }

    private static String resource(String path) throws IOException {
        try (var in = ScheduleTemplatesCalendarLayersFrontendContractTest.class.getResourceAsStream(path)) {
            if (in == null) throw new IOException("Missing classpath resource: " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
