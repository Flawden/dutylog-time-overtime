package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Product contract for v27.32.0 Absence & Time Bank Experience. */
class AbsenceTimeBankExperienceContractTest {

    @Test
    void timeBankSeparatesOverviewCreditsUsageAndFifoDetail() throws Exception {
        String html = read("src/main/resources/static/index.html");
        String js = read("src/main/resources/static/js/40-overtime.js");

        assertTrue(html.contains("id=\"timeBankTabOverview\""));
        assertTrue(html.contains("id=\"timeBankTabCredits\""));
        assertTrue(html.contains("id=\"timeBankTabUsage\""));
        assertTrue(html.contains("id=\"timeBankTabFifo\""));
        assertTrue(html.contains("id=\"ledgerUsagePanel\""));
        assertTrue(html.contains("id=\"ledgerFifoPanel\""));
        assertTrue(js.contains("function setTimeBankView(view = \"overview\""));
        assertTrue(js.contains("new Set([\"overview\",\"credits\",\"usage\",\"fifo\"])") );
    }

    @Test
    void overviewDistinguishesPostedReservedAndFreeTime() throws Exception {
        String html = read("src/main/resources/static/index.html");
        String js = read("src/main/resources/static/js/40-overtime.js");

        assertTrue(html.contains("id=\"ledgerReserved\""));
        assertTrue(html.contains("id=\"ledgerBalanceCaption\""));
        assertTrue(js.contains("function timeBankUsageBuckets()"));
        assertTrue(js.contains("postingState || \"POSTED\""));
        assertTrue(js.contains("reservedMinutes"));
        assertTrue(js.contains("postedMinutes"));
    }

    @Test
    void absenceOwnsEditingWhileTheBankLinksToItsReadOnlyProjection() throws Exception {
        String planner = read("src/main/resources/static/js/39-vacation-planner.js");
        String overtime = read("src/main/resources/static/js/40-overtime.js");

        assertTrue(planner.contains("function openTimeBankUsageForAbsence(absenceId)"));
        assertTrue(planner.contains("data-bank-absence=\"${period.id}\""));
        assertTrue(planner.contains("Посмотреть списание"));
        assertTrue(overtime.contains("data-open-absence=\"${usage.id}\""));
        assertTrue(overtime.contains("Управляется отсутствием"));
        assertFalse(overtime.contains("data-edit-usage=\"${usage.id}\">Изменить"));
    }

    @Test
    void fifoForecastExplainsWhatWillBeSpentNext() throws Exception {
        String html = read("src/main/resources/static/index.html");
        String overtime = read("src/main/resources/static/js/40-overtime.js");
        String planner = read("src/main/resources/static/js/39-vacation-planner.js");

        assertTrue(html.contains("id=\"fifoForecastForm\""));
        assertTrue(html.contains("id=\"ledgerFifoForecast\""));
        assertTrue(html.contains("id=\"absenceFifoForecast\""));
        assertTrue(overtime.contains("function timeBankForecast(requestedMinutes,"));
        assertTrue(overtime.contains("excludeAbsenceId = null"));
        assertTrue(overtime.contains("restoredByCredit"));
        assertTrue(overtime.contains("function renderFifoForecast(requestedMinutes = null)"));
        assertTrue(planner.contains("function renderAbsenceFifoForecast"));
        assertTrue(overtime.contains("После этого останется"));
    }

    @Test
    void firstProductGuidesAndBrowserJourneyShipWithTheFeature() throws Exception {
        String html = read("src/main/resources/static/index.html");
        String spec = read("e2e/absence-time-bank-experience.spec.js");

        assertTrue(html.contains("id=\"timeBankGuideOpen\""));
        assertTrue(html.contains("id=\"absenceGuideOpen\""));
        assertTrue(html.contains("id=\"timeBankGuideModal\""));
        assertTrue(html.contains("id=\"absenceScope\""));
        assertTrue(html.contains("id=\"absenceTypeFilter\""));
        assertTrue(html.contains("id=\"absenceStatusFilter\""));
        assertTrue(spec.contains("absence remains the event owner while the time bank explains reservations and FIFO"));
        assertTrue(spec.contains("#timeBankTabUsage"));
        assertTrue(spec.contains("#timeBankTabFifo"));
        assertTrue(spec.contains("[data-bank-absence]"));
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
