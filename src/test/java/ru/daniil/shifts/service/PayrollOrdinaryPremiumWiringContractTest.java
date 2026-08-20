package ru.daniil.shifts.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PayrollOrdinaryPremiumWiringContractTest {

    @Test
    void liveDeltaAndImmutableSnapshotUseDedicatedOrdinaryComponent()
            throws Exception {

        String payroll =
                Files.readString(
                        Path.of(
                                "src/main/java/ru/daniil/shifts/service/"
                                        + "PayrollService.java"
                        )
                );

        String dto =
                Files.readString(
                        Path.of(
                                "src/main/java/ru/daniil/shifts/dto/"
                                        + "Dtos.java"
                        )
                );

        String snapshot =
                Files.readString(
                        Path.of(
                                "src/main/java/ru/daniil/shifts/model/"
                                        + "PayrollSnapshot.java"
                        )
                );

        assertTrue(
                payroll.contains(
                        "ordinaryPremiumPricing.preview("
                )
        );

        assertTrue(
                payroll.contains(
                        "ordinaryPremiumPreview.premiumAmountMinor()"
                )
        );

        assertTrue(
                payroll.contains(
                        "ordinaryPremiumPreview.pricingFingerprint()"
                )
        );

        assertFalse(
                payroll.contains(
                        "PAYROLL_ORDINARY_PREMIUM_SNAPSHOT_REQUIRED"
                )
        );

        assertTrue(
                payroll.contains(
                        "ordinaryPremiumPay,\n                        additions"
                )
        );

        assertTrue(
                dto.contains(
                        "boolean ordinaryPremiumPricingReady"
                )
        );

        assertTrue(
                dto.contains(
                        "boolean ordinaryPremiumPricingIdentityRequired"
                )
        );

        assertTrue(
                dto.contains(
                        "String ordinaryPremiumPricingFingerprint"
                )
        );

        for (String marker : new String[] {
                "ordinary_premium_minutes",
                "ordinary_premium_reference_base_pay_minor",
                "ordinary_premium_pay_minor",
                "ordinary_premium_pricing_fingerprint"
        }) {
            assertTrue(
                    snapshot.contains(marker),
                    marker
            );
        }

        for (String marker : new String[] {
                "value.getOrdinaryPremiumMinutes()",
                "value.getOrdinaryPremiumReferenceBasePayMinor()",
                "value.getOrdinaryPremiumPayMinor()",
                "value.getOrdinaryPremiumPricingFingerprint()"
        }) {
            assertTrue(
                    payroll.contains(marker),
                    marker
            );
        }
    }
}
