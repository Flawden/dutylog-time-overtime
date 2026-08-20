package ru.daniil.shifts.db;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PayrollSnapshotSettlementPricingSchemaTest {

    private static final Path V59 =
            Path.of(
                    "src/main/resources/db/migration/postgresql/"
                            + "V59__payroll_snapshot_settlement_pricing.sql"
            );

    private static final Path V60 =
            Path.of(
                    "src/main/resources/db/migration/postgresql/"
                            + "V60__payroll_snapshot_settlement_pricing_fingerprint.sql"
            );

    private static final Path ENTITY =
            Path.of(
                    "src/main/java/ru/daniil/shifts/model/"
                            + "PayrollSnapshot.java"
            );

    private static final Path SERVICE =
            Path.of(
                    "src/main/java/ru/daniil/shifts/service/"
                            + "PayrollService.java"
            );

    @Test
    void v59BackfillsHistoricalTruthAndFreezesNewSettlementAggregates()
            throws IOException {

        String migration =
                Files.readString(
                        V59
                );

        String entity =
                Files.readString(
                        ENTITY
                );

        assertTrue(
                migration.contains(
                        "ADD COLUMN hourly_base_payable_minutes INTEGER"
                )
        );

        assertTrue(
                migration.contains(
                        "SET hourly_base_payable_minutes = payable_minutes"
                )
        );

        assertTrue(
                migration.contains(
                        "settlement_count = 0"
                )
        );

        assertTrue(
                migration.contains(
                        "settlement_pay_minor = 0"
                )
        );

        assertTrue(
                migration.contains(
                        "ALTER COLUMN hourly_base_payable_minutes SET NOT NULL"
                )
        );

        assertTrue(
                migration.contains(
                        "ck_payroll_snapshot_settlement_money"
                )
        );

        assertTrue(
                migration.contains(
                        "settlement_pay_minor ="
                )
        );

        assertTrue(
                entity.contains(
                        "@Column(name = \"hourly_base_payable_minutes\", nullable = false)"
                )
        );

        assertTrue(
                entity.contains(
                        "@Column(name = \"settlement_pay_minor\", nullable = false)"
                )
        );

        /*
         * Never add a default to the entity mapping. New snapshots must provide
         * all immutable values explicitly through their constructor.
         */
        assertFalse(
                entity.contains(
                        "columnDefinition"
                )
        );
    }

    @Test
    void calculationHashOwnsAllNewAggregateSnapshotInputs()
            throws IOException {

        String service =
                Files.readString(
                        SERVICE
                );

        assertTrue(
                service.contains(
                        ".append(preview.hourlyBasePayableMinutes())"
                )
        );

        assertTrue(
                service.contains(
                        ".append(preview.settlementCount())"
                )
        );

        assertTrue(
                service.contains(
                        ".append(preview.settlementMinutes())"
                )
        );

        assertTrue(
                service.contains(
                        ".append(preview.settlementBasePayMinor())"
                )
        );

        assertTrue(
                service.contains(
                        ".append(preview.settlementPremiumPayMinor())"
                )
        );

        assertTrue(
                service.contains(
                        ".append(preview.settlementPayMinor())"
                )
        );

        assertTrue(
                service.contains(
                        ".append(preview.settlementPricingFingerprint()"
                )
        );

        assertFalse(
                service.contains(
                        "PAYROLL_SETTLEMENT_SNAPSHOT_REQUIRED"
                )
        );
    }

    @Test
    void v60FreezesFingerprintOnlyForNonEmptySettlementSnapshots()
            throws IOException {

        String migration =
                Files.readString(
                        V60
                );

        String entity =
                Files.readString(
                        ENTITY
                );

        assertTrue(
                migration.contains(
                        "ADD COLUMN settlement_pricing_fingerprint VARCHAR(64)"
                )
        );

        assertTrue(
                migration.contains(
                        "settlement_count = 0"
                )
        );

        assertTrue(
                migration.contains(
                        "settlement_pricing_fingerprint IS NULL"
                )
        );

        assertTrue(
                migration.contains(
                        "settlement_count > 0"
                )
        );

        assertTrue(
                migration.contains(
                        "settlement_pricing_fingerprint ~ '^[0-9a-f]{64}$'"
                )
        );

        assertTrue(
                entity.contains(
                        "@Column(name = \"settlement_pricing_fingerprint\", length = 64)"
                )
        );

        assertTrue(
                entity.contains(
                        "getSettlementPricingFingerprint()"
                )
        );
    }
}
