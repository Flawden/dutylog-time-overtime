package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LedgerWorkflowBrowserContractHotfixTest {

    @Test
    void browserContractsRefreshSerializeAndMarkExpectedFailures() throws IOException {
        String overtime = resource("/static/js/40-overtime.js");
        String routing = resource("/static/js/70-user-boot.js");
        String helpers = source("e2e/helpers.js");
        String fixture = source("e2e/fixtures.js");
        String workflow = source("e2e/ledger-integrity-approval-workflow.spec.js");
        String timezone = source("e2e/important-timezone.spec.js");
        String absences = source("e2e/absence-time-off-overhaul.spec.js");
        String unified = source("e2e/unified-time-compensation-ledger.spec.js");
        String shell = source("e2e/design-system-shell.spec.js");
        String overtimeNext = source("e2e/overtime-next.spec.js");

        assertTrue(overtime.contains("function refreshLedgerReadModels()"));
        assertTrue(overtime.contains("let ledgerReadModelRefreshChain = Promise.resolve()"));
        assertTrue(overtime.contains("await loadLedgerIntegrity();"));
        assertFalse(overtime.contains("Promise.all([loadTimeCompensation(), loadLedgerIntegrity(), loadActualWork()])"));
        assertFalse(overtime.contains("Promise.all([loadLedgerIntegrity(), loadActualWork(), loadTimeCompensation()])"));

        assertTrue(routing.contains("window.__dutylogLedgerRouteReady = Promise.resolve(loadLedgerPage(true))"));
        assertTrue(helpers.contains("async function waitForLedgerReady(page)"));
        assertTrue(helpers.contains("if (view === 'overtime') await waitForLedgerReady(page)"));
        assertTrue(helpers.contains("async function waitForAppIdle(page)"));

        assertTrue(fixture.contains("x-dutylog-e2e-expected-status"));
        assertTrue(workflow.contains("X-DutyLog-E2E-Expected-Status"));
        assertTrue(workflow.contains("409);"));

        assertFalse(timezone.contains("2026-07-03"));
        assertTrue(timezone.contains("sourceDisplay:`03.${month}`"));
        assertTrue(absences.contains("#vacationStatus').selectOption('APPROVED')"));
        assertTrue(unified.contains("#vacationStatus').selectOption('APPROVED')"));
        assertTrue(shell.contains("waitForAppIdle(page)"));
        assertTrue(overtimeNext.contains("openView(page, 'overtime')"));
        assertTrue(overtimeNext.contains("waitForLedgerReady(page)"));
    }

    private static String source(String path) throws IOException {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }

    private static String resource(String path) throws IOException {
        try (var input = LedgerWorkflowBrowserContractHotfixTest.class.getResourceAsStream(path)) {
            if (input == null) throw new IOException("Missing classpath resource: " + path);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
