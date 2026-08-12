package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Static architecture contract for v27.40.21 Vue Payroll workspace retirement. */
class VuePayrollWorkspaceRetirementFrontendContractTest {

    @Test
    void payrollIsVueOwnedAndUsesGeneratedOpenApiOperations() throws Exception {
        String shell = read("frontend/src/app/AppShell.vue");
        String component = read("frontend/src/features/payroll/components/PayrollWorkspace.vue");
        String api = read("frontend/src/features/payroll/api/payrollApi.ts");
        String types = read("frontend/src/types/window.d.ts");
        String helper = read("e2e/helpers.js");

        assertTrue(shell.contains("import PayrollWorkspace"));
        assertTrue(shell.contains("<PayrollWorkspace />"));
        assertTrue(component.contains("data-vue-domain-owner=\"payroll\""));
        assertTrue(component.contains("window.DutyLogVueDomains?.payroll"));
        assertFalse(component.contains("client.request(")); // API access stays out of the view.
        assertTrue(api.contains("client.request(\"payrollPeriod\""));
        assertTrue(api.contains("client.request(\"updatePayrollSettings\""));
        assertTrue(api.contains("client.request(\"addPayrollAdjustment\""));
        assertTrue(api.contains("client.request(\"calculatePayrollRevision\""));
        assertTrue(types.contains("readonly payroll?: DutyLogPayrollDomain"));
        assertTrue(helper.contains("window.DutyLogVueDomains?.payroll?.ready()"));
    }

    @Test
    void legacyPayrollDomScriptAndRouteSideEffectsAreRetired() throws Exception {
        String html = read("src/main/resources/static/index.html");
        String boot = read("src/main/resources/static/js/70-user-boot.js");
        String core = read("src/main/resources/static/js/10-core.js");
        String data = read("src/main/resources/static/js/20-data.js");
        String release = read("deploy/scripts/release-check.sh");

        assertFalse(html.contains("id=\"view-payroll\""));
        assertFalse(html.contains("js/45-payroll.js"));
        assertFalse(Files.exists(Path.of("src/main/resources/static/js/45-payroll.js")));
        assertFalse(boot.contains("openPayrollView"));
        assertFalse(core.contains("payrollLoading"));
        assertFalse(data.contains("payrollPeriod(month)"));
        assertFalse(data.contains("calculatePayroll(month)"));
        String effects = between(boot, "function applyRemainingLegacyRouteEffects(active)", "function handleVueRouteCommitted(event)");
        assertFalse(effects.contains("VIEWS.payroll"));
        assertTrue(effects.contains("VIEWS.admin"));
        assertFalse(release.contains("\"js/45-payroll.js\""));
    }

    private static String between(String source, String startToken, String endToken) {
        int start = source.indexOf(startToken);
        int end = source.indexOf(endToken, start + startToken.length());
        if (start < 0 || end < 0) throw new IllegalStateException("Contract surface not found");
        return source.substring(start, end);
    }

    private static String read(String relative) throws Exception {
        return Files.readString(Path.of(relative));
    }
}
