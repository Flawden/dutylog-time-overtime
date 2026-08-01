package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class LedgerIntegrityApprovalWorkflowContractTest {

    @Test
    void v44AddsWorkflowAuditPeriodsAndActualWorkWithoutTouchingDayEntries() throws IOException {
        String sql = source("src/main/resources/db/migration/postgresql/V44__ledger_integrity_approval_workflow.sql");
        assertTrue(sql.contains("DRAFT"));
        assertTrue(sql.contains("SUBMITTED"));
        assertTrue(sql.contains("CANCELLED"));
        assertTrue(sql.contains("posting_state"));
        assertTrue(sql.contains("CREATE TABLE time_ledger_entries"));
        assertTrue(sql.contains("CREATE TABLE time_accounting_periods"));
        assertTrue(sql.contains("CREATE TABLE actual_work_intervals"));
        assertTrue(sql.contains("reversal_of_id"));
        assertFalse(sql.contains("ALTER TABLE day_entries"));
        assertFalse(sql.contains("DROP TABLE"));
    }

    @Test
    void workflowSeparatesReservationPostingAndReversal() throws IOException {
        String ledger = source("src/main/java/ru/daniil/shifts/service/LedgerIntegrityService.java");
        String planner = source("src/main/java/ru/daniil/shifts/service/VacationPlannerService.java");
        assertTrue(ledger.contains("RESERVED_STATUSES"));
        assertTrue(ledger.contains("POSTED_STATUSES"));
        assertTrue(ledger.contains("ABSENCE_REVERSAL"));
        assertTrue(ledger.contains("assertRangeOpen"));
        assertTrue(ledger.contains("LEDGER_INTEGRITY_FAILED"));
        String overtime = source("src/main/java/ru/daniil/shifts/service/OvertimeService.java");
        String periodLocks = source("src/main/java/ru/daniil/shifts/service/AccountingPeriodLockService.java");
        String days = source("src/main/java/ru/daniil/shifts/service/DayEntryService.java");
        String schedules = source("src/main/java/ru/daniil/shifts/service/ScheduleTemplateService.java");
        String shiftTypes = source("src/main/java/ru/daniil/shifts/service/ShiftTypeService.java");
        assertTrue(overtime.contains("assertPeriodOpen(user"));
        assertTrue(overtime.contains("PERIOD_CLOSED"));
        assertTrue(periodLocks.contains("class AccountingPeriodLockService"));
        assertTrue(periodLocks.contains("PERIOD_CLOSED"));
        assertTrue(days.contains("periodLocks.assertOpen(user, d)"));
        assertTrue(schedules.contains("periodLocks.assertOpen(user, date)"));
        assertTrue(shiftTypes.contains("assignedEntries.forEach(entry -> periodLocks.assertOpen"));
        assertTrue(planner.contains("recordAbsenceTransition"));
        assertTrue(planner.contains("usagePostingState(period.getStatus())"));
        assertTrue(planner.contains("visibleAsFact(period.getStatus())"));
    }

    @Test
    void actualWorkAndIntegrityApisAreNoStoreAndV1Additive() throws IOException {
        String actual = source("src/main/java/ru/daniil/shifts/web/ActualWorkController.java");
        String integrity = source("src/main/java/ru/daniil/shifts/web/LedgerIntegrityController.java");
        String yaml = resource("/static/openapi/dutylog-v1.yaml");
        assertTrue(actual.contains("/api/v1/actual-work"));
        assertTrue(actual.contains("CacheControl.noStore()"));
        assertTrue(integrity.contains("/api/v1/ledger-integrity"));
        assertTrue(integrity.contains("CacheControl.noStore()"));
        assertTrue(yaml.contains("/api/v1/actual-work:"));
        assertTrue(yaml.contains("/api/v1/ledger-integrity:"));
        assertTrue(yaml.contains("LedgerIntegrity:"));
        assertTrue(yaml.contains("ActualWorkInterval:"));
    }

    @Test
    void frontendKeepsStrictHumanWorkflowWithoutBypassingModalOrState() throws IOException {
        String html = resource("/static/index.html");
        String planner = resource("/static/js/39-vacation-planner.js");
        String overtime = resource("/static/js/40-overtime.js");
        String data = resource("/static/js/20-data.js");
        assertTrue(html.contains("id=\"ledgerIntegrityCard\""));
        assertTrue(html.contains("id=\"actualWorkForm\""));
        assertTrue(html.contains("value=\"SUBMITTED\""));
        assertTrue(planner.contains("absencePostingLabel"));
        assertTrue(overtime.contains("loadLedgerIntegrity"));
        assertTrue(overtime.contains("saveActualWork"));
        assertTrue(data.contains("/api/ledger-integrity"));
        assertTrue(data.contains("/api/actual-work"));
        assertFalse(overtime.contains("force: true"));
    }

    private static String source(String path) throws IOException {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }

    private static String resource(String path) throws IOException {
        try (var input = LedgerIntegrityApprovalWorkflowContractTest.class.getResourceAsStream(path)) {
            if (input == null) throw new IOException("Missing classpath resource: " + path);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
