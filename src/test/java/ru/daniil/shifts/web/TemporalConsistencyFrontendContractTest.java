package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guards the remaining cross-feature timezone consistency boundaries. */
class TemporalConsistencyFrontendContractTest {

    private static String resource(String relative) throws Exception {
        return Files.readString(Path.of("src/main/resources/static").resolve(relative));
    }

    @Test
    void calendarUsesOnlyProjectedOvertimeAndNeverFallsBackOnZero() throws Exception {
        String core = resource("js/10-core.js");
        String calendar = resource("js/30-calendar.js");

        assertTrue(core.contains("function overtimeDailyOf(k)"));
        assertTrue(core.contains("function overtimeRangeTotals(from, to)"));
        assertTrue(core.contains("numOr0(x.usedHours)"));
        assertTrue(calendar.contains("const bal = showOvertime ? ledgerNetOf(k) : 0;"));
        assertFalse(calendar.contains("legacyBal"));
        assertFalse(calendar.contains("Math.abs(ledgerBal) > 0.0001 ? ledgerBal"));
    }

    @Test
    void monthlySummaryAndEditorPreviewShareCanonicalProjection() throws Exception {
        String calendar = resource("js/30-calendar.js");
        String overtime = resource("js/40-overtime.js");
        String data = resource("js/20-data.js");

        assertTrue(calendar.contains("overtimeRangeTotals(monthStart, monthEnd)"));
        assertFalse(calendar.contains("overtime += numOr0(v.overtimeHours)"));
        assertTrue(data.contains("previewOvertimeCredit"));
        assertTrue(overtime.contains("runCanonicalOvertimePreview"));
        assertTrue(overtime.contains("preview.sourceTimezone"));
        assertFalse(overtime.contains("const start = new Date(startValue)"));
    }

    @Test
    void fixedTimeScenariosSupportSignedDayOffsets() throws Exception {
        String html = resource("index.html");
        String overtime = resource("js/40-overtime.js");

        assertTrue(html.contains("id=\"scEndDayOffset\""));
        assertTrue(html.contains("value=\"-2\""));
        assertTrue(html.contains("value=\"-1\""));
        assertTrue(html.contains("value=\"2\""));
        assertTrue(overtime.contains("function scenarioDayOffset(sc)"));
        assertTrue(overtime.contains("endDayOffset:"));
        assertTrue(overtime.contains("dateKeyOffset(base, scenarioDayOffset(sc))"));
    }
}
