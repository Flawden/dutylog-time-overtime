package ru.daniil.shifts.db;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PayrollSnapshotOrdinaryPremiumPricingSchemaTest {

    private static final Path V61 =
            Path.of(
                    "src/main/resources/db/migration/postgresql/"
                            + "V61__payroll_snapshot_ordinary_premium_pricing.sql"
            );

    private static final Path ENTITY =
            Path.of(
                    "src/main/java/ru/daniil/shifts/model/"
                            + "PayrollSnapshot.java"
            );

    @Test
    void v61BackfillsHistoricalSnapshotsWithoutRewritingOldMoney()
            throws Exception {

        String migration =
                Files.readString(
                        V61
                );

        assertTrue(
                migration.contains(
                        "ADD COLUMN ordinary_premium_minutes INTEGER"
                )
        );

        assertTrue(
                migration.contains(
                        "ADD COLUMN ordinary_premium_reference_base_pay_minor BIGINT"
                )
        );

        assertTrue(
                migration.contains(
                        "ADD COLUMN ordinary_premium_pay_minor BIGINT"
                )
        );

        assertTrue(
                migration.contains(
                        "ADD COLUMN ordinary_premium_pricing_fingerprint VARCHAR(64)"
                )
        );

        assertTrue(
                migration.contains(
                        "ordinary_premium_minutes = 0"
                )
        );

        assertTrue(
                migration.contains(
                        "ordinary_premium_pricing_fingerprint = NULL"
                )
        );

        assertFalse(
                migration.contains(
                        "UPDATE payroll_snapshots\nSET total_pay_minor"
                ),
                "V61 must not reinterpret historical Payroll totals"
        );
    }

    @Test
    void v61AllowsExplicitZeroPremiumIdentityButRejectsMoneyWithoutIdentity()
            throws Exception {

        String migration =
                Files.readString(
                        V61
                );

        assertTrue(
                migration.contains(
                        "ordinary_premium_pay_minor = 0\n        OR ordinary_premium_pricing_fingerprint IS NOT NULL"
                )
        );

        assertTrue(
                migration.contains(
                        "ordinary_premium_pricing_fingerprint ~ '^[0-9a-f]{64}$'"
                )
        );

        /*
         * Fingerprint is not tied to premium > 0.
         * Therefore NIGHT +0% remains distinguishable from REGULAR/no-policy.
         */
        assertFalse(
                migration.contains(
                        "ordinary_premium_pricing_fingerprint IS NOT NULL\n        AND ordinary_premium_pay_minor > 0"
                )
        );
    }

    @Test
    void entityOwnsDedicatedOrdinaryPremiumFields()
            throws Exception {

        String entity =
                Files.readString(
                        ENTITY
                );

        for (String marker : new String[] {
                "ordinary_premium_minutes",
                "ordinary_premium_reference_base_pay_minor",
                "ordinary_premium_pay_minor",
                "ordinary_premium_pricing_fingerprint",
                "getOrdinaryPremiumMinutes()",
                "getOrdinaryPremiumReferenceBasePayMinor()",
                "getOrdinaryPremiumPayMinor()",
                "getOrdinaryPremiumPricingFingerprint()"
        }) {
            assertTrue(
                    entity.contains(marker),
                    marker
            );
        }

        assertTrue(
                entity.contains(
                        "Positive ordinary premium snapshot requires pricing fingerprint"
                )
        );
    }
}
