package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PayrollOrdinaryPremiumOpenApiContractTest {

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
    void openApiSeparatesLiveReadinessFromImmutableOrdinarySnapshot()
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
                "ordinaryPremiumPricingReady:",
                "ordinaryPremiumPricingBlockingReason:",
                "ordinaryPremiumPricingIdentityRequired:",
                "ordinaryPremiumMinutes:",
                "ordinaryPremiumReferenceBasePayMinor:",
                "ordinaryPremiumPayMinor:"
        }) {
            assertTrue(
                    preview.contains(field),
                    field
            );
        }

        for (String field : new String[] {
                "ordinaryPremiumMinutes:",
                "ordinaryPremiumReferenceBasePayMinor:",
                "ordinaryPremiumPayMinor:",
                "ordinaryPremiumPricingFingerprint:"
        }) {
            assertTrue(
                    snapshot.contains(field),
                    field
            );
        }

        assertFalse(
                snapshot.contains(
                        "ordinaryPremiumPricingReady:"
                )
        );

        assertFalse(
                snapshot.contains(
                        "ordinaryPremiumPricingBlockingReason:"
                )
        );

        assertFalse(
                snapshot.contains(
                        "ordinaryPremiumPricingIdentityRequired:"
                )
        );

        assertFalse(
                common.contains(
                        "ordinaryPremium"
                )
        );

        assertFalse(
                period.contains(
                        "PAYROLL_ORDINARY_PREMIUM_SNAPSHOT_REQUIRED"
                )
        );

        assertTrue(
                period.contains(
                        "ORDINARY_PREMIUM_SOURCE_NOT_READY"
                )
        );

        assertTrue(
                period.contains(
                        "PAYROLL_ORDINARY_PREMIUM_CURRENCY_MISMATCH"
                )
        );
    }

    @Test
    void generatedClientCarriesImmutableOrdinarySnapshot()
            throws Exception {

        String generated =
                Files.readString(
                        GENERATED,
                        StandardCharsets.UTF_8
                );

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

        for (String field : new String[] {
                "ordinaryPremiumPricingReady: boolean",
                "ordinaryPremiumPricingBlockingReason:",
                "ordinaryPremiumPricingIdentityRequired: boolean",
                "ordinaryPremiumMinutes: number",
                "ordinaryPremiumReferenceBasePayMinor: number",
                "ordinaryPremiumPayMinor: number"
        }) {
            assertTrue(
                    preview.contains(field),
                    field
            );
        }

        for (String field : new String[] {
                "ordinaryPremiumMinutes: number",
                "ordinaryPremiumReferenceBasePayMinor: number",
                "ordinaryPremiumPayMinor: number",
                "ordinaryPremiumPricingFingerprint: string | null"
        }) {
            assertTrue(
                    snapshot.contains(field),
                    field
            );
        }

        assertFalse(
                snapshot.contains(
                        "ordinaryPremiumPricingReady"
                )
        );

        assertFalse(
                generated.contains(
                        "PAYROLL_ORDINARY_PREMIUM_SNAPSHOT_REQUIRED"
                )
        );

        assertTrue(
                generated.contains(
                        "ORDINARY_PREMIUM_SOURCE_NOT_READY"
                )
        );

        assertTrue(
                generated.contains(
                        "PAYROLL_ORDINARY_PREMIUM_CURRENCY_MISMATCH"
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
                source.indexOf(start);

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
