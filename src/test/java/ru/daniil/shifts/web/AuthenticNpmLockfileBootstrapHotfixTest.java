package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Binding contracts for v27.35.2 authentic npm lockfile bootstrap. */
class AuthenticNpmLockfileBootstrapHotfixTest {

    @Test
    void bootstrapDeletesSyntheticStateAndUsesPinnedNpmToGenerateTheGraph() throws Exception {
        String script = read("deploy/scripts/bootstrap-frontend-lockfile.sh");
        assertTrue(script.contains("rm -rf \"$FRONTEND_DIR/node_modules\""));
        assertTrue(script.contains("rm -f \"$FRONTEND_DIR/package-lock.json\""));
        assertTrue(script.contains("install --package-lock-only --ignore-scripts"));
        assertTrue(script.contains("verify-authentic-lockfile.mjs"));
    }

    @Test
    void ciAlwaysPublishesTheGeneratedLockfileForPromotion() throws Exception {
        String workflows = read(".github/workflows/ci.yml")
                + read(".github/workflows/deploy-staging.yml")
                + read(".github/workflows/deploy-production.yml");
        assertTrue(workflows.contains("Upload generated authentic frontend lockfile"));
        assertTrue(workflows.contains("if: always()"));
        assertTrue(workflows.contains("frontend/generated-lockfile-manifest.txt"));
        assertTrue(workflows.contains("retention-days: 14"));
    }

    @Test
    void dockerUsesTheSameBootstrapThenNpmCiBoundary() throws Exception {
        String docker = read("Dockerfile");
        assertTrue(docker.contains("npm install --package-lock-only --ignore-scripts"));
        assertTrue(docker.contains("node ./scripts/verify-authentic-lockfile.mjs"));
        assertTrue(docker.contains("npm ci --no-audit --no-fund"));
        assertTrue(docker.contains("commit the generated"));
    }

    @Test
    void gateAIsNotClaimedCompleteUntilTheExactArtifactIsCommitted() throws Exception {
        String register = read("docs/ENGINEERING_QUALITY_REGISTER.md");
        String release = read("docs/AUTHENTIC_NPM_LOCKFILE_BOOTSTRAP_HOTFIX_V27.35.2.md");
        assertTrue(register.contains("Q-01"));
        assertTrue(register.contains("| ACTIVE |"));
        assertTrue(release.contains("Gate A remains blocked"));
        assertFalse(release.contains("Gate A is complete"));
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
