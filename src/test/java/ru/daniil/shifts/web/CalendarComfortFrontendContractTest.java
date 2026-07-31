package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CalendarComfortFrontendContractTest {

    @Test
    void todayReturnIsContextualAcrossMonthWeekAndDay() throws IOException {
        String html = resource("/static/index.html");
        String experience = resource("/static/js/37-calendar-experience.js");
        String css = resource("/static/design-system.css");
        assertTrue(html.contains("id=\"todayBtn\" type=\"button\" hidden"));
        assertTrue(experience.contains("function calendarExperienceIsAtToday()")
                && experience.contains("route === \"calendar\" && !calendarExperienceIsAtToday()")
                && experience.contains("button.hidden = !visible")
                && experience.contains("Вернуться к сегодняшнему дню"));
        assertTrue(css.contains(".nav #todayBtn:not([hidden])")
                && css.contains("content: \"↺\""));
    }

    @Test
    void selectedCalendarDateOwnsTheImportantDayDraft() throws IOException {
        String calendar = resource("/static/js/30-calendar.js");
        String tasks = resource("/static/js/50-tasks.js");
        assertTrue(calendar.contains("syncImportantSelectedDate(k)"));
        assertTrue(tasks.contains("function syncImportantSelectedDate(key = state.selected)")
                && tasks.contains("syncImportantSelectedDate(state.selected)")
                && tasks.contains("event.currentTarget.open && state.selected"));
    }

    @Test
    void overnightTodayCardSeparatesTimeFromTheTwoDates() throws IOException {
        String html = resource("/static/index.html");
        String today = resource("/static/js/35-today.js");
        String css = resource("/static/design-system.css");
        assertTrue(html.contains("id=\"todayShiftDateRange\" hidden"));
        assertTrue(today.contains("return `${startTime}–${endTime}`")
                && today.contains("function todayDashboardDateRange(occurrence)")
                && today.contains("card.classList.toggle(\"isOvernight\", !!model.dateRange)"));
        assertTrue(css.contains(".todayShiftDateRange")
                && css.contains(".todayShiftCard.isOvernight .todayShiftTime"));
    }

    @Test
    void refreshKeepsTheRenderedCalendarAndCapturesPerformanceMetrics() throws IOException {
        String core = resource("/static/js/10-core.js");
        String calendar = resource("/static/js/30-calendar.js");
        String boot = resource("/static/js/70-user-boot.js");
        assertTrue(core.contains("calendarHasRendered:false") && core.contains("calendarLoadMetrics:[]"));
        assertTrue(calendar.contains("const refreshing = loading && !!state.ui?.calendarHasRendered")
                && calendar.contains("if (refreshing)")
                && calendar.contains("calendarMonthExperience")
                && calendar.contains("Обновляю календарь…"));
        assertTrue(boot.contains("function recordCalendarLoadMetric")
                && boot.contains("dutylog:calendar-load")
                && boot.contains("slow calendar load"));
    }

    @Test
    void importantCheckboxesAndCompanionLayersUseCompactDesignSystemControls() throws IOException {
        String layers = resource("/static/js/38-schedule-layers.js");
        String css = resource("/static/design-system.css");
        assertTrue(layers.contains("bar.dataset.label = t(\"Слои\")")
                && layers.contains("layer.visible ? \"●\" : \"○\"")
                && layers.contains("button.setAttribute(\"aria-label\""));
        assertTrue(css.contains(".importantReminderSet input[type=\"checkbox\"]")
                && css.contains("width: 18px !important")
                && css.contains(".calendarLayerToggle")
                && css.contains("border-radius: 999px"));
    }

    private static String resource(String path) throws IOException {
        try (var in = CalendarComfortFrontendContractTest.class.getResourceAsStream(path)) {
            if (in == null) throw new IOException("Missing classpath resource: " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
