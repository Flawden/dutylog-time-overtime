package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Static architecture contract for v27.36.3 Vue App Shell & Design System. */
class VueAppShellDesignSystemContractTest {

    @Test
    void vueOwnsVisibleBrandNavigationProfileAndResponsiveShellChrome() throws Exception {
        String appShell = read("frontend/src/app/AppShell.vue");
        String navigation = read("frontend/src/app/AppNavigation.vue");
        String css = read("frontend/src/styles/design-system.css");
        String html = read("src/main/resources/static/index.html");

        assertTrue(appShell.contains("data-vue-app-shell"));
        assertTrue(navigation.contains("data-vue-shell-navigation"));
        assertTrue(navigation.contains("aria-current"));
        assertTrue(css.contains("html[data-vue-shell=\"ready\"] .nextTopbar"));
        assertTrue(css.contains("html[data-vue-shell=\"ready\"] #tabbar"));
        assertTrue(css.contains("@media (max-width: 840px)"));
        assertTrue(css.contains("@media (prefers-reduced-motion: reduce)"));
        assertTrue(html.contains("data-vue-app-shell-host=\"true\""));
    }

    @Test
    void immutableLegacyReadModelPublishesRouteWorkspaceNetworkAndProfileChanges() throws Exception {
        String core = read("src/main/resources/static/js/10-core.js");
        String boot = read("src/main/resources/static/js/70-user-boot.js");
        String modules = read("src/main/resources/static/js/20-data.js");
        String uiPlatform = read("src/main/resources/static/js/12-ui-platform.js");
        String bridge = read("frontend/src/platform/bridge/legacyBridge.ts");

        assertTrue(core.contains("function legacyPlatformSnapshot()"));
        assertTrue(core.contains("navigation:Object.freeze"));
        assertTrue(core.contains("availableViews:Object.freeze"));
        assertTrue(core.contains("profile:legacyProfileSnapshot()"));
        assertTrue(core.contains("function publishLegacyPlatformState()"));
        assertTrue(core.contains("subscribe(listener)"));
        assertTrue(bridge.contains("dutylog:legacy-state"));
        assertTrue(bridge.contains("subscribe(listener"));
        assertTrue(boot.contains("publishLegacyPlatformState();"));
        assertTrue(modules.contains("publishLegacyPlatformState();"));
        assertTrue(uiPlatform.contains("publishLegacyPlatformState"));
    }

    @Test
    void sharedDesignSystemProvidesTypedPrimitivesAndVueOwnedOverlayInfrastructure() throws Exception {
        String sources = readTree("frontend/src/shared");
        String shell = read("frontend/src/app/AppShell.vue");

        assertTrue(sources.contains("class=\"ui-button\""));
        assertTrue(sources.contains("class=\"ui-badge\""));
        assertTrue(sources.contains("class=\"ui-card\""));
        assertTrue(sources.contains("class=\"ui-tabs\""));
        assertTrue(sources.contains("class=\"ui-empty-state\""));
        assertTrue(sources.contains("class=\"ui-modal-backdrop\""));
        assertTrue(sources.contains("class=\"ui-toast-region\""));
        assertTrue(sources.contains("<Teleport to=\"body\">"));
        assertTrue(shell.contains("<UiModal"));
        assertTrue(shell.contains("<ToastHost"));
    }

    @Test
    void vueShellKeepsProductScreensLegacyOwnedThroughNamedCapabilitiesOnly() throws Exception {
        String frontendSources = readTree("frontend/src");
        String architecture = read("docs/FRONTEND_ARCHITECTURE.md");

        assertTrue(frontendSources.contains("bridge.navigate("));
        assertTrue(frontendSources.contains("bridge.logout()"));
        assertFalse(frontendSources.contains("document.querySelector("));
        assertFalse(frontendSources.contains("window.state"));
        assertFalse(frontendSources.contains("getElementById(\"tabbar\")"));
        assertTrue(architecture.contains("Vue owns the application shell"));
        assertTrue(architecture.contains("Legacy product screens remain authoritative"));
    }

    @Test
    void releaseBuildShipsOneShellBundleAndBrowserParityScenario() throws Exception {
        String vite = read("frontend/vite.config.ts");
        String docker = read("Dockerfile");
        String gate = read("deploy/scripts/frontend-gate.sh");
        String html = read("src/main/resources/static/index.html");
        String e2e = read("e2e/vue-app-shell.spec.js");

        assertTrue(vite.contains("vue-shell-v1"));
        assertTrue(vite.contains("dutylog-vue-app-shell.js"));
        assertTrue(docker.contains("dist/dutylog-vue-app-shell.js"));
        assertTrue(gate.contains("dist/dutylog-vue-app-shell.css"));
        assertTrue(html.contains("/vue/dutylog-vue-app-shell.js?v=27.36.3"));
        assertTrue(e2e.contains("Vue app shell owns navigation chrome"));
        assertTrue(e2e.contains("#tabbar"));
        assertTrue(e2e.contains("data-route=\"calendar\""));
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }

    private static String readTree(String root) throws Exception {
        try (var files = Files.walk(Path.of(root))) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".ts") || path.toString().endsWith(".vue"))
                    .sorted()
                    .map(path -> {
                        try {
                            return Files.readString(path, StandardCharsets.UTF_8);
                        } catch (Exception ex) {
                            throw new IllegalStateException(ex);
                        }
                    })
                    .collect(Collectors.joining("\n"));
        }
    }
}
