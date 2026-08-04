package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regression contracts preserving launcher and graph lessons after v27.35.3 promotion. */
class FrontendLockfileExecutableResolutionHotfixTest {

    @Test
    void authenticFrontendLockfileIsNowTheTrackedReleaseSourceOfTruth() throws Exception {
        String ignore = read(".gitignore");
        String bootstrap = read("deploy/scripts/bootstrap-frontend-lockfile.sh");
        assertTrue(ignore.contains("frontend/package-lock.json"));
        assertTrue(bootstrap.contains("rm -f \"$FRONTEND_DIR/package-lock.json\""));
    }

    @Test
    void generatedLockfileMustCarryAuthenticRegistryIntegrityAndGraphMetadata() throws Exception {
        String verifier = read("frontend/scripts/verify-authentic-lockfile.mjs");
        assertTrue(verifier.contains("registryEntries.length < 70"));
        assertTrue(verifier.contains("integrityEntries.length < 70"));
        assertTrue(verifier.contains("graphEntries.length < 20"));
        assertTrue(verifier.contains("node_modules/@vue/language-core"));
    }

    @Test
    void ciAndDockerStillFailWhenLocalExecutablesAreMissing() throws Exception {
        String gate = read("deploy/scripts/frontend-gate.sh");
        String docker = read("Dockerfile");
        assertTrue(gate.contains("for command in vue-tsc vitest vite"));
        assertTrue(gate.contains("local executable is missing after npm ci"));
        assertTrue(docker.contains("test -e node_modules/.bin/vue-tsc"));
        assertTrue(docker.contains("npm ls --all"));
    }

    @Test
    void deliveryAndMaintenanceToolingDoNotFallbackToNpxOrGlobalCompilers() throws Exception {
        String gate = read("deploy/scripts/frontend-gate.sh");
        String bootstrap = read("deploy/scripts/bootstrap-frontend-lockfile.sh");
        String docker = read("Dockerfile");
        assertFalse(gate.contains("npx "));
        assertFalse(bootstrap.contains("npx "));
        assertFalse(docker.contains("npx "));
        assertTrue(bootstrap.contains("npm --prefix \"$FRONTEND_DIR\" install --package-lock-only"));
        assertTrue(gate.contains("npm --prefix \"$FRONTEND_DIR\" ci"));
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
