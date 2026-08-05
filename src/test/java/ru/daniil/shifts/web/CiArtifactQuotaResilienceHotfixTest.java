package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Static delivery contract for v27.36.3 artifact-quota resilience. */
class CiArtifactQuotaResilienceHotfixTest {

    @Test
    void jacocoUploadIsCompactShortLivedAndNonBlocking() throws Exception {
        String block = stepBlock(read(".github/workflows/ci.yml"), "Upload JaCoCo report");

        assertTrue(block.contains("if: always()"));
        assertTrue(block.contains("continue-on-error: true"));
        assertTrue(block.contains("target/site/jacoco/jacoco.xml"));
        assertTrue(block.contains("target/site/jacoco/jacoco.csv"));
        assertTrue(block.contains("retention-days: 3"));
        assertFalse(block.contains("path: target/site/jacoco\n"));
    }

    @Test
    void ciPlaywrightArtifactIsUploadedOnlyForFailureAndCannotBlockTheJob() throws Exception {
        String block = stepBlock(read(".github/workflows/ci.yml"), "Upload Playwright report");

        assertTrue(block.contains("if: failure()"));
        assertTrue(block.contains("continue-on-error: true"));
        assertTrue(block.contains("if-no-files-found: ignore"));
        assertTrue(block.contains("retention-days: 3"));
        assertFalse(block.contains("if: always()"));
    }

    @Test
    void stagingPlaywrightArtifactUsesTheSameFailureOnlyBoundary() throws Exception {
        String block = stepBlock(read(".github/workflows/deploy-staging.yml"), "Upload Playwright report");

        assertTrue(block.contains("if: failure()"));
        assertTrue(block.contains("continue-on-error: true"));
        assertTrue(block.contains("if-no-files-found: ignore"));
        assertTrue(block.contains("retention-days: 3"));
        assertFalse(block.contains("if: always()"));
    }

    @Test
    void artifactNamesAreUniqueAcrossRunsAndReruns() throws Exception {
        String ci = read(".github/workflows/ci.yml");
        String staging = read(".github/workflows/deploy-staging.yml");

        assertTrue(ci.contains("jacoco-report-${{ github.run_id }}-${{ github.run_attempt }}"));
        assertTrue(ci.contains("playwright-report-${{ github.run_id }}-${{ github.run_attempt }}"));
        assertTrue(staging.contains("staging-playwright-report-${{ github.run_id }}-${{ github.run_attempt }}"));
    }

    @Test
    void reportUploadFailureDoesNotRemoveImageBuildOrMigrationSmoke() throws Exception {
        String ci = read(".github/workflows/ci.yml");
        String staging = read(".github/workflows/deploy-staging.yml");

        assertTrue(ci.indexOf("- name: Build deployment image")
                > ci.indexOf("- name: Upload Playwright report"));
        assertTrue(ci.indexOf("- name: Clean PostgreSQL migration smoke test")
                > ci.indexOf("- name: Build deployment image"));
        assertTrue(staging.contains("- name: Build and push immutable image"));
        assertTrue(staging.contains("- name: Verify the exact image on clean PostgreSQL"));
    }

    private static String stepBlock(String workflow, String stepName) {
        String marker = "      - name: " + stepName;
        int start = workflow.indexOf(marker);
        assertTrue(start >= 0);
        int end = workflow.indexOf("\n      - name:", start + marker.length());
        return end < 0 ? workflow.substring(start) : workflow.substring(start, end);
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
