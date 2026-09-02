package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ActualWorkHistoricalIdentityFrontendContractTest {

    private static final Path OPENAPI = Path.of(
            "src/main/resources/static/openapi/dutylog-v1.yaml"
    );

    private static final Path GENERATED = Path.of(
            "frontend/src/generated/dutylog-api.ts"
    );

    @Test
    void actualWorkResponseExposesHistoricalIdentityThroughGeneratedContract()
            throws Exception {

        String openapi =
                Files.readString(OPENAPI, StandardCharsets.UTF_8);

        assertTrue(openapi.contains(
                "identityReconstructed: { type: boolean }"
        ));
        assertTrue(openapi.contains("ActualWorkBreakWindowInput:"));
        assertTrue(openapi.contains("breakAuthority: { type: string, enum: [LEGACY_EARLY_TOTAL, EXPLICIT_WINDOWS] }"));

        String generated =
                Files.readString(GENERATED, StandardCharsets.UTF_8);

        int start = generated.indexOf(
                "export type ActualWorkInterval ="
        );

        int end = generated.indexOf(
                "export type ActualWorkIntervalInput =",
                start
        );

        assertTrue(start >= 0, "ActualWorkInterval generated type missing");
        assertTrue(end > start, "ActualWorkInterval generated block incomplete");

        String actualWork = generated.substring(start, end);

        assertTrue(
                actualWork.contains("sourceTimezone?: string | null;"),
                actualWork
        );
        assertTrue(
                actualWork.contains("startInstant?: string | null;"),
                actualWork
        );
        assertTrue(
                actualWork.contains("endInstant?: string | null;"),
                actualWork
        );
        assertTrue(
                actualWork.contains("identityReconstructed: boolean;"),
                actualWork
        );
        assertTrue(
                actualWork.contains("breakAuthority: \"LEGACY_EARLY_TOTAL\" | \"EXPLICIT_WINDOWS\";"),
                actualWork
        );
        assertTrue(
                actualWork.contains("breakWindows: Array<DutyLogApiSchemas.ActualWorkBreakWindow>;"),
                actualWork
        );

        int inputEnd = generated.indexOf(
                "export type AdminDatabaseStatus =",
                end
        );
        assertTrue(inputEnd > end, "ActualWorkIntervalInput generated block incomplete");

        String actualWorkInput = generated.substring(end, inputEnd);
        assertTrue(
                actualWorkInput.contains("breakWindows?: Array<DutyLogApiSchemas.ActualWorkBreakWindowInput> | null;"),
                actualWorkInput
        );
    }
}
