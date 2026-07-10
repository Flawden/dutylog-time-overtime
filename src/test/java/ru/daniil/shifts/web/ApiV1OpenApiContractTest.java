package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiV1OpenApiContractTest {
    @Test
    void openApiContainsFrozenAndroidEndpointsAndErrorSchema() throws Exception {
        try (var stream = getClass().getResourceAsStream("/static/openapi/dutylog-v1.yaml")) {
            assertNotNull(stream, "OpenAPI v1 file must be packaged");
            String yaml = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(yaml.contains("/api/v1/mobile/auth/register:"));
            assertTrue(yaml.contains("/api/v1/mobile/bootstrap:"));
            assertTrue(yaml.contains("/api/v1/mobile/sync:"));
            assertTrue(yaml.contains("MobileSyncItemResult:"));
            assertTrue(yaml.contains("ApiError:"));
            assertTrue(yaml.contains("ALREADY_APPLIED"));
            assertTrue(yaml.contains("VERSION_CONFLICT"));
        }
    }
}
