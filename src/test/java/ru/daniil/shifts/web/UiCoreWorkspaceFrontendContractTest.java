package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Static contract for UI Core v1, declarative workspaces and isolated theme packages. */
class UiCoreWorkspaceFrontendContractTest {

    @Test
    void appShellLoadsUiPlatformBetweenCoreAndFeatureBundles() throws Exception {
        String html = read("src/main/resources/static/index.html");
        int core = html.indexOf("js/10-core.js?v=27.18.3");
        int platform = html.indexOf("js/12-ui-platform.js?v=27.18.3");
        int data = html.indexOf("js/20-data.js?v=27.18.3");
        assertTrue(core >= 0);
        assertTrue(platform > core);
        assertTrue(data > platform);
        assertTrue(html.contains("ui/tokens.css?v=27.18.3"));
        assertTrue(html.contains("ui/platform.css?v=27.18.3"));
        assertTrue(html.contains("id=\"uiWorkspace\""));
        assertTrue(html.contains("id=\"uiLayout\""));
        assertTrue(html.contains("id=\"uiPalette\""));
        assertTrue(html.contains("id=\"uiPaletteState\""));
        assertTrue(html.contains("id=\"paletteThemeReset\""));
        assertTrue(html.contains("id=\"buttonVariantPreview\""));
        assertTrue(html.contains("id=\"workspaceRouteLinks\""));
    }

    @Test
    void uiPlatformUsesDeclarativeRegistriesInsteadOfDuplicatedScreens() throws Exception {
        String js = read("src/main/resources/static/js/12-ui-platform.js");
        assertTrue(js.contains("const workspaces = Object.freeze"));
        assertTrue(js.contains("const layouts = Object.freeze"));
        assertTrue(js.contains("const themes = Object.freeze"));
        assertTrue(js.contains("const palettes = Object.freeze"));
        assertTrue(js.contains("const screens = Object.freeze"));
        assertTrue(js.contains("const widgets = Object.freeze"));
        assertTrue(js.contains("navigation:[\"today\",\"calendar\",\"overtime\",\"tasks\",\"settings\"]"));
        assertTrue(js.contains("todayWidgets:[\"tasks\",\"important\",\"shift\",\"overtime\"]"));
        assertTrue(js.contains("window.DutyLogUI = api"));
        assertTrue(js.contains("const navigationUniverse = Object.freeze"));
        assertFalse(js.contains("classicNavigation"));
        assertFalse(js.contains("cfg.shellMode"));
        assertFalse(js.contains("fetch("));
        assertFalse(js.contains("jfetch("));
    }

    @Test
    void semanticTokensAndThemePackagesAreScopedByUiTheme() throws Exception {
        String tokens = read("src/main/resources/static/ui/tokens.css");
        String midnight = read("src/main/resources/static/ui/themes/midnight.css");
        String platform = read("src/main/resources/static/ui/platform.css");
        String core = read("src/main/resources/static/js/10-core.js");
        assertTrue(tokens.contains("--color-background"));
        assertTrue(tokens.contains("--color-surface"));
        assertTrue(tokens.contains("--color-text-primary"));
        assertTrue(tokens.contains("--color-accent-secondary"));
        assertTrue(tokens.contains("--button-outline-border"));
        assertTrue(tokens.contains("--button-ghost-border: transparent"));
        assertTrue(platform.contains("[data-button-style=\"outline\"]"));
        assertTrue(platform.contains("[data-button-style=\"ghost\"]"));
        assertTrue(platform.contains("border:1px solid var(--button-ghost-border)"));
        assertTrue(core.contains("function restoreThemePalette()"));
        assertTrue(core.contains("paletteId:\"theme\""));
        assertTrue(midnight.contains("html[data-ui-theme=\"midnight\"]"));
        assertFalse(midnight.contains("data-ui-theme=\"oled\""));
        assertTrue(platform.contains("html[data-ui-layout=\"compact\"]"));
        assertTrue(platform.contains("html[data-ui-layout=\"focus\"]"));
        assertTrue(platform.contains(".workspaceHidden"));
    }

    @Test
    void profileWhitelistPersistsOnlyKnownUiPlatformFields() throws Exception {
        String profile = read("src/main/java/ru/daniil/shifts/web/ProfileController.java");
        assertTrue(profile.contains("out.put(\"uiContract\""));
        assertTrue(profile.contains("out.put(\"workspaceId\""));
        assertTrue(profile.contains("out.put(\"layoutId\""));
        assertTrue(profile.contains("out.put(\"themeId\""));
        assertTrue(profile.contains("out.put(\"paletteId\""));
        assertTrue(profile.contains("out.put(\"todayWidgets\""));
        assertTrue(profile.contains("private List<String> safeStringList"));
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
