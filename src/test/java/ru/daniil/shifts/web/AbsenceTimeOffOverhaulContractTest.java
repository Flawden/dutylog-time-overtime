package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbsenceTimeOffOverhaulContractTest {

    @Test
    void plannerFormExposesFullAndPartialCoverageWithSeparateBalances() throws IOException {
        String html = resource("/static/index.html");
        assertTrue(html.contains("id=\"vacationCoverage\""));
        assertTrue(html.contains("value=\"FULL_DAY\""));
        assertTrue(html.contains("value=\"PARTIAL\""));
        assertTrue(html.contains("id=\"timeOffBalanceHours\""));
        assertTrue(html.contains("id=\"defaultTimeOffDayHours\""));
        assertTrue(html.contains("id=\"vacationTypeBalance\""));
    }

    @Test
    void editorSendsCoverageAndTimesButNeverRewritesShiftType() throws IOException {
        String js = resource("/static/js/39-vacation-planner.js");
        assertTrue(js.contains("const coverage = $(\"vacationCoverage\")?.value || \"FULL_DAY\";"));
        assertTrue(js.contains("coverage,"));
        assertTrue(js.contains("coverage === \"HOURS_ONLY\""));
        assertTrue(js.contains("startTime:partial"));
        assertTrue(js.contains("endTime:partial"));
        assertTrue(js.contains("TIME_OFF_LIMIT_EXCEEDED"));
        assertFalse(js.contains("shiftTypeId:"));
    }

    @Test
    void monthCalendarRendersActualAbsenceAndKeepsScheduledShiftAsContext() throws IOException {
        String calendar = resource("/static/js/30-calendar.js");
        assertTrue(calendar.contains("item.coverage === \"FULL_DAY\" && item.replacesShift"));
        assertTrue(calendar.contains("actual.className = \"absenceFact\""));
        assertTrue(calendar.contains("planned.className = \"plannedShiftGhost\""));
        assertTrue(calendar.contains("partial.className = \"partialAbsenceBar\""));
    }

    @Test
    void weekAndDayViewsShareTheSamePlanFactProjection() throws IOException {
        String experience = resource("/static/js/37-calendar-experience.js");
        assertTrue(experience.contains("factualAbsence"));
        assertTrue(experience.contains("partialAbsences"));
        assertTrue(experience.contains("plannedShiftName"));
        assertTrue(experience.contains("event.absence"));
    }

    @Test
    void previewLoopSnapshotsItsMutableDateBeforeTheOverlapLambda() throws IOException {
        String service = Files.readString(
                Path.of("src/main/java/ru/daniil/shifts/service/VacationPlannerService.java"),
                StandardCharsets.UTF_8
        );
        assertTrue(service.contains("LocalDate previewDate = date;"));
        assertTrue(service.contains("filter(period -> covers(period, previewDate))"));
        assertFalse(service.contains("filter(period -> covers(period, date))"));
    }

    @Test
    void visualHierarchyGivesAbsenceTheWeightOfAShift() throws IOException {
        String css = resource("/static/design-system.css");
        assertTrue(css.contains(".cell.hasAbsenceFact"));
        assertTrue(css.contains(".cell .absenceFact"));
        assertTrue(css.contains(".cell .plannedShiftGhost"));
        assertTrue(css.contains(".cell .partialAbsenceBar"));
        assertTrue(css.contains(".absenceSummaryChip"));
    }

    private static String resource(String path) throws IOException {
        try (var in = AbsenceTimeOffOverhaulContractTest.class.getResourceAsStream(path)) {
            if (in == null) throw new IOException("Missing classpath resource: " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
