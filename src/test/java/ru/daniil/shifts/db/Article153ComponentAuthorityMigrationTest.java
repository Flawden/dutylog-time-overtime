package ru.daniil.shifts.db;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class Article153ComponentAuthorityMigrationTest {
    @Test void migrationPreservesHistoryWithoutInferredClassificationsOrMoney() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/postgresql/V85__article153_component_authority.sql"));
        assertTrue(sql.contains("REFERENCES compensation_component_versions(id)"));
        assertTrue(sql.contains("UNIQUE (component_version_id)"));
        assertTrue(sql.contains("'INCLUDE', 'EXCLUDE', 'UNCLASSIFIED'"));
        assertTrue(sql.contains("classification_basis VARCHAR(2000) NOT NULL"));
        assertTrue(sql.contains("component_fingerprint ~ '^[0-9a-f]{64}$'"));
        assertFalse(sql.toUpperCase().contains("INSERT INTO"));
        assertFalse(sql.toUpperCase().contains("UPDATE "));
        assertFalse(sql.toUpperCase().contains("ON DELETE CASCADE"));
    }
    @Test void authorityIsNotWiredIntoMoneyOrNativeQuantity() throws Exception {
        for (String name : new String[]{"PayrollService", "PayrollNativeQualifiedQuantityService"}) {
            String source = Files.readString(Path.of("src/main/java/ru/daniil/shifts/service/"+name+".java"));
            assertFalse(source.contains("Article153ComponentAuthorityService"));
        }
    }
}
