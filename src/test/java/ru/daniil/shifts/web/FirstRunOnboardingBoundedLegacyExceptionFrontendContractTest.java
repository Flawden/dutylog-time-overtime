package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** v27.40.30 contract: first-run onboarding is one bounded presentation exception, not a second app owner. */
class FirstRunOnboardingBoundedLegacyExceptionFrontendContractTest {

    @Test
    void firstRunOnboardingIsTheOnlyExplicitBoundedLegacyPresentationOwner() throws Exception {
        String html = read("src/main/resources/static/index.html");
        String marker = "data-bounded-legacy-owner=\"first-run-onboarding\"";

        assertTrue(html.contains("id=\"firstRunOnboarding\""));
        assertTrue(html.contains(marker));
        assertTrue(occurrences(html, "data-bounded-legacy-owner=") == 1);
        assertFalse(html.contains("data-bounded-legacy-owner=\"offline-sync\""));
    }

    @Test
    void completionOrderingKeepsModulesProfileAndPwaHandoffAuthoritative() throws Exception {
        String data = read("src/main/resources/static/js/20-data.js");
        String finish = between(data, "async function finishOnboarding", "document.querySelectorAll('[data-onboarding-preset]')");

        assertOrdered(finish,
                "api.updateModules(ensureOnboardingDraft())",
                "setModuleList(list)",
                "await loadMonth()",
                "await refreshModuleAwareData()",
                "onboardingCompleted:true",
                "state.profile = p",
                "hideOnboarding()",
                "DutyLogPwaRuntime?.register?.()");
    }

    @Test
    void vueConsumesOnboardingAuthorityWithoutOwningTheLegacyOverlay() throws Exception {
        String shellStore = read("frontend/src/app/shellStore.ts");
        String productivity = read("frontend/src/features/productivity/components/ProductivityWorkspace.vue");
        String appShell = read("frontend/src/app/AppShell.vue");
        String architecture = read("docs/FRONTEND_ARCHITECTURE.md");

        assertTrue(shellStore.contains("this.onboardingCompleted = snapshot.profile?.onboardingCompleted === true"));
        assertTrue(productivity.contains("modulesLoaded.value && (onboardingCompleted.value || !online.value)"));
        assertFalse(appShell.contains("firstRunOnboarding"));
        assertTrue(architecture.contains("data-bounded-legacy-owner=\"first-run-onboarding\""));
        assertTrue(architecture.contains("no second bounded legacy presentation owner is allowed"));
    }

    private static void assertOrdered(String source, String... needles) {
        int previous = -1;
        for (String needle : needles) {
            int current = source.indexOf(needle);
            assertTrue(current >= 0, "Expected source fragment: " + needle);
            assertTrue(current > previous, "Expected ordering for source fragment: " + needle);
            previous = current;
        }
    }

    private static String between(String source, String start, String end) {
        int from = source.indexOf(start);
        int to = source.indexOf(end, from + start.length());
        if (from < 0 || to < 0 || to <= from) {
            throw new AssertionError("Expected source section not found: " + start + " -> " + end);
        }
        return source.substring(from, to);
    }

    private static int occurrences(String source, String needle) {
        int count = 0;
        int offset = 0;
        while ((offset = source.indexOf(needle, offset)) >= 0) {
            count++;
            offset += needle.length();
        }
        return count;
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
