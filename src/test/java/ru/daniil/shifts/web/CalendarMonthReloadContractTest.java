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

        assertTrue(calendarJs.contains("api.month(requestedYear, requestedMonth, { fresh:true })"),
                "после массового заполнения календарь должен читать сервер напрямую без IndexedDB-first");
        assertTrue(calendarJs.contains("applyCalendarBundle(bundle);")
                        && calendarJs.contains("dataLayer.writeSnapshot(bundle, requestedYear, requestedMonth)"),
                "подтверждённый серверный месяц должен заменить UI и локальный snapshot");
        assertTrue(bootJs.contains("calendarLoadGeneration"),
                "переключение месяцев должно иметь generation guard от поздних ответов");
        assertTrue(bootJs.contains("state.y !== requestedYear") && bootJs.contains("state.m !== requestedMonth"),
                "ответ другого месяца не должен применяться к текущей сетке");
        assertTrue(bootJs.contains("applyCalendarBundle(bundle);")
                        && bootJs.contains("renderNotifications();")
                        && bootJs.contains("renderCalendar();"),
                "каждый принятый bundle, включая сетевой ответ после cache-hit, должен быть отрисован");

        String dataJs = resource("/static/js/20-data.js");
        assertTrue(dataJs.contains("snap.y === y") && dataJs.contains("snap.m === m"),
                "IndexedDB snapshot можно применять только к тому месяцу, для которого он сохранён");
        assertTrue(dataJs.contains('cache:fresh ? "no-store" : undefined')
                        && dataJs.contains('cache: opts.cache'),
                "fresh calendar reload должен обходить HTTP-кэш браузера");
    }

    private static String resource(String path) throws IOException {
        try (var in = CalendarMonthReloadContractTest.class.getResourceAsStream(path)) {
            if (in == null) throw new IOException("Missing classpath resource: " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
