package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Static contract for the additive DutyLog Next shell and Classic fallback. */
class DesignSystemMobileShellFrontendContractTest {

    @Test
    void htmlLoadsTheLayeredDesignSystemAndExposesAccessibleShellControls() throws Exception {
        String html = read("src/main/resources/static/index.html");
        assertTrue(html.contains("js/shell-bootstrap.js?v=27.17.4"));
        assertTrue(html.contains("design-system.css?v=27.17.4"));
        assertTrue(html.contains("id=\"nextTopbar\""));
        assertTrue(html.contains("id=\"nextHeaderAvatar\""));
        assertTrue(html.contains("class=\"navIcon\""));
        assertTrue(html.contains("id=\"themeShellMode\""));
        assertTrue(html.contains("data-shell-choice=\"next\""));
        assertTrue(html.contains("data-shell-choice=\"classic\""));
    }

    @Test
    void appearanceContractDefaultsToNextButKeepsClassicAsASafeServerStoredEnum() throws Exception {
        String bootstrap = read("src/main/resources/static/js/shell-bootstrap.js");
        String core = read("src/main/resources/static/js/10-core.js");
        String profile = read("src/main/java/ru/daniil/shifts/web/ProfileController.java");
        assertTrue(core.contains("shellMode:\"next\""));
        assertTrue(core.contains("[\"next\",\"classic\"]"));
        assertTrue(core.contains("root.dataset.shell = cfg.shellMode"));
        assertTrue(profile.contains("safeEnum(input.get(\"shellMode\"), \"next\", \"next\", \"classic\")"));
    }

    @Test
    void cssDefinesTokensBottomNavigationReducedMotionAndAnIsolatedClassicFallback() throws Exception {
        String css = read("src/main/resources/static/design-system.css");
        assertTrue(css.contains("--ds-space-1"));
        assertTrue(css.contains("html[data-shell=\"next\"] .tabbar"));
        assertTrue(css.contains("position: fixed"));
        assertTrue(css.contains("env(safe-area-inset-bottom"));
        assertTrue(css.contains("@media (prefers-reduced-motion: reduce)"));
        assertTrue(css.contains("html[data-shell=\"classic\"] .tabbar a[data-view=\"today\"]"));
        assertTrue(css.contains("html[data-shell=\"next\"] .tabbar a[data-view=\"important\"]"));
        assertTrue(css.contains(".nextTopbar,\n.navIcon"));
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
