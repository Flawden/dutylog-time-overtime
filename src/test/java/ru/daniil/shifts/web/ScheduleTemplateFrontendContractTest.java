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
    void authoritativeTemplatePreviewAndApplyKeepAlignmentOnTheServer() throws IOException {
        String data = resource("/static/js/20-data.js");
        String calendar = resource("/static/js/30-calendar.js");

        assertTrue(data.contains("async previewScheduleTemplate(id,b)")
                        && data.contains("/api/schedule-templates/${id}/preview")
                        && data.contains("async applyScheduleTemplate(id,b)")
                        && data.contains("/api/schedule-templates/${id}/apply"),
                "browser adapter must use the authoritative server preview/apply resources");
        assertTrue(calendar.contains("startDate:selected")
                        && calendar.contains("endDate:scheduleDateOffset(selected, count - 1)")
                        && calendar.contains("anchorDate:selected")
                        && calendar.contains("overwriteExistingShift:!!$(\"tplOverwrite\").checked"),
                "preview/apply payload must preserve range, anchor and explicit overwrite semantics");
        int preview = calendar.indexOf("const prepared = await previewScheduleTemplateSelection();");
        int apply = calendar.indexOf("api.applyScheduleTemplate(template.id, payload)", preview);
        assertTrue(preview >= 0 && apply > preview,
                "application must always follow the server preview instead of rotating a browser-only sequence");
    }

    private static String resource(String path) throws IOException {
        try (var in = ScheduleTemplateFrontendContractTest.class.getResourceAsStream(path)) {
            if (in == null) throw new IOException("Missing classpath resource: " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
