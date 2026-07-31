package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class VacationPlannerFrontendContractTest {

    @Test
    void shellLoadsVacationCompositionBetweenCalendarLayersAndOvertime() throws IOException {
        String html = resource("/static/index.html");
        int layers = html.indexOf("js/38-schedule-layers.js?v=");
        int vacation = html.indexOf("js/39-vacation-planner.js?v=");
        int overtime = html.indexOf("js/40-overtime.js?v=");
        assertTrue(layers >= 0 && vacation > layers && overtime > vacation,
                "vacation composition must load after calendar layers and before overtime");
        assertTrue(html.contains("id=\"view-vacation\"")
                        && html.contains("id=\"vacationPeriodForm\"")
                        && html.contains("id=\"accVacation\"")
                        && html.contains("data-vacation-days=\"14\"")
                        && html.contains("data-vacation-days=\"28\"")
                        && html.contains("data-vacation-days=\"35\""));
    }

    @Test
    void apiAndStateKeepAbsencesSeparateFromShifts() throws IOException {
        String core = resource("/static/js/10-core.js");
        String data = resource("/static/js/20-data.js");
        String vacation = resource("/static/js/39-vacation-planner.js");
        assertTrue(core.contains("absenceOccurrences") && core.contains("vacationPlanner"));
        assertTrue(data.contains("vacationPlanner(")
                        && data.contains("previewAbsence")
                        && data.contains("createAbsence")
                        && data.contains("bundle.absences"));
        assertTrue(vacation.contains("loadVacationPlanner")
                        && vacation.contains("renderVacationDay")
                        && vacation.contains("VACATION_LIMIT_EXCEEDED")
                        && vacation.contains("TIME_OFF_LIMIT_EXCEEDED")
                        && vacation.contains("coverage:partial ? \"PARTIAL\" : \"FULL_DAY\"")
                        && !vacation.contains("shiftTypeId:"),
                "absence editor must never serialize a vacation as a shift type");
    }

    @Test
    void calendarComposesVacationIntoMonthWeekDayAndFocusedDetails() throws IOException {
        String calendar = resource("/static/js/30-calendar.js");
        String experience = resource("/static/js/37-calendar-experience.js");
        String css = resource("/static/design-system.css");
        assertTrue(calendar.contains("absencesOf(k)")
                        && calendar.contains("factualAbsence")
                        && calendar.contains("absenceFact")
                        && calendar.contains("plannedShiftGhost")
                        && calendar.contains("partialAbsenceBar")
                        && calendar.contains("absenceSummaryChip"));
        assertTrue(experience.contains("facts.absences")
                        && experience.contains("for (const absence of facts.absences)")
                        && experience.contains("type:\"vacation\"")
                        && experience.contains("editAbsenceFromOccurrence"));
        assertTrue(css.contains(".vacationSummaryGrid")
                        && css.contains(".cell.hasAbsenceFact")
                        && css.contains(".vacationDayPlanFact")
                        && css.contains("@media (max-width: 560px)"));
    }

    private static String resource(String path) throws IOException {
        try (var in = VacationPlannerFrontendContractTest.class.getResourceAsStream(path)) {
            if (in == null) throw new IOException("Missing classpath resource: " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
