package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regression guard for v27.32.1 time-bank-to-absence navigation. */
class AbsenceTimeBankNavigationHotfixTest {

    @Test
    void bankUsageOpensTheOwningAbsenceInTheComposerInsteadOfOnlyRoutingToVacation() throws Exception {
        String planner = read("src/main/resources/static/js/39-vacation-planner.js");
        String overtime = read("src/main/resources/static/js/40-overtime.js");

        assertTrue(planner.contains("async function openAbsenceEditor(id, { source = \"vacation\" } = {})"));
        assertTrue(planner.contains("editAbsence(period.id, { navigate:false, scroll:false })"));
        assertTrue(planner.contains("moveAbsenceComposerToModal()"));
        assertTrue(planner.contains("openAppModal(\"absenceComposerModal\", \"vacationTitle\")"));
        assertTrue(overtime.contains("return await openAbsenceEditor(Number(usage.sourceAbsenceId), { source:\"time-bank\" })"));
        assertFalse(overtime.contains("editAbsence(Number(usage.sourceAbsenceId))"));
    }

    @Test
    void composerRefreshesMissingAbsenceDataAndBuildsTheLinkedFifoPreview() throws Exception {
        String planner = read("src/main/resources/static/js/39-vacation-planner.js");
        String spec = read("e2e/absence-time-bank-experience.spec.js");

        assertTrue(planner.contains("let period = (state.vacationPlanner?.absences || [])"));
        assertTrue(planner.contains("await loadVacationPlanner(true);"));
        assertTrue(planner.contains("await loadLedgerPage(true)"));
        assertTrue(planner.contains("await previewVacationDraft()"));
        assertTrue(spec.contains("await usage.locator('[data-open-absence]').click()"));
        assertTrue(spec.contains("await expect(page.locator('#absenceComposerModal')).toBeVisible()"));
        assertTrue(spec.contains("await expect(page.locator('#absenceFifoForecast')).toContainText('Experience source')"));
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
