package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnifiedTimeCompensationLedgerContractTest {

    @Test
    void v43MigratesStandaloneTimeOffIntoTheCanonicalFifoLedger() throws IOException {
        String sql = source("src/main/resources/db/migration/postgresql/V43__unified_time_compensation_ledger.sql");
        assertTrue(sql.contains("compensation_policy"));
        assertTrue(sql.contains("source_kind"));
        assertTrue(sql.contains("source_absence_id"));
        assertTrue(sql.contains("Начальный баланс отгулов — перенос в единый банк V43"));
        assertTrue(sql.contains("TIMESTAMPTZ '1970-01-01 00:00:00+00'"));
        assertTrue(sql.contains("'UTC'"));
        assertTrue(sql.contains("'ABSENCE'"));
        assertTrue(sql.contains("UPDATE vacation_settings SET time_off_balance_minutes = 0"));
        assertTrue(sql.contains("ON DELETE CASCADE"));
    }

    @Test
    void absenceOwnsItsReversibleFifoUsageAndManualEndpointsCannotMutateIt() throws IOException {
        String overtime = source("src/main/java/ru/daniil/shifts/service/OvertimeService.java");
        String vacation = source("src/main/java/ru/daniil/shifts/service/VacationPlannerService.java");
        assertTrue(overtime.contains("upsertLinkedAbsenceUsage"));
        assertTrue(overtime.contains("deleteLinkedAbsenceUsage"));
        assertTrue(overtime.contains("LINKED_USAGE_MANAGED_BY_ABSENCE"));
        assertTrue(overtime.contains("usage.setSourceKind(\"ABSENCE\")"));
        assertTrue(vacation.contains("syncLinkedOvertimeUsage"));
        assertTrue(vacation.contains("OVERTIME_BALANCE_EXCEEDED"));
        assertTrue(vacation.contains("overtimeService.deleteLinkedAbsenceUsage"));
    }

    @Test
    void allocationRepairRunsInsideWritableTransactions() throws IOException {
        String overtime = source("src/main/java/ru/daniil/shifts/service/OvertimeService.java");
        assertTrue(overtime.contains("private void ensureAllocationConsistency"));
        assertFalse(overtime.contains("@Transactional(readOnly = true)\n    public OvertimeAccountPageDto accountPage"));
        assertFalse(overtime.contains("@Transactional(readOnly = true)\n    public byte[] exportAccountCsv"));
        assertFalse(overtime.contains("@Transactional(readOnly = true)\n    public OvertimeSummaryDto summary"));
    }

    @Test
    void readModelExposesPlanFactCompensationWithoutMoneyRules() throws IOException {
        String service = source("src/main/java/ru/daniil/shifts/service/TimeCompensationService.java");
        String controller = source("src/main/java/ru/daniil/shifts/web/TimeCompensationController.java");
        assertTrue(service.contains("TimeCompensationSummaryDto"));
        assertTrue(service.contains("compensatedByDate"));
        assertTrue(service.contains("plannedMinutes - Math.min(plannedMinutes, absenceMinutes)"));
        assertTrue(controller.contains("/api/time-compensation"));
        assertTrue(controller.contains("CacheControl.noStore()"));
        assertFalse(service.toLowerCase().contains("salary"));
    }

    @Test
    void frontendChoosesCoverageSourceAndShowsOneMonthlyLedger() throws IOException {
        String html = resource("/static/index.html");
        String planner = resource("/static/js/39-vacation-planner.js");
        String overtime = resource("/static/js/40-overtime.js");
        String data = resource("/static/js/20-data.js");
        assertTrue(html.contains("id=\"vacationCompensation\""));
        assertTrue(html.contains("value=\"OVERTIME_BANK\""));
        assertTrue(html.contains("id=\"timeCompensationCard\""));
        assertTrue(planner.contains("compensationPolicy:"));
        assertTrue(planner.contains("absenceCompensationLabel"));
        assertTrue(planner.contains("syncVacationCompensation({ preserve:true });"));
        assertFalse(planner.contains("timeOffBalanceHours:Number"));
        assertTrue(data.contains("/api/time-compensation"));
        assertTrue(overtime.contains("usageManagedByAbsence"));
        assertTrue(overtime.contains("Управляется отсутствием"));
    }

    @Test
    void serviceFixturesUseTheCompensationAwareAbsenceCreateConstructor() throws IOException {
        String tests = source("src/test/java/ru/daniil/shifts/service/VacationPlannerServiceTest.java")
                .replaceAll("\\s+", " ");
        assertTrue(tests.contains("\"Врач\", \"2026-08-06\", \"2026-08-06\", \"APPROVED\", null, \"PARTIAL\", \"09:00\", \"13:00\", \"OVERTIME_BANK\""));
        assertTrue(tests.contains("\"Документы\", \"2026-08-06\", \"2026-08-06\", \"PLANNED\", null, \"PARTIAL\", \"14:00\", \"16:00\", \"OVERTIME_BANK\""));
        assertTrue(tests.contains("\"Полный отгул\", \"2026-08-08\", \"2026-08-08\", \"APPROVED\", null, \"FULL_DAY\", null, null, \"OVERTIME_BANK\""));
        assertFalse(tests.contains("\"PARTIAL\", \"09:00\", \"13:00\"));"));
        assertFalse(tests.contains("\"FULL_DAY\", null, null));"));
    }

    @Test
    void canonicalLineageRecoveryKeepsV41ThroughV43AndWorkspaceRoute() throws IOException {
        String calendarSync = source("src/main/resources/static/js/55-calendar-sync.js");
        String calendarComfort = source("e2e/calendar-comfort.spec.js");
        String mobileLayout = source("e2e/mobile-layout.spec.js");
        String taskModules = source("e2e/task-modules.spec.js");

        assertTrue(Files.isRegularFile(Path.of("src/main/resources/db/migration/postgresql/V41__calendar_feed_subscriptions.sql")));
        assertTrue(Files.isRegularFile(Path.of("src/main/resources/db/migration/postgresql/V42__absence_time_off_overhaul.sql")));
        assertTrue(Files.isRegularFile(Path.of("src/main/resources/db/migration/postgresql/V43__unified_time_compensation_ledger.sql")));
        assertFalse(calendarSync.contains("localDateKey("));
        assertTrue(calendarComfort.contains("await page.locator('#pClose').click();"));
        assertFalse(calendarComfort.contains("force: true"));
        assertTrue(mobileLayout.contains("await openView(page, 'tasks');"));
        assertTrue(mobileLayout.contains("await expect(page.locator('#view-tasks')).toBeVisible();"));
        assertTrue(taskModules.contains("page.locator('#view-tasks')"));
        assertFalse(taskModules.contains("page.locator('#tabbar a[data-view=\"tasks\"]')"));
    }

    @Test
    void openApiKeepsTheAdditiveV1LedgerContract() throws IOException {
        String yaml = resource("/static/openapi/dutylog-v1.yaml");
        assertTrue(yaml.contains("/api/v1/time-compensation:"));
        assertTrue(yaml.contains("TimeCompensationSummary:"));
        assertTrue(yaml.contains("compensationPolicy:"));
        assertTrue(yaml.contains("sourceAbsenceId:"));
    }

    private static String source(String path) throws IOException {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }

    private static String resource(String path) throws IOException {
        try (var input = UnifiedTimeCompensationLedgerContractTest.class.getResourceAsStream(path)) {
            if (input == null) throw new IOException("Missing classpath resource: " + path);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
