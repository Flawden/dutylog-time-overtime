package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Static regression guards for the browser-side schedule template definitions. */
class ScheduleTemplateFrontendContractTest {

    @Test
    void everySupportedTemplateKeepsItsCanonicalShiftSequence() throws IOException {
        String core = resource("/static/js/10-core.js");

        assertTrue(core.contains("\"2x2-day\": { label:\"2 через 2\", names:[\"Дневная\",\"Дневная\",\"Выходной\",\"Выходной\"] }"));
        assertTrue(core.contains("\"day-night-48\": { label:\"День / ночь / 48\", names:[\"Дневная\",\"Ночная\",\"Выходной\",\"Выходной\"] }"));
        assertTrue(core.contains("names:[\"Дневная\",\"Дневная\",\"Дневная\",\"Дневная\",\"Дневная\",\"Выходной\",\"Выходной\"]")
                && core.contains("weekly:true"));
        assertTrue(core.contains("\"1x3-day\": { label:\"День / 72\", names:[\"Дневная\",\"Выходной\",\"Выходной\",\"Выходной\"] }"));
        assertTrue(core.contains("\"1x3-night\": { label:\"Ночь / 72\", names:[\"Ночная\",\"Выходной\",\"Выходной\",\"Выходной\"] }"));
    }

    @Test
    void weeklyTemplateIsRotatedBySelectedWeekdayAndTheEffectiveSequenceIsSentToTheServer() throws IOException {
        String core = resource("/static/js/10-core.js");
        String calendar = resource("/static/js/30-calendar.js");

        assertTrue(core.contains("function weekdayIndex(k)")
                        && core.contains("return (new Date(y, m - 1, d).getDay() + 6) % 7"),
                "weekday mapping must remain Monday=0 through Sunday=6");
        assertTrue(core.contains("function effectiveTemplateNames(tpl, startDateKey)")
                        && core.contains("const offset = weekdayIndex(startDateKey)")
                        && core.contains("return tpl.names.slice(offset).concat(tpl.names.slice(0, offset))"),
                "five-day pattern must rotate to the selected weekday");
        assertTrue(calendar.contains("const names = effectiveTemplateNames(tpl, k)")
                        && calendar.contains("shiftTypeIds: shifts.map(s => s.id)"),
                "the rotated sequence, not the unrotated preset, must be posted to /api/days/fill");
    }

    private static String resource(String path) throws IOException {
        try (var in = ScheduleTemplateFrontendContractTest.class.getResourceAsStream(path)) {
            if (in == null) throw new IOException("Missing classpath resource: " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
