package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regression guard for v27.31.2 browser-contract alignment. */
class CanonicalAbsenceBrowserContractAlignmentHotfixTest {

    @Test
    void expectedRetiredUsageProbeRunsOutsideTheBrowserRuntimeFailureMonitor() throws Exception {
        String spec = read("e2e/canonical-absence-ledger.spec.js");

        assertTrue(spec.contains("page.context().request.post('/api/overtime/usages'"));
        assertTrue(spec.contains("expect(retired.status).toBe(409)"));
        assertTrue(spec.contains("expect(retired.body.code).toBe('DIRECT_USAGE_RETIRED')"));
        assertFalse(spec.contains("await fetch('/api/overtime/usages'"));
        assertFalse(spec.contains("page.evaluate(async date =>"));
    }

    @Test
    void linkedUsageBrowserFlowsEditAndDeleteTheOwningAbsenceInsteadOfExpectingLegacyButtons() throws Exception {
        String canonical = read("e2e/canonical-absence-ledger.spec.js");
        String editors = read("e2e/overtime-editor-modals.spec.js");

        assertTrue(canonical.contains("#ledgerUsageList .timeBankUsageCard', { hasText:'Canonical time off'"));
        assertTrue(canonical.contains("#timeBankTabUsage"));
        assertTrue(canonical.contains("Управляется отсутствием|Managed by absence"));
        assertTrue(canonical.contains("[data-edit-absence=\"${absence.id}\"]"));
        assertTrue(canonical.contains("[data-edit-usage=\"${account.usages[0].id}\"]`)).toHaveCount(0)"));
        assertFalse(canonical.contains("const edit = page.locator(`[data-edit-usage="));

        assertTrue(editors.contains("[data-edit-usage=\"${secondUsageId}\"]`)).toHaveCount(0)"));
        assertTrue(editors.contains("hasText:'Surviving time-off'"));
        assertTrue(editors.contains("hasText:'Split time-off' })).toHaveCount(0)"));
        assertTrue(editors.contains("#ledgerUsageList .timeBankUsageCard"));
        assertTrue(editors.contains("await expect(surviving).toContainText(/Управляется отсутствием|Managed by absence/i)"));
        assertFalse(editors.contains("[data-edit-usage=\"${secondUsageId}\"]`)).toHaveCount(1)"));
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
