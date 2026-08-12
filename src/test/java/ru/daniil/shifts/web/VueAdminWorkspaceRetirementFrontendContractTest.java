package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Static architecture contract for v27.40.22 Vue Admin workspace and final live legacy UI retirement. */
class VueAdminWorkspaceRetirementFrontendContractTest {

    @Test
    void adminHasCanonicalV1ContractAndGeneratedClientOperations() throws Exception {
        String controller = read("src/main/java/ru/daniil/shifts/web/SystemController.java");
        String security = read("src/main/java/ru/daniil/shifts/config/SecurityConfig.java");
        String modules = read("src/main/java/ru/daniil/shifts/module/DutyLogModules.java");
        String openApi = read("src/main/resources/static/openapi/dutylog-v1.yaml");
        String generated = read("frontend/src/generated/dutylog-api.ts");

        assertTrue(controller.contains("@RequestMapping({\"/api/admin\", \"/api/v1/admin\"})"));
        assertTrue(security.contains("\"/api/admin/**\", \"/api/v1/admin/**\""));
        assertTrue(modules.contains("List.of(\"/api/admin\", \"/api/v1/admin\")"));
        assertTrue(openApi.contains("/api/v1/admin/status:"));
        assertTrue(openApi.contains("operationId: listAdminUsers"));
        assertTrue(openApi.contains("operationId: updateAdminUserRole"));
        assertTrue(openApi.contains("operationId: resetAdminUserPassword"));
        assertTrue(openApi.contains("operationId: getAdminRegistrationSettings"));
        assertTrue(openApi.contains("operationId: updateAdminRegistrationSettings"));
        assertTrue(generated.contains("Contract: 124 operations, 130 schemas"));
    }

    @Test
    void adminIsVueOwnedAndUsesOnlyGeneratedAdminApiOperations() throws Exception {
        String shell = read("frontend/src/app/AppShell.vue");
        String component = read("frontend/src/features/admin/components/AdminWorkspace.vue");
        String api = read("frontend/src/features/admin/api/adminApi.ts");
        String store = read("frontend/src/features/admin/stores/adminStore.ts");
        String shellStore = read("frontend/src/app/shellStore.ts");

        assertTrue(shell.contains("import AdminWorkspace"));
        assertTrue(shell.contains("<AdminWorkspace />"));
        assertTrue(component.contains("data-vue-domain-route=\"admin\""));
        assertTrue(component.contains("id=\"adminUsersList\""));
        assertTrue(component.contains("id=\"registrationEnabledToggle\""));
        assertTrue(component.contains("id=\"diagnosticsList\""));
        assertFalse(component.contains("client.request("));
        assertTrue(api.contains("client.request(\"adminSystemStatus\""));
        assertTrue(api.contains("client.request(\"listAdminUsers\""));
        assertTrue(api.contains("client.request(\"updateAdminUserRole\""));
        assertTrue(api.contains("client.request(\"resetAdminUserPassword\""));
        assertTrue(api.contains("client.request(\"getAdminRegistrationSettings\""));
        assertTrue(api.contains("client.request(\"updateAdminRegistrationSettings\""));
        assertTrue(store.contains("async refreshAll(): Promise<void>"));
        assertTrue(shellStore.contains("snapshot.modulesLoaded && snapshot.modules?.admin === false"));
        assertTrue(shellStore.contains("availableNavigation.filter(route => route !== \"admin\")"));
    }

    @Test
    void noLiveLegacyAdminDomStateApiOrPostVueRouteAdapterRemains() throws Exception {
        String html = read("src/main/resources/static/index.html");
        String core = read("src/main/resources/static/js/10-core.js");
        String data = read("src/main/resources/static/js/20-data.js");
        String settings = read("src/main/resources/static/js/60-settings.js");
        String boot = read("src/main/resources/static/js/70-user-boot.js");
        String router = read("frontend/src/platform/router/hashRoute.ts");
        String app = read("frontend/src/App.vue");

        assertFalse(html.contains("<section class=\"view adminView\""));
        assertTrue(html.contains("Admin is owned by the Vue admin workspace from v27.40.22 onward."));
        assertFalse(core.contains("registrationSettings: null"));
        assertFalse(core.contains("adminUsersPage:"));
        assertFalse(data.contains("jfetch(\"/api/admin/status\")"));
        assertFalse(data.contains("async adminUsers("));
        assertFalse(settings.contains("function refreshAdminPanel"));
        assertFalse(settings.contains("function initAdminNavigation"));
        assertFalse(settings.contains("function initDiagnosticsEvents"));
        assertFalse(boot.contains("applyRemainingLegacyRouteEffects"));
        assertFalse(boot.contains("dutylog:vue-route-committed"));
        assertTrue(boot.contains("name === \"admin\" ? \"settings\""));
        assertFalse(router.contains("publishCommittedHashRoute"));
        assertFalse(app.contains("publishCommittedHashRoute"));
    }

    private static String read(String relative) throws Exception {
        return Files.readString(Path.of(relative));
    }
}
