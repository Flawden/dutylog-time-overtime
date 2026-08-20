package ru.daniil.shifts.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PayrollOrdinaryPremiumImmutableWiringContractTest {

    @Test
    void payrollFreezesOrdinaryPremiumAndDeepFingerprint()
            throws Exception {

        String payroll =
                Files.readString(
                        Path.of(
                                "src/main/java/ru/daniil/shifts/service/"
                                        + "PayrollService.java"
                        )
                );

        assertFalse(
                payroll.contains(
                        "PAYROLL_ORDINARY_PREMIUM_SNAPSHOT_REQUIRED"
                ),
                "Temporary snapshot guard must be removed after V61 wiring"
        );

        for (String marker : new String[] {
                "preview.ordinaryPremiumMinutes()",
                "preview.ordinaryPremiumReferenceBasePayMinor()",
                "preview.ordinaryPremiumPayMinor()",
                "ordinaryPremiumPreview.pricingFingerprint()"
        }) {
            assertTrue(
                    payroll.contains(marker),
                    marker
            );
        }

        String hash =
                region(
                        payroll,
                        "    private String calculationHash(",
                        "    private PayrollSettingsDto toSettings("
                );

        assertTrue(
                hash.contains(
                        "preview.ordinaryPremiumMinutes()"
                )
        );

        assertTrue(
                hash.contains(
                        "preview.ordinaryPremiumReferenceBasePayMinor()"
                )
        );

        assertTrue(
                hash.contains(
                        "preview.ordinaryPremiumPayMinor()"
                )
        );

        assertTrue(
                hash.contains(
                        "ordinaryPremiumPricingFingerprint"
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
                "Missing start: " + start
        );

        assertTrue(
                to > from,
                "Missing end: " + end
        );

        return source.substring(
                from,
                to
        );
    }
}
