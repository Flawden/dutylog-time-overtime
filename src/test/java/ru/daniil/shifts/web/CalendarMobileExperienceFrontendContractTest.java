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
        int today = html.indexOf("js/35-today.js?v=27.17.5");
        int calendar = html.indexOf("js/37-calendar-experience.js?v=27.17.5");
        int overtime = html.indexOf("js/40-overtime.js?v=27.17.5");
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
        assertTrue(js.contains("calendarExperienceReminderDate"));
        assertTrue(js.contains("calendarExperienceRemindersForDate"));
        assertTrue(js.contains("toUpperCase() === \"IMPORTANT_DAY\""));
        assertTrue(js.contains("calendarAllDayHead"));
        assertTrue(js.contains("dutylog.calendar.mode.v1"));
        assertTrue(js.contains("calendarExperienceBaseRenderCalendar"));
        assertTrue(js.contains("calendarExperienceOpenLegacyDetails"));
        assertTrue(js.contains("function calendarExperienceVisualEnd(event)"));
        assertTrue(js.contains("laneEnds[lane] = calendarExperienceVisualEnd(event);"));
        assertTrue(js.contains("[range, event.meta].filter(Boolean).join(\" · \")"));
        String calendar = read("src/main/resources/static/js/30-calendar.js");
        assertTrue(calendar.contains("if ($(\"impDate\")) $(\"impDate\").value = k;"));
    }

    @Test
    void designSystemProvidesScrollableWeekStripAndHourlyTimeline() throws Exception {
        String css = read("src/main/resources/static/design-system.css");
        String appCss = read("src/main/resources/static/app.css");
        assertTrue(css.contains(".calendarModeSwitch"));
        assertTrue(css.contains(".calendarWeekStrip"));
        assertTrue(css.contains("scroll-snap-type: x mandatory"));
        assertTrue(css.contains(".calendarTimelineEvent"));
        assertTrue(css.contains(".calendarNowLine"));
        assertTrue(css.contains(".calendarAllDayHead"));
        assertTrue(css.contains(".calendarAllDayItems"));
        assertTrue(css.contains(".calendarTimelineEvent.isCompact"));
        assertTrue(css.contains("min-height: 48px"));
        assertTrue(css.contains("height: max(48px, calc(var(--duration) * 1%))"));
        assertTrue(appCss.contains("container-name:day-notes"));
        assertTrue(appCss.contains("@container day-notes (max-width:720px)"));
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
