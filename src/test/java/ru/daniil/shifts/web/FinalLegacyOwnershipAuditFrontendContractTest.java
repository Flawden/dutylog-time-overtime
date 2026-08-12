package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Final post-Vue ownership audit contract for v27.40.24. */
class FinalLegacyOwnershipAuditFrontendContractTest {

    @Test
    void recoveryChromeExistsInSourceButIsPhysicallyRetiredAfterVueReadiness() throws Exception {
        String html = read("src/main/resources/static/index.html");
        String bootstrap = read("src/main/resources/static/js/shell-bootstrap.js");
        String css = read("frontend/src/styles/design-system.css");

        assertTrue(html.contains("id=\"nextTopbar\""));
        assertTrue(html.contains("id=\"tabbar\""));
        assertTrue(bootstrap.contains("dutylog:vue-ready"));
        assertTrue(bootstrap.contains("for (const id of [\"nextTopbar\", \"tabbar\"])"));
        assertTrue(bootstrap.contains("document.getElementById(id)?.remove();"));
        assertFalse(css.contains("html[data-vue-shell=\"ready\"] .nextTopbar"));
        assertFalse(css.contains("html[data-vue-shell=\"ready\"] #tabbar"));
    }

    @Test
    void domainRetirementPhysicallyRemovesDeadMigrationFallbackSurfaces() throws Exception {
        String html = read("src/main/resources/static/index.html");
        String core = read("src/main/resources/static/js/10-core.js");
        String absence = between(core, "if (domain === \"absence-time-bank\")", "if (domain === \"settings-workspace\")");
        String settings = between(core, "if (domain === \"settings-workspace\")", "if (domain === \"productivity\")");

        // Pre-Vue recovery markup remains available in the server shell.
        assertTrue(html.contains("id=\"legacyShiftModal\""));
        assertTrue(html.contains("id=\"legacyTaskDeadlineModal\""));
        assertTrue(html.contains("id=\"legacyOvertimeModal\""));
        assertTrue(html.contains("id=\"legacyUsageMigrationModal\""));

        // Post-ready ownership does not leave those fallback surfaces mounted.
        assertTrue(settings.contains("legacyShiftModal"));
        assertTrue(settings.contains("legacyTaskDeadlineModal"));
        assertTrue(absence.contains("legacyOvertimeModal"));
        assertTrue(absence.contains("legacyUsageMigrationModal"));
    }

    @Test
    void remainingLegacyPresentationAndInfrastructureExceptionsAreExplicitAndBounded() throws Exception {
        String html = read("src/main/resources/static/index.html");
        String data = read("src/main/resources/static/js/20-data.js");
        String boot = read("src/main/resources/static/js/70-user-boot.js");
        String architecture = read("docs/FRONTEND_ARCHITECTURE.md");

        assertFalse(html.contains("id=\"view-admin\""));
        assertFalse(html.contains("id=\"view-payroll\""));
        assertFalse(boot.contains("applyRemainingLegacyRouteEffects"));
        assertFalse(boot.contains("dutylog:vue-route-committed"));

        assertTrue(html.contains("id=\"firstRunOnboarding\""));
        assertTrue(html.contains("id=\"offlineStatus\""));
        assertTrue(html.contains("id=\"offlineSyncDialog\""));
        assertTrue(data.contains("const dataLayer = " + Character.toString(123)));
        assertTrue(data.contains("async syncQueue()"));
        assertTrue(data.contains("dataLayer.syncQueue()"));

        assertTrue(architecture.contains("Vue owns all user-facing screens"));
        assertTrue(architecture.contains("Known live legacy presentation is limited to first-run onboarding and offline/sync UX"));
        assertTrue(architecture.contains("dataLayer remains the single offline mutation/sync owner"));
    }

    private static String between(String source, String start, String end) {
        int from = source.indexOf(start);
        int to = source.indexOf(end, from + start.length());
        if (from < 0 || to < 0 || to <= from) {
            throw new AssertionError("Expected source section not found: " + start + " -> " + end);
        }
        return source.substring(from, to);
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
