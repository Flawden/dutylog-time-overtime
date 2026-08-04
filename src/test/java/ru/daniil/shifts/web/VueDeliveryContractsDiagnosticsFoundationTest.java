package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Binding static contracts for v27.35.0 Gate A. */
class VueDeliveryContractsDiagnosticsFoundationTest {

    @Test
    void frontendDeliveryPinsExactToolchainAndCommittedAuthenticLockfile() throws Exception {
        String pkg = read("frontend/package.json");
        String npmrc = read("frontend/.npmrc");
        String lock = read("frontend/package-lock.json");
        assertTrue(pkg.contains("\"packageManager\": \"npm@10.8.2\""));
        assertTrue(pkg.contains("\"node\": \"20.18.1\""));
        assertTrue(npmrc.contains("package-lock=true"));
        assertTrue(npmrc.contains("engine-strict=true"));
        assertTrue(lock.contains("https://registry.npmjs.org/"));
        assertFalse(read(".gitignore").contains("frontend/package-lock.json"));
    }

    @Test
    void ciDockerAndLocalGateUseTheCommittedAuthenticGraphWithNpmCi() throws Exception {
        String gate = read("deploy/scripts/frontend-gate.sh");
        String docker = read("Dockerfile");
        String workflows = read(".github/workflows/ci.yml")
                + read(".github/workflows/deploy-staging.yml")
                + read(".github/workflows/deploy-production.yml");
        assertFalse(gate.contains("bootstrap-frontend-lockfile.sh"));
        assertTrue(gate.contains("npm --prefix \"$FRONTEND_DIR\" ci"));
        assertTrue(docker.contains("COPY frontend/package.json frontend/package-lock.json"));
        assertFalse(docker.contains("npm install --package-lock-only"));
        assertTrue(docker.contains("npm ci --no-audit --no-fund"));
        assertTrue(docker.contains("node:20.18.1-alpine3.20"));
        assertTrue(workflows.contains("node-version-file: 'frontend/.node-version'"));
        assertTrue(workflows.contains("cache-dependency-path: frontend/package-lock.json"));
        assertFalse(workflows.contains("Upload generated authentic frontend lockfile"));
        assertFalse(gate.contains("package-lock=false"));
        assertFalse(docker.contains("npx "));
    }

    @Test
    void generatedContractCarriesCanonicalOpenApiHashAndDriftGate() throws Exception {
        byte[] yaml = Files.readAllBytes(Path.of("src/main/resources/static/openapi/dutylog-v1.yaml"));
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(yaml));
        String generated = read("frontend/src/generated/dutylog-api.ts");
        String generator = read("frontend/scripts/generate-openapi-contract.mjs");
        String pkg = read("frontend/package.json");
        assertTrue(generated.contains("SHA-256: " + hash));
        assertTrue(generated.contains("export const dutyLogOperations"));
        assertTrue(generated.contains("response: Array<DutyLogApiSchemas.DayNote>"));
        assertTrue(generated.contains("done: boolean;"));
        assertTrue(generator.contains("--check"));
        assertTrue(pkg.contains("\"contract:check\""));
    }

    @Test
    void generatedClientUsesOperationIdAndSharedTransport() throws Exception {
        String client = read("frontend/src/platform/api/generatedClient.ts");
        assertTrue(client.contains("DutyLogOperationId"));
        assertTrue(client.contains("dutyLogOperations[operationId]"));
        assertTrue(client.contains("createDutyLogHttpClient"));
        assertFalse(client.contains("window.state"));
    }

    @Test
    void diagnosticsExposeRecoveryWithoutSuppressingStrictBrowserFailures() throws Exception {
        String diagnostics = read("frontend/src/platform/diagnostics/frontendDiagnostics.ts");
        String boundary = read("frontend/src/shared/errors/AppErrorBoundary.vue");
        String client = read("frontend/src/platform/api/httpClient.ts");
        assertTrue(diagnostics.contains("unhandledrejection"));
        assertTrue(diagnostics.contains("do not call preventDefault"));
        assertTrue(boundary.contains("data-vue-recovery-ui"));
        assertTrue(boundary.contains("data-vue-recovery-request-id"));
        assertTrue(client.contains("X-Request-Id"));
        assertTrue(client.contains("recordRequestDiagnostics"));
    }

    @Test
    void migrationManifestTemplateContainsBindingParitySections() throws Exception {
        String template = read("docs/migration/_template.md");
        for (String section : new String[]{
                "Domain owner", "Legacy entry points", "User journeys", "API endpoints",
                "Server invariants", "Offline/PWA behavior", "Accessibility requirements",
                "Existing tests", "Vue target modules", "Temporary bridge capabilities",
                "Legacy files to delete", "Rollback expectations", "Known non-goals", "Parity matrix"}) {
            assertTrue(template.contains(section), section);
        }
    }

    @Test
    void adrFoundationRecordsTheFiveGateADecisions() throws Exception {
        String index = read("docs/architecture/adr/INDEX.md");
        for (int number = 1; number <= 5; number++) {
            assertTrue(index.contains("ADR-00" + number));
        }
        assertTrue(read("docs/architecture/adr/ADR-005-openapi-generated-frontend-contract.md")
                .contains("canonical contract"));
    }

    @Test
    void engineeringQualityRegisterMarksGateAWorkDonePendingGreenAcceptance() throws Exception {
        String register = read("docs/ENGINEERING_QUALITY_REGISTER.md");
        String q01 = register.lines().filter(line -> line.startsWith("| Q-01 ")).findFirst().orElseThrow();
        assertTrue(q01.endsWith("| DONE |"), q01);
        for (int number = 2; number <= 5; number++) {
            String rowPrefix = "| Q-0" + number + " ";
            String row = register.lines().filter(line -> line.startsWith(rowPrefix)).findFirst().orElseThrow();
            assertTrue(row.endsWith("| DONE |"), row);
        }
        assertTrue(register.contains("полного зелёного CI/staging `v27.35.7`"));
    }

    @Test
    void releaseKeepsBackendAndDeploymentTopologyUnchanged() throws Exception {
        try (var migrations = Files.walk(Path.of("src/main/resources/db/migration"))) {
            assertEquals(47, migrations
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().matches("V\\d+__.*\\.sql"))
                    .count());
        }
        String release = read("docs/VUE_DELIVERY_CONTRACTS_DIAGNOSTICS_FOUNDATION_V27.35.0.md");
        assertTrue(release.contains("No product domain moves to Vue"));
        assertTrue(release.contains("one-image topology remain unchanged"));
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
