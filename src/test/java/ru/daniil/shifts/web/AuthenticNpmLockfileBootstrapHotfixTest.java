package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Historical contracts preserving the v27.35.2 bootstrap and v27.35.3 promotion boundary. */
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
    void ordinaryCiNoLongerPublishesABootstrapArtifactAfterPromotion() throws Exception {
        String workflows = read(".github/workflows/ci.yml")
                + read(".github/workflows/deploy-staging.yml")
                + read(".github/workflows/deploy-production.yml");
        assertFalse(workflows.contains("Upload generated authentic frontend lockfile"));
        assertTrue(workflows.contains("cache-dependency-path: frontend/package-lock.json"));
    }

    @Test
    void dockerUsesThePromotedCommittedGraphAndNpmCiBoundary() throws Exception {
        String docker = read("Dockerfile");
        assertTrue(docker.contains("COPY frontend/package.json frontend/package-lock.json"));
        assertFalse(docker.contains("npm install --package-lock-only"));
        assertTrue(docker.contains("node ./scripts/verify-authentic-lockfile.mjs"));
        assertTrue(docker.contains("npm ci --no-audit --no-fund"));
        assertTrue(docker.contains("npm ci --no-audit --no-fund"));
    }

    @Test
    void gateARecordsThePromotedArtifactAndStillRequiresGreenAcceptance() throws Exception {
        String register = read("docs/ENGINEERING_QUALITY_REGISTER.md");
        String release = read("docs/AUTHENTIC_LOCKFILE_COMMIT_GENERATED_CLIENT_FIXTURE_HOTFIX_V27.35.3.md");
        assertTrue(register.contains("Q-01"));
        assertTrue(register.lines().anyMatch(line -> line.startsWith("| Q-01 ") && line.endsWith("| DONE |")));
        assertTrue(release.contains("full green CI and staging"));
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
