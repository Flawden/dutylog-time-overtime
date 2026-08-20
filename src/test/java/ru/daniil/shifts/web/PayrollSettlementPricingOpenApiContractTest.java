package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PayrollSettlementPricingOpenApiContractTest {

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
    void openApiSeparatesCommonMoneyProjectionFromPreviewReadiness()
            throws Exception {

        String yaml =
                Files.readString(
                        OPENAPI,
                        StandardCharsets.UTF_8
                );

        String common =
                region(
                        yaml,
                        "    PayrollMoneyProjection:",
                        "    PayrollPreview:"
                );

        String preview =
                region(
                        yaml,
                        "    PayrollPreview:",
                        "    PayrollSnapshot:"
                );

        String snapshot =
                region(
                        yaml,
                        "    PayrollSnapshot:",
                        "    PayrollPeriod:"
                );

        String period =
                region(
                        yaml,
                        "    PayrollPeriod:",
                        "    VacationSettings:"
                );

        for (String field : new String[] {
                "hourlyBasePayableMinutes:",
                "settlementCount:",
                "settlementMinutes:",
                "settlementBasePayMinor:",
                "settlementPremiumPayMinor:",
                "settlementPayMinor:",
                "settlementPricingFingerprint:"
        }) {
            assertTrue(
                    common.contains(field),
                    field
            );
        }

        assertTrue(
                preview.contains(
                        "$ref: '#/components/schemas/PayrollMoneyProjection'"
                )
        );

        assertTrue(
                preview.contains(
                        "settlementPricingReady:"
                )
        );

        assertTrue(
                preview.contains(
                        "settlementPricingBlockingReason:"
                )
        );

        assertTrue(
                snapshot.contains(
                        "$ref: '#/components/schemas/PayrollMoneyProjection'"
                )
        );

        assertFalse(
                snapshot.contains(
                        "$ref: '#/components/schemas/PayrollPreview'"
                ),
                "Immutable snapshots must not inherit live readiness state"
        );

        assertFalse(
                snapshot.contains(
                        "settlementPricingReady:"
                )
        );

        for (String blocker : new String[] {
                "PAY_PRICING_PROVENANCE_REQUIRED",
                "PAY_PRICING_RULES_REQUIRED",
                "PAY_PRICING_CURRENCY_MISMATCH",
                "PAYROLL_SETTLEMENT_CURRENCY_MISMATCH"
        }) {
            assertTrue(
                    period.contains(blocker),
                    blocker
            );
        }

        assertFalse(
                yaml.contains(
                        "PAYROLL_SETTLEMENT_SNAPSHOT_REQUIRED"
                )
        );
    }

    @Test
    void generatedClientCarriesSettlementPayrollContractWithoutSnapshotReadinessLeak()
            throws Exception {

        String generated =
                Files.readString(
                        GENERATED,
                        StandardCharsets.UTF_8
                );

        assertTrue(
                generated.contains(
                        "export type PayrollMoneyProjection"
                )
        );

        for (String field : new String[] {
                "hourlyBasePayableMinutes:",
                "settlementCount:",
                "settlementMinutes:",
                "settlementBasePayMinor:",
                "settlementPremiumPayMinor:",
                "settlementPayMinor:",
                "settlementPricingFingerprint:"
        }) {
            assertTrue(
                    generated.contains(field),
                    field
            );
        }

        assertTrue(
                generated.contains(
                        "export type PayrollPreview = "
                                + "DutyLogApiSchemas.PayrollMoneyProjection &"
                )
        );

        assertTrue(
                generated.contains(
                        "settlementPricingReady: boolean"
                )
        );

        assertTrue(
                generated.contains(
                        "settlementPricingBlockingReason:"
                )
        );

        assertTrue(
                generated.contains(
                        "export type PayrollSnapshot = "
                                + "DutyLogApiSchemas.PayrollMoneyProjection &"
                )
        );

        String snapshot =
                region(
                        generated,
                        "export type PayrollSnapshot =",
                        "export type ProductionCalendarDay ="
                );

        assertFalse(
                snapshot.contains(
                        "settlementPricingReady"
                ),
                "Generated immutable snapshot must not own live preview readiness"
        );

        assertTrue(
                generated.contains(
                        "PAY_PRICING_PROVENANCE_REQUIRED"
                )
        );

        assertTrue(
                generated.contains(
                        "PAYROLL_SETTLEMENT_CURRENCY_MISMATCH"
                )
        );

        assertTrue(
                generated.contains(
                        "DUTYLOG_OPENAPI_SOURCE_SHA256"
                )
        );
    }

    private static String region(
            String source,
            String start,
            String end
    ) {
        int from =
                source.indexOf(
                        start
                );

        int to =
                source.indexOf(
                        end,
                        from + start.length()
                );

        assertTrue(
                from >= 0,
                "Missing region start: " + start
        );

        assertTrue(
                to > from,
                "Missing region end: " + end
        );

        return source.substring(
                from,
                to
        );
    }
}
