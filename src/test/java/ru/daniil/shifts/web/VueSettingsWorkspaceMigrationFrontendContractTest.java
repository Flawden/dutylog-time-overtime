package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Binding source contracts for v27.39.0 Vue Settings, Workspace & Integrations. */
class VueSettingsWorkspaceMigrationFrontendContractTest {

    private static final Path FEATURE = Path.of("frontend/src/features/settings-workspace");

    @Test
    void appShellInstallsOneVueSettingsOwnerAndKeepsOnlyNamedCompatibilityIslands() throws Exception {
        String shell = read("frontend/src/app/AppShell.vue");
        String workspace = read(FEATURE.resolve("components/SettingsWorkspace.vue"));
        String core = read("src/main/resources/static/js/10-core.js");

        assertTrue(shell.contains("SettingsWorkspace"));
        assertTrue(workspace.contains("retireDomainOwners(\"settings-workspace\")"));
        assertTrue(workspace.contains("data-vue-domain-owner=\"settings-workspace\""));
        assertTrue(workspace.contains("id=\"settingsLegacyHost\""));
        assertTrue(core.contains("attachLegacySettingsCards"));
        assertTrue(core.contains("settingsLegacyParking"));
        assertTrue(core.contains("root.dataset.vueSettingsWorkspace = \"ready\""));
        assertTrue(core.contains("view-settings"));
    }

    @Test
    void migratedSettingsWritesUseGeneratedV1OperationsInsteadOfLegacyTransport() throws Exception {
        String api = read(FEATURE.resolve("api/settingsWorkspaceApi.ts"));
        String source = featureSources();
        String generated = read("frontend/src/generated/dutylog-api.ts");

        assertTrue(api.contains("createGeneratedDutyLogApiClient"));
        assertTrue(api.contains("client.request(\"updateProfile\""));
        assertTrue(api.contains("client.request(\"updateModules\""));
        assertTrue(api.contains("client.request(\"rotateCalendarSubscription\""));
        assertTrue(api.contains("client.request(\"getTelegramStatus\""));
        assertTrue(generated.contains("\"updateModules\": { method: \"PATCH\", path: \"/api/v1/modules\" }"));
        assertTrue(generated.contains("\"rotateCalendarSubscription\": { method: \"POST\", path: \"/api/v1/calendar-sync/subscription\" }"));
        assertFalse(source.contains("jfetch("));
        assertFalse(source.contains("fetch("));
    }

    @Test
    void moduleToggleKeepsDisableBeforePatchAndEnableAfterBackendAuthority() throws Exception {
        String store = read(FEATURE.resolve("stores/settingsWorkspaceStore.ts"));
        String bridge = read("src/main/resources/static/js/10-core.js");

        int disablePreview = store.indexOf("if (!enabled)");
        int update = store.indexOf("api.updateModules", disablePreview);
        int commit = store.indexOf("bridge.commitModuleList", update);
        assertTrue(disablePreview >= 0 && update > disablePreview && commit > update);
        assertTrue(store.contains("bridge.previewModuleEnabled(key, false)"));
        assertTrue(store.contains("bridge.restoreModuleList"));
        assertTrue(bridge.contains("await loadMonth({ fresh:true })"));
        assertTrue(bridge.contains("await refreshModuleAwareData"));
    }

    @Test
    void appearanceModelPreservesUiContractWorkspaceRulesAndBackendPersistence() throws Exception {
        String model = read(FEATURE.resolve("types/model.ts"));
        String appearance = read(FEATURE.resolve("components/AppearanceSettingsCard.vue"));
        String store = read(FEATURE.resolve("stores/settingsWorkspaceStore.ts"));

        assertTrue(model.contains("uiContract: 2"));
        assertTrue(model.contains("navigationVisible"));
        assertTrue(model.contains("selected.add(\"today\")"));
        assertTrue(model.contains("selected.add(\"settings\")"));
        assertTrue(model.contains("slice(0, 5)"));
        assertTrue(appearance.contains("id=\"workspaceStudio\""));
        assertTrue(appearance.contains("data-studio-kind=\"navigation\""));
        assertTrue(store.contains("scheduleAppearanceSave"));
        assertTrue(store.contains("api.updateProfile"));
    }

    @Test
    void integrationSecretsStayVolatileAndProductionSourceMapsAreOptInHidden() throws Exception {
        String store = read(FEATURE.resolve("stores/settingsWorkspaceStore.ts"));
        String vite = read("frontend/vite.config.ts");
        String diagnostics = read("docs/FRONTEND_DIAGNOSTICS.md");
        String adr = read("docs/architecture/adr/ADR-008-production-source-maps-and-frontend-diagnostics.md");

        assertTrue(store.contains("calendarSyncIssuedUrl"));
        assertFalse(store.contains("localStorage"));
        assertTrue(vite.contains("DUTYLOG_FRONTEND_SOURCEMAPS"));
        assertTrue(vite.contains("? \"hidden\" : false"));
        assertTrue(diagnostics.contains("source maps fail-closed"));
        assertTrue(adr.contains("bearer URLs"));
        assertTrue(adr.contains("not deployed with public static assets"));
    }

    @Test
    void browserParityRequiresCanonicalSettingsAndCalendarSyncRoutes() throws Exception {
        String helper = read("e2e/helpers.js");
        String calendarSync = read("e2e/external-calendar-sync.spec.js");
        String migration = read("e2e/vue-settings-workspace.spec.js");

        assertTrue(helper.contains("waitForApi(page, 'PATCH', '/api/v1/modules')"));
        assertTrue(calendarSync.contains("url.pathname === '/api/v1/calendar-sync/subscription'"));
        assertTrue(migration.contains("data-vue-settings-workspace"));
        assertTrue(migration.contains("#settingsLegacyHost #timeSettingsCard"));
        assertTrue(migration.contains("waitForApi(page, 'PUT', '/api/v1/profile')"));
        assertFalse(migration.contains("waitForTimeout"));
    }

    private static String featureSources() throws Exception {
        StringBuilder out = new StringBuilder();
        try (var paths = Files.walk(FEATURE)) {
            for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
                out.append(read(path)).append('\n');
            }
        }
        return out.toString();
    }

    private static String read(String path) throws Exception { return read(Path.of(path)); }
    private static String read(Path path) throws Exception { return Files.readString(path, StandardCharsets.UTF_8); }
}
