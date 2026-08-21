package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PayrollCompensationComponentOpenApiWiringTest {

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
    void openApiExposesLiveComponentReadinessAndFrozenExplainabilityLines()
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

        assertTrue(
                yaml.contains(
                        "PayrollCompensationComponentLine:"
                )
        );

        for (String field : new String[] {
                "compensationComponentCount:",
                "compensationComponentEarningsMinor:",
                "compensationComponentFingerprint:",
                "compensationComponentLines:"
        }) {
            assertTrue(
                    common.contains(field),
                    field
            );
        }

        assertTrue(
                preview.contains(
                        "compensationComponentCalculationReady:"
                )
        );

        assertTrue(
                preview.contains(
                        "compensationComponentCalculationBlockingReason:"
                )
        );

        assertFalse(
                snapshot.contains(
                        "compensationComponentCalculationReady:"
                )
        );

        assertFalse(
                snapshot.contains(
                        "compensationComponentCalculationBlockingReason:"
                )
        );

        for (String code : new String[] {
                "PAYROLL_COMP_COMPONENT_CURRENCY_MISMATCH",
                "PAYROLL_COMP_COMPONENT_BASE_UNAVAILABLE",
                "PAYROLL_COMP_COMPONENT_INVALID"
        }) {
            assertTrue(
                    period.contains(code),
                    code
            );
        }
    }

    @Test
    void generatedClientCarriesGenericComponentMoneyAndFrozenLines()
            throws Exception {

        String generated =
                Files.readString(
                        GENERATED,
                        StandardCharsets.UTF_8
                );

        /*
         * Exact source/generator identity is already owned by contract:check.
         * Here we verify the semantic TS surface needed by Payroll UI.
         */
        for (
                String marker
                : new String[] {
                        "export type PayrollCompensationComponentLine =",
                        "compensationComponentCount:",
                        "compensationComponentEarningsMinor:",
                        "compensationComponentFingerprint:",
                        "compensationComponentLines:"
                }
        ) {
            assertTrue(
                    generated.contains(
                            marker
                    ),
                    marker
            );
        }

        String preview =
                region(
                        generated,
                        "export type PayrollPreview =",
                        "export type PayrollSettings ="
                );

        String snapshot =
                region(
                        generated,
                        "export type PayrollSnapshot =",
                        "export type ProductionCalendarDay ="
                );

        assertTrue(
                preview.contains(
                        "compensationComponentCalculationReady:"
                )
        );

        assertTrue(
                preview.contains(
                        "compensationComponentCalculationBlockingReason:"
                )
        );

        /*
         * Readiness is live state only.
         * Immutable snapshots expose frozen result/provenance, not a
         * contemporary calculation blocker.
         */
        assertFalse(
                snapshot.contains(
                        "compensationComponentCalculationReady"
                )
        );

        assertFalse(
                snapshot.contains(
                        "compensationComponentCalculationBlockingReason"
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
