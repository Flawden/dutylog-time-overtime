package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regression contract for v27.34.4 secondary navigation and non-persistent overtime previews. */
class VueSecondaryNavigationOvertimePreviewHotfixTest {

    @Test
    void secondaryRoutesExposeTheirActiveStateThroughVisibleVueControls() throws Exception {
        String navigation = read("frontend/src/app/AppNavigation.vue");
        String shell = read("frontend/src/app/AppShell.vue");
        String browser = read("e2e/vue-app-shell.spec.js");

        assertTrue(navigation.contains("secondaryNavigation.value.includes(activeRoute.value)"));
        assertTrue(navigation.contains(":aria-current=\"secondaryActive ? 'page' : false\""));
        assertTrue(shell.contains(":aria-current=\"activeRoute === item.route ? 'page' : false\""));
        assertTrue(browser.contains("page.locator('[data-vue-shell-more]')"));
        assertTrue(browser.contains(".vue-shell-more-grid [data-route=\"tasks\"]"));
        assertFalse(browser.contains("[data-vue-shell-navigation] [data-route=\"tasks\"]"));
    }

    @Test
    void previewAllowsAZeroDraftWithoutWeakeningPersistenceValidation() throws Exception {
        String service = read("src/main/java/ru/daniil/shifts/service/OvertimeService.java");
        String browser = read("e2e/overtime-scenario-manager.spec.js");

        assertTrue(service.contains("calculateCredit(user, req, null, false)"));
        assertTrue(service.contains("return calculateCredit(user, req, null, true)"));
        assertTrue(service.contains("creditedMinutes <= 0 && requirePositiveCalculatedHours"));
        assertTrue(browser.contains("waitForApi(page, 'POST', '/api/v1/overtime/preview', 200)"));
        assertTrue(browser.contains("expect(zeroDraftBody.creditedMinutes).toBe(0)"));
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
