package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionCalendarFoundationContractTest {

    @Test
    void productionCalendarStaysBetweenScheduleNormAndMoneyRules() throws IOException {
        String migration = resource("/db/migration/postgresql/V49__production_calendar_foundation.sql");
        String service = source("src/main/java/ru/daniil/shifts/service/ProductionCalendarService.java");
        String norm = source("src/main/java/ru/daniil/shifts/service/WorkNormService.java");
        String controller = source("src/main/java/ru/daniil/shifts/web/ProductionCalendarController.java");
        String payroll = source("src/main/java/ru/daniil/shifts/service/PayrollService.java");
        String time = source("src/main/java/ru/daniil/shifts/service/TimeCompensationService.java");
        String component = source("frontend/src/features/payroll/components/PayrollWorkspace.vue");
        String dayPanel = source("frontend/src/features/calendar-timeline/components/NativeWorkdayCard.vue");
        String calendarPage = source("frontend/src/features/calendar-timeline/components/CalendarPage.vue");
        String workday = source("src/main/java/ru/daniil/shifts/service/WorkdayTruthService.java");
        String api = source("frontend/src/features/calendar-timeline/api/calendarTimelineApi.ts");
        String openapi = resource("/static/openapi/dutylog-v1.yaml");

        assertTrue(migration.contains("layer IN ('BASE', 'LOCAL_OVERRIDE')"));
        assertTrue(migration.contains("schedule_effect IN ('NONE', 'NORM_OVERRIDE')"));
        assertTrue(migration.contains("payroll_effect IN ('NONE', 'HOLIDAY')"));
        assertFalse(migration.toUpperCase().contains("DROP TABLE"));

        assertTrue(service.contains("LOCAL_OVERRIDE"));
        assertTrue(service.contains("periodLocks.assertOpen(user, date)"));
        assertTrue(service.contains("productionMinutes - baseMinutes"));
        assertTrue(norm.contains("basePlannedMinutes"));
        assertTrue(time.contains("productionCalendar.requiredMinutes(user, date"));
        assertFalse(time.contains("private int plannedMinutes(DayEntry entry)"));

        assertTrue(controller.contains("/api/v1/production-calendar"));
        assertTrue(controller.contains("CacheControl.noStore()"));
        assertTrue(payroll.contains("ProductionCalendarMonthDto productionCalendarMonth"));
        assertTrue(component.contains("data-production-calendar-foundation"));
        assertTrue(component.contains("data-production-calendar-summary-only"));
        assertFalse(component.contains("productionCalendarForm"));
        assertTrue(dayPanel.contains("data-native-workday-truth"));
        assertTrue(dayPanel.contains("data-native-special-day-editor"));
        assertTrue(dayPanel.contains("data-native-actual-work-editor"));
        assertTrue(calendarPage.contains("data-production-calendar-day"));
        assertTrue(workday.contains("class WorkdayTruthService"));
        assertTrue(api.contains("workdayTruth"));
        assertTrue(api.contains("upsertProductionCalendarDay"));
        assertTrue(api.contains("createActualWorkInterval"));

        assertTrue(openapi.contains("/api/v1/production-calendar/months/{month}:"));
        assertTrue(openapi.contains("ProductionCalendarMonth:"));
        assertTrue(openapi.contains("/api/v1/workdays/{date}:"));
        assertTrue(openapi.contains("WorkdayTruth:"));
        assertTrue(openapi.contains("scheduleEffect:"));
        assertTrue(openapi.contains("payrollEffect:"));
    }

    private static String source(String path) throws IOException {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }

    private static String resource(String path) throws IOException {
        try (var input = ProductionCalendarFoundationContractTest.class.getResourceAsStream(path)) {
            if (input == null) throw new IOException("Missing classpath resource: " + path);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
