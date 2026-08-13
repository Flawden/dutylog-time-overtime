package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** v27.40.26 ownership contract: Vue owns offline/sync presentation, dataLayer owns execution. */
class VueOfflineSyncSurfaceFrontendContractTest {

    @Test
    void vueOwnsThePostReadyOfflineStatusDialogAndActions() throws Exception {
        String shell = read("frontend/src/app/AppShell.vue");
        String modal = read("frontend/src/app/OfflineSyncModal.vue");
        String bridge = read("frontend/src/platform/bridge/legacyBridge.ts");
        String css = read("frontend/src/styles/design-system.css");

        assertTrue(shell.contains("id=\"offlineStatus\""));
        assertTrue(shell.contains("<OfflineSyncModal"));
        assertTrue(modal.contains("id=\"offlineSyncDialog\""));
        assertTrue(modal.contains("id=\"offlineSyncNow\""));
        assertTrue(modal.contains("id=\"offlineFailedRetryAll\""));
        assertTrue(modal.contains("id=\"offlineExport\""));
        assertTrue(modal.contains("id=\"offlineFailedClear\""));
        assertTrue(modal.contains("id=\"offlineDiagnosticsCopy\""));
        assertTrue(bridge.contains("offlineSyncDetails(): Promise<DutyLogOfflineSyncDetailsSnapshot | null>"));
        assertTrue(css.contains(".vue-shell-header__actions > .vue-shell-sync-status.ui-badge"));
        assertFalse(modal.contains("dataLayer" + ".syncQueue()"));
    }

    @Test
    void legacyOfflinePresentationIsRecoveryOnlyAfterVueReadiness() throws Exception {
        String html = read("src/main/resources/static/index.html");
        String bootstrap = read("src/main/resources/static/js/shell-bootstrap.js");
        String data = read("src/main/resources/static/js/20-data.js");

        assertTrue(html.contains("id=\"legacyGlobalHeader\""));
        assertTrue(html.contains("id=\"offlineSyncDialog\""));
        assertTrue(bootstrap.contains("document.getElementById(\"legacyGlobalHeader\")?.remove();"));
        assertFalse(bootstrap.contains("document.querySelector(\"body > .head\")?.remove();"));
        assertTrue(bootstrap.contains("document.getElementById(\"offlineSyncDialog\")?.remove();"));
        assertTrue(bootstrap.contains("dataset.vueOfflineSync = \"ready\""));
        assertTrue(data.contains("dataset.vueOfflineSync === \"ready\""));
        assertTrue(data.contains("publishLegacyPlatformState();"));
    }

    @Test
    void dataLayerRemainsTheSingleOfflineQueueExecutorAndSaveFeedbackMovesToVue() throws Exception {
        String data = read("src/main/resources/static/js/20-data.js");
        String core = read("src/main/resources/static/js/10-core.js");
        String app = read("frontend/src/App.vue");
        String bridge = read("frontend/src/platform/bridge/legacyBridge.ts");

        assertTrue(data.contains("const dataLayer = " + Character.toString(123)));
        assertTrue(data.contains("async syncQueue()"));
        assertTrue(core.contains("await dataLayer.syncQueue()"));
        assertFalse(app.contains("syncQueue()"));
        assertTrue(data.contains("new CustomEvent(\"dutylog:save-feedback\""));
        assertTrue(bridge.contains("SAVE_FEEDBACK_EVENT = \"dutylog:save-feedback\""));
        assertTrue(app.contains("window.addEventListener(SAVE_FEEDBACK_EVENT, synchronizeSaveFeedback)"));
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
