package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PayPricingConfigurationOpenApiContractTest {

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
    void openApiExposesEffectiveDatedPricingCrudAndWholeTermShape()
            throws Exception {

        String yaml =
                Files.readString(
                        OPENAPI,
                        StandardCharsets.UTF_8
                );

        for (String token : new String[] {
                "/api/v1/payroll/pricing/terms:",
                "/api/v1/payroll/pricing/terms/{effectiveFrom}:",
                "operationId: listPayrollPricingTerms",
                "operationId: upsertPayrollPricingTerm",
                "operationId: deletePayrollPricingTerm",
                "PayPricingRule:",
                "PayPricingTermInput:",
                "PayPricingTerm:",
                "premiumBps:",
                "exclusiveGroup:"
        }) {
            assertTrue(yaml.contains(token), token);
        }
    }

    @Test
    void generatedClientKeepsTypedPricingConfigurationOperations()
            throws Exception {

        String generated =
                Files.readString(
                        GENERATED,
                        StandardCharsets.UTF_8
                );

        for (String token : new String[] {
                "Contract: 141 operations, 147 schemas",
                "export type PayPricingRule =",
                "export type PayPricingTermInput =",
                "export type PayPricingTerm =",
                "\"listPayrollPricingTerms\": { method: \"GET\", path: \"/api/v1/payroll/pricing/terms\" }",
                "\"upsertPayrollPricingTerm\": { method: \"PUT\", path: \"/api/v1/payroll/pricing/terms/{effectiveFrom}\" }",
                "\"deletePayrollPricingTerm\": { method: \"DELETE\", path: \"/api/v1/payroll/pricing/terms/{effectiveFrom}\" }",
                "response: Array<DutyLogApiSchemas.PayPricingTerm>",
                "requestBody: DutyLogApiSchemas.PayPricingTermInput",
                "response: DutyLogApiSchemas.PayPricingTerm"
        }) {
            assertTrue(generated.contains(token), token);
        }
    }
}
