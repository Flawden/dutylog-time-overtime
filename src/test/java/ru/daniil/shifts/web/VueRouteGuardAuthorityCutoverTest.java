package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Static architecture contract for v27.40.14 Vue route-guard authority and legacy-router narrowing. */
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
    void postVueLegacyRouterOnlyKeepsPayrollAndAdminSideEffects() throws Exception {
        String boot = read("src/main/resources/static/js/70-user-boot.js");
        String calendar = read("frontend/src/features/calendar-timeline/components/CalendarTimelineWorkspace.vue");

        assertTrue(boot.contains("document.documentElement.dataset.vueShell === \"ready\""));
        assertTrue(boot.contains("const payrollView = document.getElementById(VIEWS.payroll)"));
        assertTrue(boot.contains("const adminView = document.getElementById(VIEWS.admin)"));
        assertTrue(boot.contains("Pre-Vue recovery keeps the historical router intact."));
        assertTrue(boot.contains("Profile load still must publish authoritative access state"));
        assertTrue(calendar.contains("route !== \"calendar\" && store.dayPanelOpen"));
        assertTrue(calendar.contains("store.closeDayPanel()"));
        assertFalse(appRouteSurface(boot).contains("renderTodayDashboard"));
        assertFalse(appRouteSurface(boot).contains("renderImportantBoard"));
        assertFalse(appRouteSurface(boot).contains("openVacationPlannerView"));
        assertFalse(appRouteSurface(boot).contains("loadLedgerPage"));
    }

    private static String appRouteSurface(String boot) {
        int start = boot.indexOf("if (document.documentElement.dataset.vueShell === \"ready\")");
        int end = boot.indexOf("// Pre-Vue recovery keeps the historical router intact.", start);
        return boot.substring(start, end);
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
