package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regression contract for v27.29.3 Custom Workspace Today Widget Order Persistence Hotfix. */
class CustomWorkspaceTodayWidgetOrderPersistenceHotfixTest {

    @Test
    void serverPreservesExplicitWidgetOrderWhileStillRestoringRequiredShift() throws Exception {
        String profile = Files.readString(Path.of("src/main/java/ru/daniil/shifts/web/ProfileController.java"));
        String browser = Files.readString(Path.of("e2e/workspace-layout-theme-studio.spec.js"));

        assertTrue(profile.contains("LinkedHashSet<String> result = new LinkedHashSet<>(selected);"));
        assertTrue(profile.contains("if (!result.contains(\"shift\"))"));
        assertTrue(profile.contains("withRequiredShift.add(\"shift\");"));
        assertFalse(profile.contains("result.add(\"shift\");\n        result.addAll(selected);"));
        assertTrue(browser.contains("tasks.compareDocumentPosition(shift)"));
        assertTrue(browser.contains("Node.DOCUMENT_POSITION_FOLLOWING"));
    }
}
