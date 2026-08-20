package ru.daniil.shifts.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class PayrollSnapshotOrdinaryPremiumModelTest {

    private static final String FINGERPRINT =
            "b".repeat(64);

    @Test
    void regularOnlyMayFreezeOrdinaryExplainabilityWithoutPricingIdentity() {
        PayrollSnapshot value =
                snapshot(
                        480,
                        800_000L,
                        0L,
                        null
                );

        assertEquals(
                480,
                value.getOrdinaryPremiumMinutes()
        );

        assertEquals(
                800_000L,
                value.getOrdinaryPremiumReferenceBasePayMinor()
        );

        assertEquals(
                0L,
                value.getOrdinaryPremiumPayMinor()
        );

        assertNull(
                value.getOrdinaryPremiumPricingFingerprint()
        );
    }

    @Test
    void explicitZeroPremiumMayStillFreezeDeepPricingIdentity() {
        PayrollSnapshot value =
                snapshot(
                        60,
                        100_000L,
                        0L,
                        FINGERPRINT
                );

        assertEquals(
                FINGERPRINT,
                value.getOrdinaryPremiumPricingFingerprint()
        );

        assertEquals(
                0L,
                value.getOrdinaryPremiumPayMinor()
        );
    }

    @Test
    void positivePremiumWithoutFingerprintFailsClosed() {
        IllegalArgumentException error =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> snapshot(
                                60,
                                100_000L,
                                20_000L,
                                null
                        )
                );

        assertTrue(
                error.getMessage()
                        .contains(
                                "requires pricing fingerprint"
                        )
        );
    }

    @Test
    void emptyOrdinaryComponentCannotCarryPricingIdentity() {
        assertThrows(
                IllegalArgumentException.class,
                () -> snapshot(
                        0,
                        0L,
                        0L,
                        FINGERPRINT
                )
        );
    }

    @Test
    void compatibilityConstructorKeepsLegacySnapshotNeutral() {
        AppUser owner =
                new AppUser(
                        "ordinary-snapshot-compat",
                        "{noop}unused"
                );

        PayrollSnapshot value =
                new PayrollSnapshot(
                        owner,
                        LocalDate.of(2026, 8, 1),
                        1,
                        "RUB",
                        100_000L,
                        "HOURLY",
                        LocalDate.of(2026, 8, 1),
                        100_000L,
                        null,
                        0,
                        0,
                        480,
                        480,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        480,
                        480,
                        800_000L,
                        0,
                        0,
                        0L,
                        0L,
                        0L,
                        null,
                        0L,
                        0L,
                        800_000L,
                        Instant.parse("2026-09-01T00:00:00Z"),
                        Instant.parse("2026-09-01T00:01:00Z"),
                        "a".repeat(64)
                );

        assertEquals(
                0,
                value.getOrdinaryPremiumMinutes()
        );

        assertEquals(
                0L,
                value.getOrdinaryPremiumReferenceBasePayMinor()
        );

        assertEquals(
                0L,
                value.getOrdinaryPremiumPayMinor()
        );

        assertNull(
                value.getOrdinaryPremiumPricingFingerprint()
        );
    }

    private PayrollSnapshot snapshot(
            int ordinaryMinutes,
            long referenceBase,
            long premium,
            String fingerprint
    ) {
        AppUser owner =
                new AppUser(
                        "ordinary-snapshot-model",
                        "{noop}unused"
                );

        return new PayrollSnapshot(
                owner,
                LocalDate.of(2026, 8, 1),
                1,
                "RUB",
                100_000L,
                "HOURLY",
                LocalDate.of(2026, 8, 1),
                100_000L,
                null,
                0,
                0,
                480,
                480,
                0,
                0,
                0,
                0,
                0,
                0,
                480,
                480,
                800_000L,
                ordinaryMinutes,
                referenceBase,
                premium,
                fingerprint,
                0,
                0,
                0L,
                0L,
                0L,
                null,
                0L,
                0L,
                800_000L + premium,
                Instant.parse("2026-09-01T00:00:00Z"),
                Instant.parse("2026-09-01T00:01:00Z"),
                "a".repeat(64)
        );
    }
}
