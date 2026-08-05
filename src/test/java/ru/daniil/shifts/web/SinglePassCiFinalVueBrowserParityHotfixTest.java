package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Static delivery and browser-parity contract for v27.36.6. */
class SinglePassCiFinalVueBrowserParityHotfixTest {

    @Test
    void ledgerPeriodButtonsExposeExplicitAriaBooleanTokens() throws IOException {
        String page = read("frontend/src/features/absence-time-bank/components/TimeBankPage.vue");

        assertTrue(page.contains(":aria-pressed=\"rangeMode === 'month' ? 'true' : 'false'\""));
        assertTrue(page.contains(":aria-pressed=\"rangeMode === 'year' ? 'true' : 'false'\""));
        assertFalse(page.contains(":aria-pressed=\"rangeMode === 'month'\""));
        assertFalse(page.contains(":aria-pressed=\"rangeMode === 'year'\""));
    }

    @Test
    void ledgerPeriodOwnershipChangesBeforeTheNetworkRefreshCompletes() throws IOException {
        String store = compact(read("frontend/src/features/absence-time-bank/stores/absenceTimeBankStore.ts"));
        String block = actionBlock(store, "async setRangeMode(mode: LedgerRangeMode): Promise<void>", "openGuide(): void");

        assertTrue(block.indexOf("this.rangeMode = mode;") < block.indexOf("await this.refresh(todayIso(), mode);"));
    }

    @Test
    void composerRefreshesTheRequestedDateBeforeReadingBalanceAndTypes() throws IOException {
        String store = compact(read("frontend/src/features/absence-time-bank/stores/absenceTimeBankStore.ts"));
        String block = actionBlock(store, "async openAbsenceComposer(options: AbsenceComposerOpenOptions = {}): Promise<void>", "async openAbsenceEditor(id: number): Promise<void>");

        assertTrue(block.indexOf("const date = options.date || todayIso();")
                < block.indexOf("await this.refresh(date, this.rangeMode);"));
        assertTrue(block.indexOf("await this.refresh(date, this.rangeMode);")
                < block.indexOf("const types = this.planner?.types ?? [];"));
        assertFalse(block.contains("await this.ensureLoaded();"));
    }

    @Test
    void vitestLocksOptimisticPeriodAndFreshComposerAccountBehavior() throws IOException {
        String spec = read("frontend/src/features/absence-time-bank/stores/absenceTimeBankStore.spec.ts");

        assertTrue(spec.contains("switches the ledger period immediately while the refreshed model is loading"));
        assertTrue(spec.contains("refreshes a previously loaded account before opening the absence composer"));
        assertTrue(spec.contains("expect(load).toHaveBeenNthCalledWith(2, \"2026-08-06\", \"year\")"));
        assertTrue(spec.contains("expect(store.account?.balanceHours).toBe(8)"));
    }

    @Test
    void ordinaryCiSkipsOnlyPushesToTheStagingBranch() throws IOException {
        String ci = compact(read(".github/workflows/ci.yml"));

        assertTrue(ci.contains("on: push: branches-ignore: [test] tags: ['**'] pull_request:"));
        assertFalse(ci.contains("branches-ignore: [main]"));
        assertFalse(ci.contains("branches-ignore: [master]"));
    }

    @Test
    void stagingWorkflowRemainsTheOnlyPushOwnerForTest() throws IOException {
        String staging = compact(read(".github/workflows/deploy-staging.yml"));

        assertTrue(staging.contains("on: push: branches: [test] workflow_dispatch:"));
        assertTrue(staging.contains("group: dutylog-staging"));
    }

    @Test
    void stagingValidationStillRunsEveryBlockingQualityGate() throws IOException {
        String staging = read(".github/workflows/deploy-staging.yml");

        assertTrue(staging.contains("bash ./deploy/scripts/frontend-gate.sh"));
        assertTrue(staging.contains("mvn -B --no-transfer-progress verify"));
        assertTrue(staging.contains("bash ./deploy/scripts/release-check.sh"));
        assertTrue(staging.contains("npm run test:e2e"));
        assertTrue(staging.contains("Build and push immutable image"));
        assertTrue(staging.contains("Verify the exact image on clean PostgreSQL"));
    }

    @Test
    void nonStagingCiRetainsItsCompleteIndependentValidationPath() throws IOException {
        String ci = read(".github/workflows/ci.yml");

        assertTrue(ci.contains("bash ./deploy/scripts/frontend-gate.sh"));
        assertTrue(ci.contains("mvn -B --no-transfer-progress verify"));
        assertTrue(ci.contains("bash ./deploy/scripts/release-check.sh"));
        assertTrue(ci.contains("npm run test:e2e"));
        assertTrue(ci.contains("Build deployment image"));
        assertTrue(ci.contains("Clean PostgreSQL migration smoke test"));
    }

    private static String actionBlock(String source, String startMarker, String endMarker) {
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker, start + startMarker.length());
        assertTrue(start >= 0 && end > start);
        return source.substring(start, end);
    }

    private static String compact(String value) {
        return value.replaceAll("\\s+", " ").trim();
    }

    private static String read(String path) throws IOException {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
