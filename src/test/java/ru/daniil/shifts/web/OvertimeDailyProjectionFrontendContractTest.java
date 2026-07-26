package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class OvertimeDailyProjectionFrontendContractTest {

    private static String resource(String relative) throws Exception {
        return Files.readString(Path.of("src/main/resources/static").resolve(relative));
    }

    @Test
    void ledgerUsesProjectedDayAndKeepsSourceTotalsForEditingAndDeletion() throws Exception {
        String overtime = resource("js/40-overtime.js");

        assertTrue(overtime.contains("function overtimeProjection(credit)"));
        assertTrue(overtime.contains("sourceWorkedDate"));
        assertTrue(overtime.contains("sourceCreditHours"));
        assertTrue(overtime.contains("sourceUsedHours"));
        assertTrue(overtime.contains("projection.sourceUsedHours <= 0.0001"));
    }

    @Test
    void ledgerShowsOneTimezoneAwareSubtotalForEveryProjectedDay() throws Exception {
        String overtime = resource("js/40-overtime.js");
        String css = resource("app.css");

        assertTrue(overtime.contains("function overtimeDaySummaryHtml(credit)"));
        assertTrue(overtime.contains("dayEarnedHours"));
        assertTrue(overtime.contains("dayUsedHours"));
        assertTrue(overtime.contains("dayRemainingHours"));
        assertTrue(css.contains(".overtimeDaySummary"));
        assertTrue(css.contains(".overtimeProjectionBadge"));
    }
}
