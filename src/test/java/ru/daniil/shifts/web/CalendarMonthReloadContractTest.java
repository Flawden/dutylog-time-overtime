package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lightweight regression guards for the browser month-navigation bug.
 * These assertions protect the two invariants that are easy to accidentally remove:
 * generated schedules are reloaded from the server, and stale month responses are ignored.
 */
class CalendarMonthReloadContractTest {

    @Test
    void scheduleFillReloadsServerStateAndMonthLoaderRejectsStaleResponses() throws IOException {
        String calendarJs = resource("/static/js/30-calendar.js");
        String bootJs = resource("/static/js/70-user-boot.js");

        assertTrue(calendarJs.contains("await loadMonth();"),
                "после массового заполнения календарь должен перечитать сохранённые данные");
        assertTrue(bootJs.contains("calendarLoadGeneration"),
                "переключение месяцев должно иметь generation guard от поздних ответов");
        assertTrue(bootJs.contains("state.y !== requestedYear") && bootJs.contains("state.m !== requestedMonth"),
                "ответ другого месяца не должен применяться к текущей сетке");
    }

    private static String resource(String path) throws IOException {
        try (var in = CalendarMonthReloadContractTest.class.getResourceAsStream(path)) {
            if (in == null) throw new IOException("Missing classpath resource: " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
