package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regression guard for v27.31.1 static-contract alignment. */
class CanonicalAbsenceStaticContractAlignmentHotfixTest {

    @Test
    void historicalFrontendContractsFollowCanonicalAbsenceOwnershipWithoutRestoringLegacyWrites() throws Exception {
        String planner = read("src/main/resources/static/js/39-vacation-planner.js");
        String overtime = read("src/main/resources/static/js/40-overtime.js");

        assertTrue(planner.contains("const coverage = $(\"vacationCoverage\")?.value || \"FULL_DAY\";"));
        assertTrue(planner.contains("coverage,"));
        assertTrue(planner.contains("coverage === \"HOURS_ONLY\""));
        assertFalse(planner.contains("coverage:partial ? \"PARTIAL\" : \"FULL_DAY\""));
        assertFalse(planner.contains("shiftTypeId:"));

        assertTrue(overtime.contains("async function openLegacyUsageMigration(focusId = null)"));
        assertTrue(overtime.contains("function renderLegacyUsageMigrationPreview(preview)"));
        assertTrue(overtime.contains("Number(row.usageId) === Number(state.legacyUsageMigrationFocusId)"));
        assertTrue(overtime.contains("Перенести старое списание в отсутствия"));
        assertTrue(overtime.contains("Удалить старое списание"));
        assertTrue(overtime.contains("Управляется отсутствием"));
        assertTrue(overtime.contains("usageManagedByAbsence(fullUsage)"));
        assertFalse(overtime.contains("openLegacyUsageMigrationModal"));
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
