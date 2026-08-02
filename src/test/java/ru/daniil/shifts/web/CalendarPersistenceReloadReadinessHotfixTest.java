package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CalendarPersistenceReloadReadinessHotfixTest {

    @Test
    void calendarReloadWaitsForNavigationAndLedgerReadModelsWithoutSuppressingFailures() throws IOException {
        String settings = resource("/static/js/60-settings.js");
        String experience = resource("/static/js/37-calendar-experience.js");
        String helpers = source("e2e/helpers.js");
        String persistence = source("e2e/calendar-persistence.spec.js");
        String fixture = source("e2e/fixtures.js");

        assertTrue(settings.contains("window.__dutylogCalendarNavigationReady = Promise.resolve()"));
        assertTrue(settings.contains("function trackCalendarNavigation(operation)"));
        assertTrue(settings.contains("trackCalendarNavigation(goto(state.y, state.m - 1))"));
        assertTrue(settings.contains("trackCalendarNavigation(goto(state.y, state.m + 1))"));
        assertTrue(experience.contains("trackCalendarNavigation(operation)"));

        assertTrue(helpers.contains("async function waitForCalendarNavigationReady(page)"));
        assertTrue(helpers.contains("window.__dutylogCalendarNavigationReady"));
        assertTrue(helpers.contains("window.__dutylogLedgerReady"));
        assertTrue(helpers.contains("await waitForAppIdle(page)"));

        assertTrue(persistence.contains("await waitForCalendarNavigationReady(page)"));
        assertTrue(persistence.contains("await waitForAppIdle(page);\n  const authoritativeReload"));
        assertTrue(persistence.contains("await waitForAppIdle(page);\n  const reloadAfterDelete"));

        assertTrue(fixture.contains("expect.soft(issues, 'Browser console, page and same-origin HTTP failures').toEqual([])"));
        assertFalse(fixture.contains("Failed to load actual work TypeError: Failed to fetch"));
        assertFalse(fixture.contains("Failed to load time compensation summary TypeError: Failed to fetch"));
    }

    private static String source(String path) throws IOException {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }

    private static String resource(String path) throws IOException {
        try (var input = CalendarPersistenceReloadReadinessHotfixTest.class.getResourceAsStream(path)) {
            if (input == null) throw new IOException("Missing classpath resource: " + path);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
