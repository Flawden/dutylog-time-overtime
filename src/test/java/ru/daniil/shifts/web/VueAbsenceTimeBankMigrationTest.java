package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Binding contracts for the first full Vue domain migration in v27.36.0. */
class VueAbsenceTimeBankMigrationTest {

    private static final Path FEATURE = Path.of("frontend/src/features/absence-time-bank");

    @Test
    void manifestClosesParityAndKeepsSpringBootAsTheBusinessOwner() throws Exception {
        String manifest = read("docs/migration/absence-time-bank-vue-migration-manifest.md");

        assertTrue(manifest.contains("status: complete"));
        assertTrue(manifest.contains("target_release: \"v27.36.0\""));
        assertTrue(manifest.contains("Final UI owner after this release: Vue"));
        assertTrue(manifest.contains("Business rules that remain owned by Spring Boot"));
        assertTrue(manifest.contains("Q-06 stale/double submit"));
        assertFalse(manifest.contains("| planned |"));
    }

    @Test
    void appShellInstallsOneVueOwnerForBothRoutesAndRetiresLegacyRoots() throws Exception {
        String shell = read("frontend/src/app/AppShell.vue");
        String workspace = read(FEATURE.resolve("components/AbsenceTimeBankWorkspace.vue"));
        String main = read("frontend/src/main.ts");
        String bridge = read("frontend/src/platform/bridge/legacyBridge.ts");
        String legacyPlatform = read("src/main/resources/static/js/10-core.js");

        assertTrue(shell.contains("AbsenceTimeBankWorkspace"));
        assertTrue(main.contains("bridge.retireDomainOwners(\"absence-time-bank\")"));
        assertTrue(main.indexOf("bridge.retireDomainOwners") > main.indexOf("await nextTick()"));
        assertTrue(bridge.contains("retireDomainOwners(domain"));
        assertTrue(legacyPlatform.contains("view-vacation"));
        assertTrue(legacyPlatform.contains("view-overtime"));
        assertTrue(legacyPlatform.contains("data-vue-absence-time-bank"));
        assertTrue(workspace.contains("openTimeBankUsage"));
        assertFalse(workspace.contains("document.querySelector"));
    }

    @Test
    void featureUsesOnlyTheGeneratedClientAndNeverReimplementsTransport() throws Exception {
        String api = read(FEATURE.resolve("api/absenceTimeBankApi.ts"));
        String allSources = featureSources();

        assertTrue(api.contains("createGeneratedDutyLogApiClient"));
        assertTrue(api.contains("client.request(\"getVacationPlanner\""));
        assertTrue(api.contains("client.request(\"overtimeAccount\""));
        assertTrue(api.contains("client.request(\"inspectLedgerIntegrity\""));
        assertTrue(api.contains("client.request(\"createQuickScenario\""));
        assertFalse(allSources.contains("jfetch("));
        assertFalse(allSources.contains("window.state"));
        assertFalse(allSources.contains("fetch("));
    }

    @Test
    void openApiAndGeneratedContractCoverAllNewTypedDomainOperations() throws Exception {
        String openApi = read("src/main/resources/static/openapi/dutylog-v1.yaml");
        String generated = read("frontend/src/generated/dutylog-api.ts");

        for (String operation : List.of(
                "createQuickScenario", "updateQuickScenario", "deleteQuickScenario",
                "createOvertimeCredit", "updateOvertimeCredit", "deleteOvertimeCredit",
                "createAbsencePeriod", "updateAbsencePeriod", "deleteAbsencePeriod")) {
            assertTrue(openApi.contains("operationId: " + operation), operation);
            assertTrue(generated.contains("\"" + operation + "\""), operation);
        }
        assertTrue(generated.contains("GENERATED FILE — DO NOT EDIT."));
        assertTrue(generated.contains("DUTYLOG_OPENAPI_SOURCE_SHA256"));
        assertTrue(generated.contains("credits: Array<DutyLogApiSchemas.OvertimeCredit>"));
        assertTrue(generated.contains("allocations: Array<DutyLogApiSchemas.OvertimeAllocation>"));
        assertTrue(generated.contains("items: Array<DutyLogApiSchemas.AbsencePreviewItem>"));
        assertFalse(generated.contains("export type OvertimeAccount = DutyLogApiSchemas.OvertimeCredit"));
    }

    @Test
    void storeBlocksDuplicateMutationsAndRejectsStaleRefreshResults() throws Exception {
        String store = read(FEATURE.resolve("stores/absenceTimeBankStore.ts"));

        assertTrue(store.contains("let readSequence = 0"));
        assertTrue(store.contains("const sequence = ++readSequence"));
        assertTrue(store.contains("if (sequence !== readSequence) return"));
        assertFalse(store.contains("refreshSequence"));
        assertTrue(store.contains("if (this.mutationPending) return"));
        assertTrue(store.contains("error.status === 409"));
        assertTrue(store.contains("await this.refresh()"));
        assertTrue(store.contains("previewController === controller"));
        assertTrue(store.contains("creditPreviewController === controller"));
    }

    @Test
    void parityIncludesComposerJournalResponsiveLedgerIntegrityAndFifo() throws Exception {
        String absence = read(FEATURE.resolve("components/AbsencePage.vue"));
        String composer = read(FEATURE.resolve("components/AbsenceComposer.vue"));
        String bank = read(FEATURE.resolve("components/TimeBankPage.vue"));
        String credit = read(FEATURE.resolve("components/CreditEditor.vue"));

        assertTrue(absence.contains("id=\"vacationPeriodList\""));
        assertTrue(absence.contains("data-bank-absence"));
        assertTrue(composer.contains("id=\"absenceFifoForecast\""));
        assertTrue(composer.contains("id=\"vacationPreview\""));
        assertTrue(bank.contains("id=\"ledgerChart\""));
        assertTrue(bank.contains("id=\"ledgerCards\""));
        assertTrue(bank.contains("id=\"ledgerIntegrityCard\""));
        assertTrue(bank.contains("id=\"actualWorkList\""));
        assertTrue(bank.contains("id=\"fifoForecastForm\""));
        assertTrue(credit.contains("id=\"scenarioManagerView\""));
    }

    @Test
    void legacyEntryPointsAreNamedCrossDomainAdaptersRatherThanRuntimeOwners() throws Exception {
        String absenceLegacy = read("src/main/resources/static/js/39-vacation-planner.js");
        String overtimeLegacy = read("src/main/resources/static/js/40-overtime.js");

        assertTrue(absenceLegacy.contains("vueDomain.openAbsenceComposer"));
        assertTrue(absenceLegacy.contains("vueDomain.openAbsenceEditor"));
        assertTrue(absenceLegacy.contains("vueDomain.openTimeBankUsage"));
        assertTrue(overtimeLegacy.contains("vueDomain.openCreditEditor"));
        assertTrue(overtimeLegacy.contains("if (vueDomain) return vueDomain.refresh()"));
    }

    @Test
    void migrationHasUnitRuntimeAndAccessibilityRegressionCoverage() throws Exception {
        String modelTests = read(FEATURE.resolve("types/model.spec.ts"));
        String storeTests = read(FEATURE.resolve("stores/absenceTimeBankStore.spec.ts"));
        String e2e = read("e2e/vue-absence-time-bank-migration.spec.js");
        String bank = read(FEATURE.resolve("components/TimeBankPage.vue"));

        assertTrue(modelTests.contains("forecasts FIFO"));
        String model = read(FEATURE.resolve("types/model.ts"));
        assertFalse(model.contains("allocations,\n    allocations,"));
        assertTrue(model.contains("projection?.sourceRemainingHours"));
        assertTrue(model.contains("projection?.sourceCreditHours"));
        assertTrue(storeTests.contains("stale refresh"));
        assertTrue(storeTests.contains("blocks a second submit"));
        assertTrue(storeTests.contains("newest request"));
        assertTrue(e2e.contains("button.click();\n    button.click();"));
        assertTrue(e2e.contains("#view-vacation"));
        assertTrue(bank.contains("role=\"tablist\""));
        assertTrue(bank.contains("aria-live=\"polite\""));
    }

    private static String featureSources() throws Exception {
        var result = new StringBuilder();
        try (var paths = Files.walk(FEATURE)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                result.append(Files.readString(path, StandardCharsets.UTF_8)).append('\n');
            }
        }
        return result.toString();
    }

    private static String read(String path) throws Exception {
        return read(Path.of(path));
    }

    private static String read(Path path) throws Exception {
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
