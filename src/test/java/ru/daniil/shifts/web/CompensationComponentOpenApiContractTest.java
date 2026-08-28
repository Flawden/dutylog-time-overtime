package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CompensationComponentOpenApiContractTest {

    private static final Path OPENAPI =
            Path.of(
                    "src/main/resources/static/openapi/"
                            + "dutylog-v1.yaml"
            );

    private static final Path GENERATED =
            Path.of(
                    "frontend/src/generated/"
                            + "dutylog-api.ts"
            );

    @Test
    void openApiExposesStableComponentCreateHistoryEffectiveAndVersionUpsert()
            throws Exception {

        String yaml =
                Files.readString(
                        OPENAPI,
                        StandardCharsets.UTF_8
                );

        for (String token : new String[] {
                "/api/v1/payroll/compensation-components:",
                "/api/v1/payroll/compensation-components/effective/{month}:",
                "/api/v1/payroll/compensation-components/{componentId}/versions/{month}:",
                "operationId: createPayrollCompensationComponent",
                "operationId: listPayrollCompensationComponentHistory",
                "operationId: listEffectivePayrollCompensationComponents",
                "operationId: upsertPayrollCompensationComponentVersion",
                "PayrollCompensationComponentCreateInput:",
                "PayrollCompensationComponentVersionInput:",
                "PayrollCompensationComponentVersion:",
                "LOCAL_ELIGIBLE_EARNINGS"
        }) {
            assertTrue(
                    yaml.contains(token),
                    token
            );
        }
    }

    @Test
    void generatedClientKeepsTypedCompensationComponentOperations()
            throws Exception {

        String generated =
                Files.readString(
                        GENERATED,
                        StandardCharsets.UTF_8
                );

        for (String token : new String[] {
                "export type PayrollCompensationComponentCreateInput =",
                "export type PayrollCompensationComponentVersionInput =",
                "export type PayrollCompensationComponentVersion =",
                "LOCAL_ELIGIBLE_EARNINGS",
                "\"createPayrollCompensationComponent\": { method: \"POST\", path: \"/api/v1/payroll/compensation-components\" }",
                "\"listPayrollCompensationComponentHistory\": { method: \"GET\", path: \"/api/v1/payroll/compensation-components\" }",
                "\"listEffectivePayrollCompensationComponents\": { method: \"GET\", path: \"/api/v1/payroll/compensation-components/effective/{month}\" }",
                "\"upsertPayrollCompensationComponentVersion\": { method: \"PUT\", path: \"/api/v1/payroll/compensation-components/{componentId}/versions/{month}\" }"
        }) {
            assertTrue(
                    generated.contains(token),
                    token
            );
        }
    }
}
