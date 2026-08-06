package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Compile-gated alignment contract for v27.37.0. */
class VueReadSequencingStaticContractAlignmentHotfixTest {

    @Test
    void productionStoreUsesOneSharedReadSequenceForFullAndPeriodLoads() throws IOException {
        String store = read("frontend/src/features/absence-time-bank/stores/absenceTimeBankStore.ts");

        assertTrue(store.contains("let readSequence = 0;"));
        assertTrue(count(store, "const sequence = ++readSequence;") >= 2);
        assertTrue(store.contains("if (sequence !== readSequence) return;"));
        assertTrue(store.contains("if (sequence !== readSequence || this.rangeMode !== mode) return;"));
        assertFalse(store.contains("refreshSequence"));
    }

    @Test
    void migrationContractTracksTheAcceptedReadSequenceName() throws IOException {
        String contract = read("src/test/java/ru/daniil/shifts/web/VueAbsenceTimeBankMigrationTest.java");

        assertTrue(contract.contains("let readSequence = 0"));
        assertTrue(contract.contains("const sequence = ++readSequence"));
        assertTrue(contract.contains("if (sequence !== readSequence) return"));
        assertFalse(contract.contains("let refreshSequence = 0"));
    }

    @Test
    void browserProjectionContractWaitsForTheWinningFullRead() throws IOException {
        String contract = read("src/test/java/ru/daniil/shifts/web/VueAbsenceTimeBankBrowserParityHotfixTest.java");

        assertTrue(contract.contains("if (sequence !== readSequence) return;"));
        assertTrue(contract.contains("publishAbsenceTimeBankProjection"));
        assertFalse(contract.contains("sequence !== refreshSequence"));
    }

    @Test
    void singlePassContractUsesTheCanonicalSnapshotVitestName() throws IOException {
        String contract = read("src/test/java/ru/daniil/shifts/web/SinglePassCiFinalVueBrowserParityHotfixTest.java");
        String spec = read("frontend/src/features/absence-time-bank/stores/absenceTimeBankStore.spec.ts");
        String scenario = "switches the ledger period immediately without replacing the canonical account snapshot";

        assertTrue(contract.contains(scenario));
        assertTrue(spec.contains(scenario));
        assertFalse(contract.contains("while the refreshed model is loading"));
    }

    @Test
    void alignmentChangesOnlyTestsAndReleaseIdentityNotRuntimeSemantics() throws IOException {
        String store = read("frontend/src/features/absence-time-bank/stores/absenceTimeBankStore.ts");
        String api = read("frontend/src/features/absence-time-bank/api/absenceTimeBankApi.ts");

        assertTrue(store.contains("const result = await api.loadPeriod(mode);"));
        assertTrue(store.contains("if (!this.loaded)"));
        assertTrue(store.contains("await this.refresh(todayIso(), mode);"));
        assertTrue(api.contains("async function loadPeriod(rangeMode: LedgerRangeMode = \"month\")"));
    }

    @Test
    void strictChromiumUsageDateExpectationRemainsUntouched() throws IOException {
        String spec = read("e2e/overtime-next.spec.js");

        assertTrue(spec.contains("usageDate:`${prefix}-03`"));
        assertTrue(spec.contains("data-series-key=\"${usageDate}\""));
        assertTrue(spec.contains("toHaveAttribute('title', /−4/)"));
    }

    private static int count(String source, String token) {
        int occurrences = 0;
        int index = 0;
        while ((index = source.indexOf(token, index)) >= 0) {
            occurrences++;
            index += token.length();
        }
        return occurrences;
    }

    private static String read(String path) throws IOException {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
