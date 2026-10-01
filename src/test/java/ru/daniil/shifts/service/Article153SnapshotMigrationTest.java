package ru.daniil.shifts.service;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class Article153SnapshotMigrationTest {
    @Test void migrationHasRevisionForeignKeyAndNoHistoricalBackfill() throws Exception {
        String sql=Files.readString(Path.of("src/main/resources/db/migration/postgresql/V86__article153_snapshot_authority.sql"));
        assertTrue(sql.contains("snapshot_id BIGINT PRIMARY KEY REFERENCES payroll_snapshots(id)"));
        assertTrue(sql.contains("piece_count >= 0"));assertTrue(sql.contains("qualified_minutes >= 0"));
        assertTrue(sql.contains("fingerprint ~ '^[0-9a-f]{64}$'"));
        assertFalse(sql.toUpperCase().contains("INSERT INTO"));assertFalse(sql.toUpperCase().contains("UPDATE "));
        assertFalse(sql.toUpperCase().contains("ON DELETE CASCADE"));
    }
    @Test void preparationDoesNotActivateHolidayMoney() throws Exception {
        for(String name:new String[]{"PayrollService","PayrollNativeQualifiedQuantityService"}) {
            String source=Files.readString(Path.of("src/main/java/ru/daniil/shifts/service/"+name+".java"));
            assertFalse(source.contains("Article153SnapshotFreezeService"));
        }
    }
}
