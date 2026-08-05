package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Static architecture contract for v27.36.2 Vue App Shell & Design System continuation. */
class VueFrontendFoundationContractTest {

    @Test
    void frontendPinsVueTypeScriptVitePiniaRouterAndVitestWithStrictTypeChecking() throws Exception {
        String packageJson = read("frontend/package.json");
        String tsconfig = read("frontend/tsconfig.json");
        String vite = read("frontend/vite.config.ts");

        assertTrue(packageJson.contains("\"vue\": \"3.5.13\""));
        assertTrue(packageJson.contains("\"pinia\": \"2.3.0\""));
        assertTrue(packageJson.contains("\"vue-router\": \"4.5.0\""));
        assertTrue(packageJson.contains("\"vite\": \"5.4.14\""));
        assertTrue(packageJson.contains("\"vitest\": \"2.1.8\""));
        assertTrue(packageJson.contains("\"vue-tsc\": \"2.2.0\""));
        assertTrue(tsconfig.contains("\"strict\": true"));
        assertTrue(tsconfig.contains("\"noUncheckedIndexedAccess\": true"));
        assertTrue(vite.contains("dutylog-vue-app-shell.js"));
        assertTrue(vite.contains("dutylog-vue-app-shell.css"));
    }

    @Test
    void vueUsesMemoryHistoryAndAnExplicitLegacyCapabilityBridge() throws Exception {
        String router = read("frontend/src/platform/router/index.ts");
        String bridge = read("frontend/src/platform/bridge/legacyBridge.ts");
        String legacy = read("src/main/resources/static/js/10-core.js");
        String frontendSources = readTree("frontend/src");

        assertTrue(router.contains("createMemoryHistory"));
        assertFalse(router.contains("createWebHashHistory"));
        assertTrue(bridge.contains("target.DutyLogLegacyPlatform"));
        assertTrue(bridge.contains("dutylog:legacy-command"));
        assertTrue(legacy.contains("window.DutyLogLegacyPlatform = Object.freeze"));
        assertTrue(legacy.contains("snapshot:legacyPlatformSnapshot"));
        assertFalse(frontendSources.contains("document.querySelector("));
        assertFalse(frontendSources.contains("window.state"));
    }

    @Test
    void mavenAndDockerPackageVueIntoTheExistingSingleApplicationImage() throws Exception {
        String pom = read("pom.xml");
        String dockerfile = read("Dockerfile");
        String dockerIgnore = read(".dockerignore");

        assertTrue(pom.contains("<directory>frontend/dist</directory>"));
        assertTrue(pom.contains("<targetPath>static/vue</targetPath>"));
        assertTrue(dockerfile.contains("FROM node:20.18.1-alpine3.20 AS frontend-build"));
        assertTrue(dockerfile.contains("COPY --from=frontend-build /frontend/dist ./frontend/dist"));
        assertTrue(dockerfile.contains("FROM maven:3.9.9-eclipse-temurin-17 AS backend-build"));
        assertTrue(dockerfile.contains("COPY --from=backend-build --chown=dutylog:dutylog /app/target/dutylog-*.jar /app/dutylog.jar"));
        assertFalse(dockerfile.contains("nginx:alpine"));
        assertFalse(dockerfile.contains("EXPOSE 5173"));
        assertTrue(dockerIgnore.contains("/package.json"));
        assertFalse(dockerIgnore.lines().anyMatch("package.json"::equals));
    }

    @Test
    void ciStagingAndProductionValidateFrontendBeforeMaven() throws Exception {
        for (String workflow : new String[]{
                ".github/workflows/ci.yml",
                ".github/workflows/deploy-staging.yml",
                ".github/workflows/deploy-production.yml"
        }) {
            String yaml = read(workflow);
            int frontend = yaml.indexOf("bash ./deploy/scripts/frontend-gate.sh");
            int maven = Math.max(yaml.indexOf("mvn -B --no-transfer-progress verify"),
                    yaml.indexOf("mvn -B --no-transfer-progress test"));
            assertTrue(frontend >= 0, workflow + " must run the frontend gate");
            assertTrue(maven > frontend, workflow + " must build Vue before Maven packages resources");
        }
    }

    @Test
    void browserBootPublishesAwaitableImmutableVueDiagnosticsWithoutReplacingLegacyShell() throws Exception {
        String html = read("src/main/resources/static/index.html");
        String bootstrap = read("src/main/resources/static/js/shell-bootstrap.js");
        String main = read("frontend/src/main.ts");
        String spec = read("e2e/vue-frontend-foundation.spec.js");

        assertTrue(html.contains("id=\"dutylog-vue-root\""));
        assertTrue(html.contains("type=\"module\" src=\"/vue/dutylog-vue-app-shell.js?v=27.36.2\""));
        assertTrue(bootstrap.contains("window.__dutylogVueReady = new Promise"));
        assertTrue(main.contains("window.DutyLogVuePlatform = platform"));
        assertTrue(main.contains("host.dataset.vueReady = \"true\""));
        assertTrue(main.contains("Object.freeze"));
        assertTrue(spec.contains("window.__dutylogVueReady"));
        assertTrue(spec.contains("data-vue-ready"));
        assertTrue(html.contains("js/70-user-boot.js?v=27.36.2"));
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }

    private static String readTree(String root) throws Exception {
        try (var files = Files.walk(Path.of(root))) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".ts") || path.toString().endsWith(".vue"))
                    .sorted()
                    .map(path -> {
                        try {
                            return Files.readString(path, StandardCharsets.UTF_8);
                        } catch (Exception ex) {
                            throw new IllegalStateException(ex);
                        }
                    })
                    .collect(Collectors.joining("\n"));
        }
    }
}
