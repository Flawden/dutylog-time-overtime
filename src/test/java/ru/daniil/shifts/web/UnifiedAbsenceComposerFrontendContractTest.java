package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnifiedAbsenceComposerFrontendContractTest {

    private static String resource(String path) throws Exception {
        return Files.readString(Path.of("src/main/resources/static").resolve(path), StandardCharsets.UTF_8);
    }

    @Test
    void oneComposerOwnsVacationTimeOffSickAndUnpaidEntryPoints() throws Exception {
        String html = resource("index.html");
        String modules = resource("js/20-data.js");
        String vacation = resource("js/39-vacation-planner.js");
        String overtime = resource("js/40-overtime.js");
        String tasks = resource("js/50-tasks.js");

        assertTrue(html.contains("id=\"absenceComposerModal\""));
        assertTrue(html.contains("id=\"absenceComposerModalMount\""));
        assertTrue(html.contains("Оформить отсутствие"));
        assertTrue(html.contains("отпуск, отгул, больничный"));
        assertTrue(html.contains("id=\"vacationTitle\" maxlength=\"120\"") && html.contains("required"));
        assertTrue(occurrences(html, "id=\"vacationPeriodForm\"") == 1);
        assertTrue(vacation.contains("function openAbsenceComposer("));
        assertTrue(vacation.contains("systemCode = null"));
        assertTrue(vacation.contains("renderAbsenceComposerContext"));
        assertTrue(overtime.contains("systemCode:\"TIME_OFF\""));
        assertTrue(overtime.contains("source:\"overtime\""));
    }

    @Test
    void composerExplainsCoverageAndCalendarProjectionWithoutDeletingThePlan() throws Exception {
        String vacation = resource("js/39-vacation-planner.js");
        String calendar = resource("js/30-calendar.js");
        String experience = resource("js/37-calendar-experience.js");
        String css = resource("app.css");

        assertTrue(vacation.contains("VACATION_ALLOWANCE"));
        assertTrue(vacation.contains("OVERTIME_BANK"));
        assertTrue(vacation.contains("SICK_PAY"));
        assertTrue(vacation.contains("UNPAID"));
        assertTrue(vacation.contains("function absenceGlyph(value)"));
        assertTrue(calendar.contains("plannedShiftGhost"));
        assertTrue(calendar.contains("partialAbsenceBar"));
        assertTrue(calendar.contains("dataset.absenceStatus"));
        assertTrue(experience.contains("absenceGlyph(absence)"));
        assertTrue(css.contains(".absenceComposerContext"));
        assertTrue(css.contains("[data-absence-status=\"planned\"]"));
        assertFalse(vacation.contains("delete planned shift"));
    }

    private static int occurrences(String source, String token) {
        int count = 0;
        int offset = 0;
        while ((offset = source.indexOf(token, offset)) >= 0) {
            count++;
            offset += token.length();
        }
        return count;
    }
}
