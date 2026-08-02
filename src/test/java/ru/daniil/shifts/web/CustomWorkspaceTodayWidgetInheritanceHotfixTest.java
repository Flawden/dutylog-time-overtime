package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regression contract for v27.29.2 Custom Workspace Today Widget Inheritance Hotfix. */
class CustomWorkspaceTodayWidgetInheritanceHotfixTest {

    @Test
    void customWorkspaceCopiesTheActivePresetNavigationAndTodayWidgets() throws Exception {
        String studio = read("src/main/resources/static/js/12-ui-platform.js");
        String browser = read("e2e/workspace-layout-theme-studio.spec.js");

        assertTrue(studio.contains("navigationVisible:[...workspace.navigation],"));
        assertTrue(studio.contains("todayWidgets:[...workspace.todayWidgets]"));
        assertTrue(browser.contains("studioRow(page, 'widget', 'overtime').locator('[data-studio-visible]')).toBeChecked()"));
        assertTrue(browser.contains("studioRow(page, 'widget', 'tasks').locator('[data-studio-visible]')).toBeChecked()"));
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
