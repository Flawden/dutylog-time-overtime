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
            assertTrue(yaml.contains("/api/v1/schedule-templates:"));
            assertTrue(yaml.contains("/api/v1/schedule-templates/{id}/preview:"));
            assertTrue(yaml.contains("/api/v1/calendar-layers:"));
            assertTrue(yaml.contains("ScheduleTemplatePreview:"));
            assertTrue(yaml.contains("CalendarLayerEntry:"));
            assertTrue(yaml.contains("clearEndDate: { type: boolean, default: false }"));
        }
    }
    @Test
    void openApiDocumentsImmutableShiftOccurrencesAndLegacyMigration() throws Exception {
        try (var stream = getClass().getResourceAsStream("/static/openapi/dutylog-v1.yaml")) {
            assertNotNull(stream, "OpenAPI v1 file must be packaged");
            String yaml = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(yaml.contains("/api/v1/shifts/legacy-migration/preview:"));
            assertTrue(yaml.contains("/api/v1/shifts/legacy-migration:"));
            assertTrue(yaml.contains("ShiftOccurrence:"));
            assertTrue(yaml.contains("LegacyShiftMigrationRequest:"));
            assertTrue(yaml.contains("legacyLocal:"));
        }
    }

    @Test
    void openApiDocumentsPrivateIcsSubscriptionAndReadOnlyExports() throws Exception {
        try (var stream = getClass().getResourceAsStream("/static/openapi/dutylog-v1.yaml")) {
            assertNotNull(stream, "OpenAPI v1 file must be packaged");
            String yaml = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(yaml.contains("/api/v1/calendar-sync/status:"));
            assertTrue(yaml.contains("/api/v1/calendar-sync/subscription:"));
            assertTrue(yaml.contains("/api/v1/calendar-sync/export:"));
            assertTrue(yaml.contains("/api/v1/calendar-sync/events/{id}.ics:"));
            assertTrue(yaml.contains("/calendar-feed.ics:"));
            assertTrue(yaml.contains("CalendarSyncStatus:"));
            assertTrue(yaml.contains("CalendarSubscription:"));
            assertTrue(yaml.contains("text/calendar:"));
        }
    }

}
