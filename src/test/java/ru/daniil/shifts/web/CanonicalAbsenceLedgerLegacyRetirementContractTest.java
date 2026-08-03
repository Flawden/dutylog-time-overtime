package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Release contract for v27.31.0 Canonical Absence Ledger & Legacy Retirement. */
class CanonicalAbsenceLedgerLegacyRetirementContractTest {

    @Test
    void browserCreatesTimeOffOnlyThroughTheUnifiedAbsenceComposer() throws Exception {
        String html = read("src/main/resources/static/index.html");
        String data = read("src/main/resources/static/js/20-data.js");
        String overtime = read("src/main/resources/static/js/40-overtime.js");

        assertFalse(html.contains("id=\"overtimeUsageModal\""));
        assertFalse(html.contains("id=\"overtimeUsageForm\""));
        assertFalse(data.contains("createOvertimeUsage"));
        assertFalse(data.contains("updateOvertimeUsage"));
        assertTrue(overtime.contains("openAbsenceComposer({ date:overtimeDefaultDate(date), systemCode:\"TIME_OFF\", source:\"overtime\" })"));
        assertTrue(html.contains("id=\"absenceComposerModal\""));
    }

    @Test
    void legacyManualUsagesHaveOneExplicitPromotionPath() throws Exception {
        String html = read("src/main/resources/static/index.html");
        String data = read("src/main/resources/static/js/20-data.js");
        String overtime = read("src/main/resources/static/js/40-overtime.js");
        String controller = read("src/main/java/ru/daniil/shifts/web/OvertimeController.java");

        assertTrue(html.contains("id=\"legacyUsageMigrationModal\""));
        assertTrue(html.contains("id=\"ledgerMigrateUsages\""));
        assertTrue(data.contains("previewLegacyOvertimeUsages"));
        assertTrue(data.contains("migrateLegacyOvertimeUsages"));
        assertTrue(overtime.contains("openLegacyUsageMigration"));
        assertTrue(controller.contains("DIRECT_USAGE_RETIRED"));
        assertTrue(controller.contains("LEGACY_USAGE_MUST_BE_MIGRATED"));
        assertTrue(controller.contains("LINKED_USAGE_MANAGED_BY_ABSENCE"));
        assertTrue(controller.contains("isAbsenceLinkedUsage"));
        assertTrue(controller.contains("/legacy-usages/preview"));
        assertTrue(controller.contains("/legacy-usages/migrate"));
    }

    @Test
    void importedUnknownIntervalsRemainTruthfulAndEditable() throws Exception {
        String html = read("src/main/resources/static/index.html");
        String planner = read("src/main/resources/static/js/39-vacation-planner.js");
        String calendar = read("src/main/resources/static/js/30-calendar.js");
        String service = read("src/main/java/ru/daniil/shifts/service/VacationPlannerService.java");

        assertTrue(html.contains("value=\"HOURS_ONLY\""));
        assertTrue(planner.contains("Интервал не указан"));
        assertTrue(planner.contains("period.coverage === \"HOURS_ONLY\""));
        assertTrue(calendar.contains("[\"PARTIAL\",\"HOURS_ONLY\"]"));
        assertTrue(service.contains("attachManualUsageToAbsence"));
        assertTrue(service.contains("Сохранён объём часов, но исходный временной интервал неизвестен"));
    }

    @Test
    void overtimeJournalAndFifoRemainCanonicalReadModels() throws Exception {
        String html = read("src/main/resources/static/index.html");
        String overtime = read("src/main/resources/static/js/40-overtime.js");
        String service = read("src/main/java/ru/daniil/shifts/service/OvertimeService.java");
        String planner = read("src/main/java/ru/daniil/shifts/service/VacationPlannerService.java");

        assertTrue(html.contains("id=\"ledgerRows\""));
        assertTrue(html.contains("id=\"ledgerFifoQueue\""));
        assertTrue(overtime.contains("renderFifoQueue"));
        assertTrue(overtime.contains("renderLedgerTable"));
        assertTrue(planner.contains("Existing allocation rows remain untouched"));
        assertTrue(service.contains("usage.setSourceKind(\"ABSENCE\")"));
    }

    @Test
    void v47AllowsTruthfulHoursOnlyRowsWithoutRewritingV42() throws Exception {
        Path v42 = Path.of("src/main/resources/db/migration/postgresql/V42__absence_time_off_overhaul.sql");
        String v47 = read("src/main/resources/db/migration/postgresql/V47__absence_hours_only_legacy_shape.sql");

        assertTrue("2bd6b413899be74f5d070ecde18915bd5152072d2fc05bcb87e40c0ea931a76c"
                .equals(sha256(v42)));
        assertTrue(v47.contains("coverage IN ('FULL_DAY', 'PARTIAL', 'HOURS_ONLY')"));
        assertTrue(v47.contains("coverage = 'HOURS_ONLY' AND start_date = end_date"));
        assertTrue(v47.contains("start_time IS NULL AND end_time IS NULL"));
        assertFalse(v47.contains("UPDATE absence_periods"));
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
    private static String sha256(Path path) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path));
        return HexFormat.of().formatHex(digest);
    }

}
