package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Static architecture contract for v27.40.17 Vue route commit and legacy hash-listener retirement. */
class VueRouteCommitLegacyHashListenerRetirementTest {

    @Test
    void vuePublishesOnlyCanonicalCommittedRoutesAfterGuardResolution() throws Exception {
        String router = read("frontend/src/platform/router/hashRoute.ts");
        String app = read("frontend/src/App.vue");

        assertTrue(router.contains("VUE_ROUTE_COMMITTED_EVENT = \"dutylog:vue-route-committed\""));
        assertTrue(router.contains("export function publishCommittedHashRoute"));
        assertTrue(router.contains("new CustomEvent<DutyLogRouteSnapshot>(VUE_ROUTE_COMMITTED_EVENT"));
        assertTrue(app.contains("const route = guardHashRoute(requested"));
        assertTrue(app.contains("const committedKey = `${route.rawRoute}|${route.activeRoute}`"));
        assertTrue(app.contains("lastCommittedRoute = committedKey;"));
        assertTrue(app.contains("publishCommittedHashRoute(route);"));
        assertTrue(app.indexOf("publishCommittedHashRoute(route);") > app.indexOf("const route = guardHashRoute(requested"));
    }

    @Test
    void legacyStopsListeningToHashAfterVueReadyAndKeepsOnlyPayrollAdminEffects() throws Exception {
        String boot = read("src/main/resources/static/js/70-user-boot.js");
        String effects = between(boot, "function applyRemainingLegacyRouteEffects(active)", "function handleVueRouteCommitted(event)");
        String recovery = between(boot, "function applyRoute()", "window.addEventListener(\"hashchange\", applyRoute)");

        assertTrue(boot.contains("window.addEventListener(\"dutylog:vue-route-committed\", handleVueRouteCommitted)"));
        assertTrue(boot.contains("window.removeEventListener(\"hashchange\", applyRoute);"));
        assertTrue(boot.contains("if (active !== preVueActiveRoute) applyRemainingLegacyRouteEffects(active);"));
        assertTrue(effects.contains("VIEWS.payroll"));
        assertTrue(effects.contains("VIEWS.admin"));
        assertFalse(effects.contains("renderTodayDashboard"));
        assertFalse(effects.contains("openVacationPlannerView"));
        assertFalse(effects.contains("loadLedgerPage"));
        assertTrue(recovery.contains("Pre-Vue recovery keeps the historical router intact."));
        assertTrue(recovery.contains("renderTodayDashboard"));
        assertTrue(recovery.contains("openVacationPlannerView"));
        assertTrue(recovery.contains("loadLedgerPage"));
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
