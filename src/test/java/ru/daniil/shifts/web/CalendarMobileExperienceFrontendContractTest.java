package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Static contract for the additive Month / Week / Day calendar experience. */
class CalendarMobileExperienceFrontendContractTest {

    @Test
    void shellLoadsCalendarExperienceAfterTodayAndBeforeFeatureEditors() throws Exception {
        String html = read("src/main/resources/static/index.html");
        int today = html.indexOf("js/35-today.js?v=27.17.0");
        int calendar = html.indexOf("js/37-calendar-experience.js?v=27.17.0");
        int overtime = html.indexOf("js/40-overtime.js?v=27.17.0");
        assertTrue(today >= 0 && calendar > today && overtime > calendar);
        assertTrue(html.contains("data-calendar-mode=\"month\""));
        assertTrue(html.contains("data-calendar-mode=\"week\""));
        assertTrue(html.contains("data-calendar-mode=\"day\""));
        assertTrue(html.contains("id=\"calendarTimelineCanvas\""));
    }

    @Test
    void calendarExperienceReusesAuthoritativeStoresAndPersistsOnlyUiState() throws Exception {
        String js = read("src/main/resources/static/js/37-calendar-experience.js");
        assertTrue(js.contains("shiftSegmentsOf(key)"));
        assertTrue(js.contains("activeTasksOf(key)"));
        assertTrue(js.contains("importantOf(key)"));
        assertTrue(js.contains("creditsOf(key)"));
        assertTrue(js.contains("dutylog.calendar.mode.v1"));
        assertTrue(js.contains("calendarExperienceBaseRenderCalendar"));
        assertTrue(js.contains("calendarExperienceOpenLegacyDetails"));
    }

    @Test
    void designSystemProvidesScrollableWeekStripAndHourlyTimeline() throws Exception {
        String css = read("src/main/resources/static/design-system.css");
        assertTrue(css.contains(".calendarModeSwitch"));
        assertTrue(css.contains(".calendarWeekStrip"));
        assertTrue(css.contains("scroll-snap-type: x mandatory"));
        assertTrue(css.contains(".calendarTimelineEvent"));
        assertTrue(css.contains(".calendarNowLine"));
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
