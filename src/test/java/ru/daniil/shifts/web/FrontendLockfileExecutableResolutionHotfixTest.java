package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regression contracts for v27.35.1 frontend lockfile executable resolution. */
class FrontendLockfileExecutableResolutionHotfixTest {

    @Test
    void lockfileCarriesTheThreeCliBinMappingsUsedByNpmScripts() throws Exception {
        String lock = read("frontend/package-lock.json");
        assertTrue(lock.contains("\"vue-tsc\": \"bin/vue-tsc.js\""));
        assertTrue(lock.contains("\"vitest\": \"vitest.mjs\""));
        assertTrue(lock.contains("\"vite\": \"bin/vite.js\""));
        assertTrue(lock.contains("https://registry.npmjs.org/vue-tsc/-/vue-tsc-2.2.0.tgz"));
    }

    @Test
    void deliveryVerifierRejectsMissingLockMetadataOrInstalledLaunchers() throws Exception {
        String verifier = read("frontend/scripts/verify-delivery-foundation.mjs");
        assertTrue(verifier.contains("executableContracts"));
        assertTrue(verifier.contains("entry.bin?.[command]"));
        assertTrue(verifier.contains("node_modules/.bin/${command}"));
        assertTrue(verifier.contains("npm ci did not create the local"));
    }

    @Test
    void ciAndDockerFailBeforeTypecheckWhenLocalExecutablesAreMissing() throws Exception {
        String gate = read("deploy/scripts/frontend-gate.sh");
        String docker = read("Dockerfile");
        assertTrue(gate.contains("for command in vue-tsc vitest vite"));
        assertTrue(gate.contains("local executable is missing after npm ci"));
        assertTrue(docker.contains("test -e node_modules/.bin/vue-tsc"));
        assertTrue(docker.contains("npm ls --all"));
    }

    @Test
    void hotfixDoesNotFallbackToNpxOrMutableInstall() throws Exception {
        String gate = read("deploy/scripts/frontend-gate.sh");
        String docker = read("Dockerfile");
        assertFalse(gate.contains("npx "));
        assertFalse(docker.contains("npx "));
        assertFalse(gate.contains("npm install"));
        assertFalse(docker.contains("npm install"));
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
