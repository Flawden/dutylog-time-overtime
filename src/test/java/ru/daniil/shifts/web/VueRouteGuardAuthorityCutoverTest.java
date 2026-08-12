package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Static architecture contract for Vue route-guard authority and profile-state publication alignment through v27.40.17. */
class VueRouteGuardAuthorityCutoverTest {

    @Test
    void vueOwnsAdminAndModuleRouteGuardsAfterAuthoritativeAccessStateLoads() throws Exception {
        String router = read("frontend/src/platform/router/hashRoute.ts");
        String app = read("frontend/src/App.vue");
        String store = read("frontend/src/app/shellStore.ts");

        assertTrue(router.contains("export function guardHashRoute"));
        assertTrue(router.contains("activeRoute === \"admin\" && access.profileLoaded && !access.admin"));
        assertTrue(router.contains("access.modulesLoaded && access.modules[moduleKey] === false"));
        assertTrue(app.contains("const route = guardHashRoute(requested"));
        assertTrue(app.contains("document.body.dataset.view = route.activeRoute"));
        assertTrue(app.contains("navigateHashRoute(route.rawRoute)"));
        assertTrue(store.contains("profileLoaded: false"));
        assertTrue(store.contains("this.profileLoaded = snapshot.profile != null"));
    }

    @Test
    void postVueLegacyRouterOnlyKeepsAdminSideEffects() throws Exception {
        String boot = read("src/main/resources/static/js/70-user-boot.js");
        String calendar = read("frontend/src/features/calendar-timeline/components/CalendarTimelineWorkspace.vue");

        assertTrue(boot.contains("function applyRemainingLegacyRouteEffects(active)"));
        assertTrue(boot.contains("window.addEventListener(\"dutylog:vue-route-committed\", handleVueRouteCommitted)"));
        assertTrue(boot.contains("window.removeEventListener(\"hashchange\", applyRoute);"));
        assertTrue(boot.contains("Pre-Vue recovery keeps the historical router intact."));
        String loadProfile = loadProfileSurface(boot);
        assertTrue(loadProfile.contains("state.profile = p;"));
        assertTrue(loadProfile.contains("applyRoute();"));
        assertTrue(loadProfile.contains("publishLegacyPlatformState();"));
        assertTrue(loadProfile.indexOf("publishLegacyPlatformState();") > loadProfile.indexOf("applyRoute();"));
        assertTrue(calendar.contains("route !== \"calendar\" && store.dayPanelOpen"));
        assertTrue(calendar.contains("store.closeDayPanel()"));
        String effects = legacyRouteEffectsSurface(boot);
        assertFalse(effects.contains("VIEWS.payroll"));
        assertTrue(effects.contains("VIEWS.admin"));
        assertFalse(effects.contains("renderTodayDashboard"));
        assertFalse(effects.contains("renderImportantBoard"));
        assertFalse(effects.contains("openVacationPlannerView"));
        assertFalse(effects.contains("loadLedgerPage"));
    }

    private static String loadProfileSurface(String boot) {
        int start = boot.indexOf("async function loadProfile()");
        int end = boot.indexOf("$(\"nextHeaderAvatar\")", start);
        return boot.substring(start, end);
    }

    private static String legacyRouteEffectsSurface(String boot) {
        int start = boot.indexOf("function applyRemainingLegacyRouteEffects(active)");
        int end = boot.indexOf("function handleVueRouteCommitted(event)", start);
        return boot.substring(start, end);
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
