package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Binding contracts for v27.35.3 authentic graph promotion and generated-client fixture repair. */
class AuthenticLockfileCommitGeneratedClientFixtureHotfixTest {

    @Test
    void authenticGraphAndProvenanceAreCommittedReleaseInputs() throws Exception {
        String ignore = read(".gitignore");
        String lock = read("frontend/package-lock.json");
        String manifest = read("frontend/generated-lockfile-manifest.txt");
        assertFalse(ignore.contains("frontend/package-lock.json"));
        assertTrue(lock.contains("https://registry.npmjs.org/"));
        assertTrue(lock.contains("sha512-"));
        assertTrue(manifest.contains("sourceArtifactSha256=3f6c590948a62c506c2191c9b279f15712f5182148c5f3131e96d5a56bd54060"));
        assertTrue(manifest.contains("source=github-actions-run-30906521813"));
    }

    @Test
    void normalDeliveryUsesCommittedNpmCiWithoutGraphRegeneration() throws Exception {
        String gate = read("deploy/scripts/frontend-gate.sh");
        String docker = read("Dockerfile");
        String workflows = read(".github/workflows/ci.yml")
                + read(".github/workflows/deploy-staging.yml")
                + read(".github/workflows/deploy-production.yml");
        assertTrue(gate.contains("npm --prefix "$FRONTEND_DIR" ci"));
        assertFalse(gate.contains("bootstrap-frontend-lockfile.sh"));
        assertTrue(docker.contains("COPY frontend/package.json frontend/package-lock.json"));
        assertFalse(docker.contains("npm install --package-lock-only"));
        assertTrue(workflows.contains("cache-dependency-path: frontend/package-lock.json"));
        assertFalse(workflows.contains("Upload generated authentic frontend lockfile"));
    }

    @Test
    void generatedClientFixtureReturnsAFreshResponsePerRequest() throws Exception {
        String spec = read("frontend/src/platform/api/generatedClient.spec.ts");
        assertTrue(spec.contains("mockImplementation(async () =>"));
        assertTrue(spec.contains("two sequential operations with independent response bodies"));
        assertFalse(spec.contains("mockResolvedValue(jsonResponse"));
    }

    @Test
    void gateAImplementationIsCompleteButRequiresGreenAcceptance() throws Exception {
        String register = read("docs/ENGINEERING_QUALITY_REGISTER.md");
        String q01 = register.lines().filter(line -> line.startsWith("| Q-01 ")).findFirst().orElseThrow();
        assertTrue(q01.endsWith("| DONE |"), q01);
        assertTrue(register.contains("полного зелёного CI/staging `v27.35.3`"));
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
