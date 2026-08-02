package ru.daniil.shifts.db;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PayrollSnapshotHashSchemaValidationHotfixTest {

    private static final Path V45 = Path.of(
            "src/main/resources/db/migration/postgresql/V45__payroll_foundation.sql");
    private static final Path V46 = Path.of(
            "src/main/resources/db/migration/postgresql/V46__payroll_snapshot_hash_schema_alignment.sql");
    private static final Path ENTITY = Path.of(
            "src/main/java/ru/daniil/shifts/model/PayrollSnapshot.java");

    @Test
    void v46AlignsSnapshotHashWithJpaWithoutRewritingV45() throws IOException, NoSuchAlgorithmException {
        byte[] v45Bytes = Files.readAllBytes(V45);
        String v45 = new String(v45Bytes, StandardCharsets.UTF_8);
        String v46 = Files.readString(V46, StandardCharsets.UTF_8);
        String entity = Files.readString(ENTITY, StandardCharsets.UTF_8);

        assertEquals(
                "6fab27acb0af68a36dfe2dc85c4df09562cc273cf0bb859807ae34f518798709",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(v45Bytes)),
                "the released V45 migration must remain immutable");
        assertTrue(v45.contains("calculation_hash CHAR(64) NOT NULL"));
        assertTrue(v45.contains("ck_payroll_snapshot_hash CHECK (calculation_hash ~ '^[0-9a-f]{64}$')"));

        assertTrue(v46.contains("ALTER TABLE payroll_snapshots"));
        assertTrue(v46.contains("ALTER COLUMN calculation_hash TYPE VARCHAR(64)"));
        assertTrue(v46.contains("USING BTRIM(calculation_hash)"));
        assertFalse(v46.contains("DROP CONSTRAINT ck_payroll_snapshot_hash"));
        assertFalse(v46.contains("DROP NOT NULL"));
        assertFalse(v46.toUpperCase().contains("DROP TABLE"));

        assertTrue(entity.contains(
                "@Column(name = \"calculation_hash\", nullable = false, length = 64)"));
        assertFalse(entity.contains("columnDefinition = \"CHAR(64)\""));
    }
}
