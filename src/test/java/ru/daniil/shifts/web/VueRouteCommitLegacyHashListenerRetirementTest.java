package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Static architecture contract for the completed Vue route-authority cutover. */
class VueRouteCommitLegacyHashListenerRetirementTest {

    @Test
    void vueOwnsCanonicalHashRoutingWithoutASecondRouteCommitTransport() throws Exception {
        String router = read("frontend/src/platform/router/hashRoute.ts");
        String app = read("frontend/src/App.vue");

        assertTrue(router.contains("export function guardHashRoute"));
        assertTrue(router.contains("export function subscribeHashRoute"));
        assertTrue(app.contains("const route = guardHashRoute(requested"));
        assertTrue(app.contains("document.body.dataset.view = route.activeRoute"));
        assertFalse(router.contains("VUE_ROUTE_COMMITTED_EVENT"));
        assertFalse(router.contains("publishCommittedHashRoute"));
        assertFalse(app.contains("publishCommittedHashRoute"));
        assertFalse(app.contains("lastCommittedRoute"));
    }

    @Test
    void legacyStopsListeningToHashAfterVueReadyAndHasNoPostVueRouteEffects() throws Exception {
        String boot = read("src/main/resources/static/js/70-user-boot.js");
        String recovery = between(boot, "function applyRoute()", "window.addEventListener(\"hashchange\", applyRoute)");

        assertTrue(boot.contains("window.removeEventListener(\"hashchange\", applyRoute);"));
        assertFalse(boot.contains("dutylog:vue-route-committed"));
        assertFalse(boot.contains("applyRemainingLegacyRouteEffects"));
        assertFalse(boot.contains("handleVueRouteCommitted"));
        assertTrue(recovery.contains("Pre-Vue recovery is intentionally limited to legacy-compatible screens."));
        assertTrue(recovery.contains("name === \"admin\" ? \"settings\""));
        assertTrue(recovery.contains("renderTodayDashboard"));
        assertTrue(recovery.contains("openVacationPlannerView"));
        assertTrue(recovery.contains("loadLedgerPage"));
        assertFalse(recovery.contains("openPayrollView"));
        assertFalse(recovery.contains("refreshAdminPanel"));
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
