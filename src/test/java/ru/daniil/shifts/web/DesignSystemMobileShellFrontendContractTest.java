package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Static contract for the single DutyLog UI Core shell after Classic Sunset. */
class DesignSystemMobileShellFrontendContractTest {

    @Test
    void htmlLoadsTheLayeredDesignSystemWithoutAClassicSelector() throws Exception {
        String html = read("src/main/resources/static/index.html");
        assertTrue(html.contains("js/shell-bootstrap.js?v=27.18.0"));
        assertTrue(html.contains("design-system.css?v=27.18.0"));
        assertTrue(html.contains("id=\"nextTopbar\""));
        assertTrue(html.contains("id=\"nextHeaderAvatar\""));
        assertTrue(html.contains("class=\"navIcon\""));
        assertTrue(html.contains("id=\"singleShellNotice\""));
        assertFalse(html.contains("themeShellMode"));
        assertFalse(html.contains("data-shell-choice"));
    }

    @Test
    void bootstrapAndRuntimeIgnoreLegacyClassicPreferences() throws Exception {
        String bootstrap = read("src/main/resources/static/js/shell-bootstrap.js");
        String core = read("src/main/resources/static/js/10-core.js");
        String profile = read("src/main/java/ru/daniil/shifts/web/ProfileController.java");
        assertTrue(bootstrap.contains("root.dataset.shell = \"next\""));
        assertTrue(core.contains("root.dataset.shell = \"next\""));
        assertFalse(core.contains("shellMode"));
        assertFalse(profile.contains("out.put(\"shellMode\""));
        assertFalse(profile.contains("input.get(\"shellMode\")"));
    }

    @Test
    void cssKeepsTheResponsiveShellButContainsNoClassicOnlySelectors() throws Exception {
        String css = read("src/main/resources/static/design-system.css");
        assertTrue(css.contains("--ds-space-1"));
        assertTrue(css.contains("html[data-shell=\"next\"] .tabbar"));
        assertTrue(css.contains("position: fixed"));
        assertTrue(css.contains("env(safe-area-inset-bottom"));
        assertTrue(css.contains("@media (prefers-reduced-motion: reduce)"));
        assertTrue(css.contains(".nextTopbar,\n.navIcon"));
        assertFalse(css.contains("data-shell=\"classic\""));
        assertFalse(css.contains(".shellChoice"));
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
