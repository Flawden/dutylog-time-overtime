package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Static regression contract for v27.37.0 browser timer handle typing and compile coverage. */
class VueBrowserTimerHandleTypeFrontendContractTest {

    private static String source(String relativePath) throws IOException {
        return Files.readString(Path.of(relativePath));
    }

    @Test
    void absenceComposerUsesTheBrowserTimerOverload() throws IOException {
        String source = source("frontend/src/features/absence-time-bank/components/AbsenceComposer.vue");
        assertTrue(source.contains("let previewTimer: number | null = null;"));
        assertTrue(source.contains("window.setTimeout(() => { void store.previewAbsence(); }, 260)"));
        assertTrue(source.contains("window.clearTimeout(previewTimer)"));
        assertFalse(source.contains("globalThis.setTimeout"));
    }

    @Test
    void creditEditorUsesTheBrowserTimerOverload() throws IOException {
        String source = source("frontend/src/features/absence-time-bank/components/CreditEditor.vue");
        assertTrue(source.contains("let previewTimer: number | null = null;"));
        assertTrue(source.contains("window.setTimeout(() => { void store.previewCredit(); }, 280)"));
        assertTrue(source.contains("window.clearTimeout(previewTimer)"));
        assertFalse(source.contains("globalThis.setTimeout"));
    }

    @Test
    void bothEditorsCancelThePreviousDebounceBeforeReplacement() throws IOException {
        String absence = source("frontend/src/features/absence-time-bank/components/AbsenceComposer.vue");
        String credit = source("frontend/src/features/absence-time-bank/components/CreditEditor.vue");
        assertTrue(absence.indexOf("window.clearTimeout(previewTimer)") < absence.indexOf("window.setTimeout"));
        assertTrue(credit.indexOf("window.clearTimeout(previewTimer)") < credit.indexOf("window.setTimeout"));
    }

    private static String compact(String value) {
        return value.replaceAll("\\s+", " ").trim();
    }

    @Test
    void bothEditorsCancelPendingWorkDuringUnmount() throws IOException {
        String absence = compact(source("frontend/src/features/absence-time-bank/components/AbsenceComposer.vue"));
        String credit = compact(source("frontend/src/features/absence-time-bank/components/CreditEditor.vue"));
        String cleanup = "onBeforeUnmount(() => { "
                + "if (previewTimer !== null) window.clearTimeout(previewTimer); "
                + "});";
        assertTrue(absence.contains(cleanup));
        assertTrue(credit.contains(cleanup));
    }
}
