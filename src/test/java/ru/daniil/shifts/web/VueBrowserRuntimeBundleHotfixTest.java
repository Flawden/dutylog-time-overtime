package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regression contract for v27.34.2 browser-safe Vue library output. */
class VueBrowserRuntimeBundleHotfixTest {

    @Test
    void productionBundleReplacesNodeEnvironmentAndAuditsBrowserRuntimeGlobals() throws Exception {
        String vite = read("frontend/vite.config.ts");
        String frontendPackage = read("frontend/package.json");
        String audit = read("frontend/scripts/audit-browser-bundle.mjs");
        String dockerfile = read("Dockerfile");
        String fixture = read("e2e/fixtures.js");

        assertTrue(vite.contains("\"process.env.NODE_ENV\": JSON.stringify(\"production\")"));
        assertTrue(frontendPackage.contains("\"audit:bundle\": \"node ./scripts/audit-browser-bundle.mjs\""));
        assertTrue(frontendPackage.contains("vite build && npm run audit:bundle"));
        assertTrue(audit.contains("unreplaced process.env"));
        assertTrue(audit.contains("CommonJS require"));
        assertTrue(audit.contains("CommonJS module.exports"));
        assertTrue(audit.contains("dutylog-vue-app-shell.js"));
        assertTrue(dockerfile.contains("npm run build"));
        assertTrue(fixture.contains("page.on('pageerror'"));
        assertFalse(vite.contains("process: {}"));
        assertFalse(vite.contains("global: {}"));
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
