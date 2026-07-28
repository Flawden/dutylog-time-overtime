package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Static contract for the read-only Today Dashboard composition layer. */
class TodayDashboardFrontendContractTest {

    @Test
    void shellExposesTodayAsTheDefaultPrimaryDestinationWithoutRemovingCalendar() throws Exception {
        String html = read("src/main/resources/static/index.html");
        String boot = read("src/main/resources/static/js/70-user-boot.js");
        assertTrue(html.contains("data-view=\"today\" href=\"#today\""));
        assertTrue(html.contains("data-view=\"calendar\" href=\"#calendar\""));
        assertTrue(html.contains("id=\"view-today\""));
        assertTrue(html.contains("js/35-today.js?v=27.17.3"));
        assertTrue(boot.contains("dataset.shell === \"classic\" ? \"#calendar\" : \"#today\""));
        assertTrue(boot.contains("dataset.shell === \"classic\" && active === \"today\""));
        assertTrue(boot.contains("today:\"view-today\""));
    }

    @Test
    void dashboardComposesExistingShiftOvertimeTaskAndImportantDateStores() throws Exception {
        String js = read("src/main/resources/static/js/35-today.js");
        assertTrue(js.contains("state.shiftOccurrences"));
        assertTrue(js.contains("state.overtimeAccount"));
        assertTrue(js.contains("activeTasksOf(key)"));
        assertTrue(js.contains("state.importantDays"));
        assertTrue(js.contains("openTaskDetails"));
        assertTrue(js.contains("openOvertimeCreditModal"));
        assertTrue(js.contains("addEventListener(\"click\", () => openQuickActions())"));
        assertFalse(js.contains("addEventListener(\"click\", openQuickActions)"));
        assertFalse(js.contains("/api/today"));
    }

    @Test
    void liveShiftCardUsesImmutableInstantsForProgressAndCountdown() throws Exception {
        String js = read("src/main/resources/static/js/35-today.js");
        assertTrue(js.contains("Date.parse(item.startInstant)"));
        assertTrue(js.contains("Date.parse(item.endInstant)"));
        assertTrue(js.contains("todayDashboardCountdown"));
        assertTrue(js.contains("aria-valuenow"));
        assertTrue(js.contains("setInterval"));
    }

    @Test
    void designSystemProvidesResponsiveDashboardCardsAndMobileDateStrip() throws Exception {
        String css = read("src/main/resources/static/design-system.css");
        assertTrue(css.contains(".todayDashboardGrid"));
        assertTrue(css.contains(".todayQuickActions"));
        assertTrue(css.contains(".todayShiftCard[data-shift-state=\"active\"]"));
        assertTrue(css.contains("scroll-snap-type: x proximity"));
        assertTrue(css.contains("@media (max-width: 720px)"));
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
