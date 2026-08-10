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
        String dataJs = resource("/static/js/20-data.js");

        int apply = calendarJs.indexOf("api.applyScheduleTemplate(template.id, payload)");
        int freshReload = calendarJs.indexOf("await loadMonth({ fresh:true })", apply);
        assertTrue(apply >= 0 && freshReload > apply,
                "после применения серверного шаблона календарь должен запрашивать свежий месяц");
        int loadMonth = bootJs.indexOf("async function loadMonth(opts = {})");
        int dataLayerCall = bootJs.indexOf("dataLayer.loadCalendar(requestedYear, requestedMonth", loadMonth);
        int freshForwarding = bootJs.indexOf("{ fresh:!!opts.fresh }", dataLayerCall);
        assertTrue(loadMonth >= 0 && dataLayerCall > loadMonth && freshForwarding > dataLayerCall,
                "month loader должен передавать fresh-флаг в data layer");

        int loadCalendar = dataJs.indexOf("async loadCalendar(y, m, applyBundle, opts = {})");
        int dataFresh = dataJs.indexOf("const fresh = !!opts.fresh;", loadCalendar);
        int monthApi = dataJs.indexOf("api.month(y, m, { fresh })", dataFresh);
        assertTrue(loadCalendar >= 0 && dataFresh > loadCalendar && monthApi > dataFresh,
                "data layer должен доводить fresh-флаг до authoritative month API");

        assertTrue(bootJs.contains("calendarLoadGeneration"),
                "переключение месяцев должно иметь generation guard от поздних ответов");
        assertTrue(bootJs.contains("window.addEventListener(\"pagehide\"")
                        && bootJs.contains("function expectedPageLifecycleFetchAbort(error)")
                        && bootJs.contains("if (expectedPageLifecycleFetchAbort(err)) return;"),
                "reload/navigation должен отличать ожидаемый lifecycle fetch-abort от реальной сетевой ошибки");
        assertTrue(bootJs.contains("state.y !== requestedYear") && bootJs.contains("state.m !== requestedMonth"),
                "ответ другого месяца не должен применяться к текущей сетке");
        assertTrue(bootJs.contains("applyCalendarBundle(bundle);")
                        && bootJs.contains("renderNotifications();")
                        && bootJs.contains("renderCalendar();"),
                "каждый принятый bundle, включая сетевой ответ после cache-hit, должен быть отрисован");

        assertTrue(dataJs.contains("snap.y === y") && dataJs.contains("snap.m === m"),
                "IndexedDB snapshot можно применять только к тому месяцу, для которого он сохранён");
        assertTrue(dataJs.contains("date: day.date ?? null"),
                "module-aware sanitizer обязан сохранять date, иначе все дни схлопываются в state.days[undefined]");
        assertTrue(dataJs.contains("version: Number.isFinite(Number(day.version))")
                        && dataJs.contains("updatedAt: day.updatedAt ?? null"),
                "snapshot должен сохранять sync metadata дня");
        assertTrue(dataJs.contains("cache:fresh ? \"no-store\" : undefined")
                        && dataJs.contains("cache: opts.cache"),
                "fresh calendar reload должен обходить HTTP-кэш браузера");
    }

    private static String resource(String path) throws IOException {
        try (var in = CalendarMonthReloadContractTest.class.getResourceAsStream(path)) {
            if (in == null) throw new IOException("Missing classpath resource: " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
