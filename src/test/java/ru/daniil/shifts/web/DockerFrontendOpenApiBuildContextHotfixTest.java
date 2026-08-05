package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Static delivery contract for v27.36.1 Docker frontend OpenAPI build context. */
class DockerFrontendOpenApiBuildContextHotfixTest {

    private static final String OPENAPI_SOURCE =
            "src/main/resources/static/openapi/dutylog-v1.yaml";
    private static final String OPENAPI_TARGET =
            "/src/main/resources/static/openapi/dutylog-v1.yaml";

    @Test
    void frontendDockerStageCopiesTheCanonicalBackendOpenApiDocument() throws Exception {
        String docker = read("Dockerfile");

        assertTrue(docker.contains("COPY " + OPENAPI_SOURCE + " \\\n"
                + "     " + OPENAPI_TARGET));
    }

    @Test
    void canonicalOpenApiCopyPrecedesTheFrontendContractCheckAndBuild() throws Exception {
        String docker = read("Dockerfile");
        int sourceCopy = docker.indexOf("COPY " + OPENAPI_SOURCE);
        int build = docker.indexOf("RUN node ./scripts/verify-authentic-lockfile.mjs", sourceCopy);
        int npmBuild = docker.indexOf("&& npm run build", build);

        assertTrue(sourceCopy >= 0, "canonical OpenAPI source must enter the frontend stage");
        assertTrue(build > sourceCopy, "frontend validation must run after the OpenAPI copy");
        assertTrue(npmBuild > build, "npm build must keep the OpenAPI drift gate active");
    }

    @Test
    void generatorStillReadsTheBackendSourceOfTruthRatherThanAFrontendDuplicate() throws Exception {
        String generator = read("frontend/scripts/generate-openapi-contract.mjs");
        String packageJson = read("frontend/package.json");

        assertTrue(generator.contains(OPENAPI_SOURCE));
        assertTrue(packageJson.contains("node ./scripts/generate-openapi-contract.mjs --check"));
        assertFalse(Files.exists(Path.of("frontend", "dutylog-v1.yaml")));
    }

    @Test
    void hotfixKeepsTheSingleApplicationImageTopology() throws Exception {
        String docker = read("Dockerfile");

        assertTrue(docker.contains("COPY --from=frontend-build /frontend/dist ./frontend/dist"));
        assertTrue(docker.contains("COPY --from=backend-build --chown=dutylog:dutylog "
                + "/app/target/dutylog-*.jar /app/dutylog.jar"));
        assertFalse(docker.contains("nginx:alpine"));
        assertFalse(docker.contains("EXPOSE 5173"));
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
