package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Static contract for v27.18.0 Overtime Next. */
class OvertimeNextFrontendContractTest {

    @Test
    void overtimeWorkspaceExposesSummaryPeriodsChartAndMobileCards() throws Exception {
        String html = read("src/main/resources/static/index.html");
        assertTrue(html.contains("id=\"overtimeWorkspaceTitle\""));
        assertTrue(html.contains("id=\"ledgerEarned\""));
        assertTrue(html.contains("id=\"ledgerUsed\""));
        assertTrue(html.contains("id=\"ledgerUsageRatio\""));
        assertTrue(html.contains("id=\"ledgerThisMonth\""));
        assertTrue(html.contains("id=\"ledgerThisYear\""));
        assertTrue(html.contains("id=\"ledgerAllTime\""));
        assertTrue(html.contains("id=\"ledgerChart\""));
        assertTrue(html.contains("id=\"ledgerFifoQueue\""));
        assertTrue(html.contains("id=\"ledgerCards\""));
    }

    @Test
    void overtimeRuntimeKeepsFifoAndPeriodPresentationClientSide() throws Exception {
        String js = read("src/main/resources/static/js/40-overtime.js");
        assertTrue(js.contains("function renderOvertimeOverview()"));
        assertTrue(js.contains("function renderLedgerChart(credits, usages)"));
        assertTrue(js.contains("function renderFifoQueue(rows)"));
        assertTrue(js.contains("function ledgerFilteredUsages()"));
        assertTrue(js.contains("data-series-key"));
        assertTrue(js.contains("function renderLedgerCards(credits, options = "));
        assertTrue(js.contains("function setLedgerThisYear()"));
        assertTrue(js.contains("preset:\"year\""));
        assertTrue(js.contains("uniqueSourceCredits"));
        assertFalse(js.contains("innerHTML = credit.reason"));
    }

    @Test
    void mobilePresentationHidesTheWideTableInsteadOfCompressingIt() throws Exception {
        String css = read("src/main/resources/static/design-system.css");
        assertTrue(css.contains("/* v27.18.0 — Overtime Next */"));
        assertTrue(css.contains(".overtimeMobileList"));
        assertTrue(css.contains("display: none;"));
        assertTrue(css.contains(".ledgerTableWrap"));
        assertTrue(css.contains("display: grid;"));
        assertTrue(css.contains(".overtimeLedgerCard"));
        assertTrue(css.contains("@media (max-width: 760px)"));
    }

    @Test
    void exportsAndExistingEditorsRemainAvailable() throws Exception {
        String html = read("src/main/resources/static/index.html");
        String js = read("src/main/resources/static/js/40-overtime.js");
        assertTrue(html.contains("id=\"ledgerExportCsv\""));
        assertTrue(html.contains("id=\"ledgerExportXls\""));
        assertTrue(html.contains("id=\"ledgerAddCredit\""));
        assertTrue(html.contains("id=\"ledgerAddUsage\""));
        assertTrue(js.contains("openOvertimeCreditModal"));
        assertTrue(js.contains("openOvertimeUsageModal"));
        assertTrue(js.contains("openAbsenceComposer({ date:overtimeDefaultDate(date), systemCode:\"TIME_OFF\", source:\"overtime\" })"));
        assertTrue(html.contains("id=\"ledgerMigrateUsages\""));
        assertTrue(js.contains("ledgerExportUrl"));
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
