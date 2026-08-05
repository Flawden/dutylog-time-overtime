package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Static browser-parity contract for v27.36.4 Vue Absence and Time Bank ownership. */
class VueAbsenceTimeBankBrowserParityHotfixTest {

    @Test
    void retiredLegacyGuideCannotDuplicateTheVueModalId() throws IOException {
        String core = compact(read("src/main/resources/static/js/10-core.js"));

        assertTrue(core.contains("\"timeBankGuideModal\", \"timeBankGuideBackdrop\""));
        assertTrue(core.contains("document.getElementById(id)?.remove()"));
    }

    @Test
    void winningVueRefreshPublishesTheCanonicalPlannerAndAccountProjection() throws IOException {
        String bridge = read("frontend/src/platform/bridge/legacyBridge.ts");
        String store = compact(read("frontend/src/features/absence-time-bank/stores/absenceTimeBankStore.ts"));

        assertTrue(bridge.contains("dutylog:absence-time-bank-projection"));
        assertTrue(store.contains("if (sequence !== refreshSequence) return;"));
        assertTrue(store.contains("publishAbsenceTimeBankProjection(window, { planner: this.planner, account: this.account, referenceDate, });"));
    }

    @Test
    void legacyBridgeRefreshesOnlyTheRemainingCalendarTodayAndSelectedDayProjections() throws IOException {
        String core = compact(read("src/main/resources/static/js/10-core.js"));
        int start = core.indexOf("function synchronizeLegacyAbsenceTimeBankProjection(snapshot)");
        int end = core.indexOf("window.addEventListener(ABSENCE_TIME_BANK_PROJECTION_EVENT", start);
        assertTrue(start >= 0 && end > start);
        String synchronizer = core.substring(start, end);

        assertTrue(synchronizer.contains("renderVacationDay()"));
        assertTrue(synchronizer.contains("renderOvertimeDayDetails()"));
        assertTrue(synchronizer.contains("renderCalendar()"));
        assertTrue(synchronizer.contains("renderTodayDashboard()"));
        assertFalse(synchronizer.contains("renderVacationPlanner()"));
        assertFalse(synchronizer.contains("renderOvertimeControls()"));
    }

    @Test
    void todayAndCalendarComposerLaunchesPreserveTheirCurrentRoute() throws IOException {
        String workspace = compact(read("frontend/src/features/absence-time-bank/components/AbsenceTimeBankWorkspace.vue"));

        assertTrue(workspace.contains("if (options?.source === \"vacation\") props.bridge.navigate(\"vacation\");"));
        assertTrue(workspace.contains("if (options?.source === \"time-bank\") props.bridge.navigate(\"overtime\");"));
        assertFalse(workspace.contains("openCreditEditor: async (date?: string | null) => { props.bridge.navigate(\"overtime\"); await store.openCreditEditor(date); }"));
    }

    @Test
    void visibleEditModalOwnsAbsenceDeletionInsteadOfTheCoveredJournalRow() throws IOException {
        String composer = read("frontend/src/features/absence-time-bank/components/AbsenceComposer.vue");
        String page = read("frontend/src/features/absence-time-bank/components/AbsencePage.vue");

        assertTrue(composer.contains(":data-delete-absence=\"absenceDraft.id\""));
        assertTrue(composer.contains("@click=\"store.deleteAbsence(Number(absenceDraft.id))\""));
        assertTrue(page.contains(":data-delete-absence-row=\"period.id\""));
        assertFalse(page.contains(":data-delete-absence=\"period.id\""));
    }

    @Test
    void timeBankOverviewExposesUsageRatioAndOldestCreditBeforeTabContent() throws IOException {
        String page = read("frontend/src/features/absence-time-bank/components/TimeBankPage.vue");
        int insights = page.indexOf("data-time-bank-insights");
        int overview = page.indexOf("v-if=\"timeBankTab === 'overview'\"");
        int credits = page.indexOf("v-else-if=\"timeBankTab === 'credits'\"");

        assertTrue(insights >= 0 && insights < overview);
        assertTrue(page.indexOf("id=\"ledgerUsageRatio\"") < overview);
        assertTrue(page.indexOf("id=\"ledgerOldestCredit\"") < overview);
        assertTrue(page.indexOf("id=\"ledgerPeriodLabel\"") > credits);
    }

    @Test
    void legacySelectedDayActionsDelegateToTheVueAbsenceEditor() throws IOException {
        String planner = compact(read("src/main/resources/static/js/39-vacation-planner.js"));

        assertTrue(planner.contains("openAbsenceEditor(Number(button.dataset.dayAbsence), { source:\"calendar\" })"));
        assertTrue(planner.contains("openAbsenceEditor(Number(occurrence?.periodId), { source:\"calendar\" })"));
        assertFalse(planner.contains("editAbsence(Number(button.dataset.dayAbsence))"));
    }

    @Test
    void projectionEventHasAUnitLevelPublisherContract() throws IOException {
        String test = read("frontend/src/platform/bridge/legacyBridge.spec.ts");

        assertTrue(test.contains("ABSENCE_TIME_BANK_PROJECTION_EVENT"));
        assertTrue(test.contains("publishAbsenceTimeBankProjection"));
        assertTrue(test.contains("referenceDate: \"2026-08-05\""));
    }

    private static String compact(String value) {
        return value.replaceAll("\\s+", " ").trim();
    }

    private static String read(String path) throws IOException {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
