package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PayrollFoundationContractTest {

    @Test
    void payrollFoundationKeepsClosedPeriodMoneyAndUiContracts() throws IOException {
        String migration = resource("/db/migration/postgresql/V45__payroll_foundation.sql");
        String service = source("src/main/java/ru/daniil/shifts/service/PayrollService.java");
        String sourceProjection = source("src/main/java/ru/daniil/shifts/service/TimeCompensationService.java");
        String controller = source("src/main/java/ru/daniil/shifts/web/PayrollController.java");
        String modules = source("src/main/java/ru/daniil/shifts/module/DutyLogModules.java");
        String moduleKeys = source("src/main/java/ru/daniil/shifts/module/ModuleKeys.java");
        String html = resource("/static/index.html");
        String javascript = resource("/static/js/45-payroll.js");
        String data = resource("/static/js/20-data.js");
        String openapi = resource("/static/openapi/dutylog-v1.yaml");

        assertTrue(migration.contains("CREATE TABLE payroll_settings"));
        assertTrue(migration.contains("CREATE TABLE payroll_adjustments"));
        assertTrue(migration.contains("CREATE TABLE payroll_snapshots"));
        assertTrue(migration.contains("paid_absence_minutes"));
        assertTrue(migration.contains("uq_payroll_snapshot_revision"));
        assertFalse(migration.toUpperCase().contains("DROP TABLE"));

        assertTrue(service.contains("requireClosedPeriod(user, month, true)"));
        assertTrue(service.contains("ledgerIntegrity.inspect"));
        assertTrue(service.contains("timeCompensation.payrollSource"));
        assertTrue(service.contains("RoundingMode.HALF_UP"));
        assertTrue(service.contains("calculationHash"));
        assertTrue(service.contains("previous.supersedeWith(created)"));
        assertTrue(sourceProjection.contains("Canonical posted-only source for money calculation"));
        assertTrue(sourceProjection.contains("APPROVED"));
        assertTrue(sourceProjection.contains("COMPLETED"));

        assertTrue(controller.contains("@RequestMapping({\"/api/payroll\", \"/api/v1/payroll\"})"));
        assertTrue(controller.contains("CacheControl.noStore()"));
        assertTrue(moduleKeys.contains("public static final String PAYROLL = \"payroll\";"));
        assertTrue(modules.contains("                    PAYROLL,\n                    ModuleCategory.TIME_ACCOUNTING,"));
        assertTrue(modules.contains("/api/v1/payroll"));

        assertTrue(html.contains("id=\"view-payroll\""));
        assertTrue(html.contains("id=\"payrollCalculate\""));
        assertTrue(html.contains("id=\"payrollSnapshotList\""));
        assertTrue(html.contains("js/45-payroll.js"));
        assertTrue(javascript.contains("window.__dutylogPayrollReady"));
        assertTrue(javascript.contains("api.calculatePayroll"));
        assertTrue(javascript.contains("paidAbsenceMinutes"));
        assertTrue(data.contains("payrollPeriod(month)"));
        assertTrue(data.contains("calculatePayroll(month)"));

        assertTrue(openapi.contains("/api/v1/payroll/periods/{month}:"));
        assertTrue(openapi.contains("/api/v1/payroll/settings:"));
        assertTrue(openapi.contains("/api/v1/payroll/adjustments:"));
        assertTrue(openapi.contains("PayrollSnapshot:"));
        assertTrue(openapi.contains("paidAbsenceMinutes:"));
    }

    private static String source(String path) throws IOException {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }

    private static String resource(String path) throws IOException {
        try (var input = PayrollFoundationContractTest.class.getResourceAsStream(path)) {
            if (input == null) throw new IOException("Missing classpath resource: " + path);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
