package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Static contract for v27.29.0 Workspace, Layout & Theme Studio. */
class WorkspaceLayoutThemeStudioFrontendContractTest {

    @Test
    void uiContractV2NormalizesBoundedNavigationAndCalendarPreferences() throws Exception {
        String core = read("src/main/resources/static/js/10-core.js");
        assertTrue(core.contains("const UI_CONTRACT_VERSION = 2"));
        assertTrue(core.contains("const UI_NAVIGATION_IDS = Object.freeze"));
        assertTrue(core.contains("navigationVisible:[\"today\",\"calendar\",\"vacation\",\"overtime\",\"settings\"]"));
        assertTrue(core.contains("slice(0, 5)"));
        assertTrue(core.contains("out.navigationVisible.includes(\"today\")"));
        assertTrue(core.contains("out.navigationVisible.includes(\"settings\")"));
        assertTrue(core.contains("out.calendarDensity"));
        assertTrue(core.contains("out.calendarLayerStyle"));
    }

    @Test
    void studioEditsDeclarativeConfigurationWithoutFeatureApiCalls() throws Exception {
        String studio = read("src/main/resources/static/js/12-ui-platform.js");
        assertTrue(studio.contains("function workspaceDefinition(cfg)"));
        assertTrue(studio.contains("function renderStudio(cfg)"));
        assertTrue(studio.contains("function persistStudioPatch(patch)"));
        assertTrue(studio.contains("navigationOrder"));
        assertTrue(studio.contains("navigationVisible"));
        assertTrue(studio.contains("todayWidgets"));
        assertTrue(studio.contains("selected.add(\"shift\")"));
        String vueModel = read("frontend/src/features/settings-workspace/types/model.ts");
        assertTrue(vueModel.contains("const orderedVisibleWidgets = source.filter"));
        assertTrue(vueModel.contains("visibleWidgets?.has(item)"));
        assertTrue(studio.contains("ordered.length > 5"));
        assertFalse(studio.contains("jfetch("));
        assertFalse(studio.contains("fetch("));
        assertFalse(studio.contains("innerHTML = user"));
    }

    @Test
    void sidebarMobileFlowAndCalendarLayerModesStayCssScoped() throws Exception {
        String css = read("src/main/resources/static/ui/platform.css");
        assertTrue(css.contains("html[data-ui-layout=\"sidebar\"][data-shell=\"next\"]"));
        assertTrue(css.contains("html[data-ui-layout=\"mobile-flow\"][data-shell=\"next\"]"));
        assertTrue(css.contains("html[data-ui-calendar-density=\"compact\"][data-shell=\"next\"] .cell"));
        assertTrue(css.contains("html[data-ui-calendar-layers=\"dots\"][data-shell=\"next\"] .calendarLayerChip"));
        assertTrue(css.contains("html[data-shell=\"next\"][data-ui-decoration=\"grid\"] body::before"));
        assertTrue(css.contains("pointer-events:none"));
        assertFalse(css.contains("body *"));
    }

    @Test
    void synchronousBootstrapPreventsLayoutAndDecorationFlash() throws Exception {
        String bootstrap = read("src/main/resources/static/js/shell-bootstrap.js");
        String html = read("src/main/resources/static/index.html");
        assertTrue(bootstrap.contains("root.dataset.uiContract = \"2\""));
        assertTrue(bootstrap.contains("\"sidebar\",\"mobile-flow\""));
        assertTrue(bootstrap.contains("root.dataset.uiDecoration"));
        assertTrue(bootstrap.contains("root.dataset.uiCalendarDensity"));
        assertTrue(bootstrap.contains("root.dataset.uiCalendarLayers"));
        assertTrue(html.indexOf("js/shell-bootstrap.js?v=") < html.indexOf("ui/tokens.css?v="));
    }

    @Test
    void themeAndDecorationPackagesDeclareCompatibilityAndRemainDataScoped() throws Exception {
        String studio = read("src/main/resources/static/js/12-ui-platform.js");
        String midnight = read("src/main/resources/static/ui/themes/midnight.css");
        String forest = read("src/main/resources/static/ui/themes/forest.css");
        assertTrue(studio.contains("supportsCustomPalette:true"));
        assertTrue(studio.contains("tokenScope:`html[data-ui-theme=\"${id}\"]`"));
        assertTrue(studio.contains("pointerEvents:\"none\""));
        assertTrue(midnight.contains("html[data-ui-theme=\"midnight\"]"));
        assertFalse(midnight.contains("data-ui-theme=\"forest\""));
        assertTrue(forest.contains("html[data-ui-theme=\"forest\"]"));
        assertFalse(forest.contains("data-ui-theme=\"midnight\""));
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
