package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Compile-gated source contracts for the v27.37.1 strict TypeScript hotfix. */
class VueCalendarTimelineStrictTypecheckHotfixTest {

    private static final Path FEATURE = Path.of("frontend/src/features/calendar-timeline");

    @Test
    void workspaceBridgeCallbackCarriesExplicitGeneratedDomainTypes() throws Exception {
        String workspace = read(FEATURE.resolve("components/CalendarTimelineWorkspace.vue"));

        assertTrue(workspace.contains("import type { CalendarMode, DutyLogCalendarTimelineDomain }"));
        assertTrue(workspace.contains("openDate: async (date: string, mode?: CalendarMode)"));
        assertTrue(workspace.contains("await store.openDate(date, mode)"));
        assertFalse(workspace.contains("openDate: async (date, mode)"));
    }

    @Test
    void openDateResolvesTheOptionalModeInsideThePiniaActionBody() throws Exception {
        String store = read(FEATURE.resolve("stores/calendarTimelineStore.ts"));

        assertTrue(store.contains("async openDate(date: string, mode?: CalendarMode): Promise<void>"));
        assertTrue(store.contains("const resolvedMode = mode ?? this.mode"));
        assertTrue(store.contains("this.mode = resolvedMode"));
        assertFalse(store.contains("mode: CalendarMode = this.mode"));
    }

    @Test
    void goTodayAvoidsThisInADefaultParameterInitializer() throws Exception {
        String store = read(FEATURE.resolve("stores/calendarTimelineStore.ts"));

        assertTrue(store.contains("async goToday(mode?: CalendarMode): Promise<void>"));
        assertTrue(store.contains("mode ?? this.mode"));
        assertFalse(store.contains("async goToday(mode: CalendarMode = this.mode)"));
    }

    @Test
    void strictCompilerConfigurationRemainsEnabled() throws Exception {
        String config = read(Path.of("frontend/tsconfig.json"));

        assertTrue(config.contains("\"strict\": true"));
        assertTrue(config.contains("\"noImplicitAny\": true") || config.contains("\"strict\": true"));
    }

    private static String read(Path path) throws Exception {
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
