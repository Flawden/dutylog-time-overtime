package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regression contract for v27.38.11 Vue-owned shell navigation and E2E compatibility. */
class VueShellE2eNavigationCompatibilityHotfixTest {

    @Test
    void browserSuiteUsesThePublicShellBoundaryInsteadOfHiddenLegacyChrome() throws Exception {
        String helpers = read("e2e/helpers.js");
        String e2e = readTree("e2e");
        String shell = read("frontend/src/app/AppShell.vue");
        String navigation = read("frontend/src/app/AppNavigation.vue");
        String calendarSync = read("e2e/external-calendar-sync.spec.js");

        assertTrue(helpers.contains("window.DutyLogVuePlatform"));
        assertTrue(helpers.contains("platform.navigateLegacy(target)"));
        assertTrue(helpers.contains("window.DutyLogLegacyPlatform?.navigate(target)"));
        assertTrue(helpers.contains("async function waitForVueShell(page)"));
        assertFalse(e2e.contains("#tabbar a[data-view="));
        assertFalse(e2e.contains("page.locator('.brandLockup').click()"));
        assertFalse(e2e.contains("page.locator('#logout').click()"));
        assertTrue(shell.contains("data-vue-shell-brand"));
        assertTrue(shell.contains("data-vue-shell-profile"));
        assertTrue(shell.contains("data-vue-shell-logout"));
        assertTrue(navigation.contains("data-vue-shell-more"));
        assertTrue(calendarSync.contains("Time and Overtime 27.38.11//RU"));
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }

    private static String readTree(String root) throws Exception {
        try (var files = Files.walk(Path.of(root))) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".js"))
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
