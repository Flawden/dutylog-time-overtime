package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemporalWorkContextFrontendContractTest {

    private static final Path CARD = Path.of(
            "frontend/src/features/settings-workspace/components/TimeSettingsCard.vue"
    );

    private static final Path STORE = Path.of(
            "frontend/src/features/settings-workspace/stores/settingsWorkspaceStore.ts"
    );

    @Test
    void timezoneSettingsExposeEffectiveDateHistoryAndProtectedBaseline() throws Exception {
        String card = Files.readString(CARD, StandardCharsets.UTF_8);

        assertTrue(card.contains("id=\"workTimezone\""));
        assertTrue(card.contains("id=\"displayTimezone\""));
        assertTrue(card.contains("id=\"timeSaveTimezone\""));

        assertTrue(card.contains("id=\"workTimezoneEffectiveFrom\""));
        assertTrue(card.contains("min=\"1970-01-02\""));
        assertTrue(card.contains(":max=\"currentWorkDate\""));

        assertTrue(card.contains("id=\"workTimezoneHistory\""));
        assertTrue(card.contains("term.baseline ? temporalText.baseline"));
        assertTrue(card.contains("currentTermEffectiveFrom"));
    }

    @Test
    void historicalMutationRequiresExplicitConfirmationAndNativeTemporalCommand() throws Exception {
        String card = Files.readString(CARD, StandardCharsets.UTF_8);
        String store = Files.readString(STORE, StandardCharsets.UTF_8);

        assertTrue(card.contains(
                "window.confirm(temporalText.value.historicalConfirm)"
        ));
        assertTrue(card.contains(
                "settings.saveTimezoneFrom("
        ));
        assertFalse(card.contains(
                "settings.saveTimezone(workTimezone.value, props.bridge)"
        ));

        assertTrue(store.contains(
                "api.updateWorkTimezone({ timezone, effectiveFrom })"
        ));
        assertFalse(store.contains(
                "api.updateProfile({ workTimezone: timezone"
        ));
    }

    @Test
    void historicalDraftDoesNotPretendToChangeCurrentShiftTemplateTimezone() throws Exception {
        String card = Files.readString(CARD, StandardCharsets.UTF_8);

        assertTrue(card.contains(
                "id=\"shiftTemplateZoneHint\" class=\"wideHint\">{{ currentTimezone }}"
        ));
    }
}
