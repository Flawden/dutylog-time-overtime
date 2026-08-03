package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LedgerBrowserStateVisibilityHotfixTest {

    @Test
    void browserRoutesExposeFreshReadinessAndResponsiveSelectors() throws IOException {
        String vacation = resource("/static/js/39-vacation-planner.js");
        String routing = resource("/static/js/70-user-boot.js");
        String settings = resource("/static/js/60-settings.js");
        String helpers = source("e2e/helpers.js");
        String fixture = source("e2e/fixtures.js");
        String timezone = source("e2e/important-timezone.spec.js");
        String overtime = source("e2e/overtime-next.spec.js");
        String unified = source("e2e/unified-time-compensation-ledger.spec.js");

        assertTrue(vacation.contains("window.__dutylogVacationReady = Promise.resolve()"));
        assertTrue(vacation.contains("function openVacationPlannerView(force = false)"));
        assertTrue(routing.contains("openVacationPlannerView(true)"));
        assertTrue(helpers.contains("async function waitForVacationReady(page)"));
        assertTrue(helpers.contains("if (view === 'vacation') await waitForVacationReady(page)"));

        assertTrue(settings.contains("state.ui.savingTimeSettings = true"));
        assertTrue(settings.contains("window.__dutylogTimeSettingsSaveReady = Promise.resolve()"));
        assertTrue(settings.contains("addEventListener(\"click\", runTimeSettingsSave)"));
        assertTrue(helpers.contains("!ui.savingTimeSettings"));
        assertTrue(timezone.contains("window.__dutylogTimeSettingsSaveReady"));
        assertTrue(timezone.contains("waitForAppIdle(page)"));

        assertTrue(fixture.contains("expectedStatusConsoleBudget"));
        assertTrue(fixture.contains("consumeExpectedStatusConsole"));
        assertTrue(fixture.contains("x-dutylog-e2e-expected-status"));

        assertFalse(overtime.contains("plusDays(today, -2)"));
        assertTrue(overtime.contains("usageDate:`${prefix}-03`"));
        assertTrue(overtime.contains("data-series-key=\"${usageDate}\""));

        assertTrue(unified.contains("#ledgerUsageList .timeBankUsageCard"));
        assertTrue(unified.contains("#timeBankTabUsage"));
        assertFalse(unified.contains("const linked = page.locator('.overtimeLinkedUsage'"));
    }

    private static String source(String path) throws IOException {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }

    private static String resource(String path) throws IOException {
        try (var input = LedgerBrowserStateVisibilityHotfixTest.class.getResourceAsStream(path)) {
            if (input == null) throw new IOException("Missing classpath resource: " + path);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
