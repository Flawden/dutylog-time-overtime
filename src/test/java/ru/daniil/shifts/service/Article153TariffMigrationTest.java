package ru.daniil.shifts.service;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
class Article153TariffMigrationTest {
    @Test void referencesRequireFrozenAuthorityAndDoNotBackfill() throws Exception {
        String sql=Files.readString(Path.of("src/main/resources/db/migration/postgresql/V87__article153_tariff_reference.sql"));
        assertTrue(sql.contains("REFERENCES payroll_snapshot_article153(snapshot_id)"));
        assertFalse(sql.toUpperCase().contains("INSERT INTO"));assertFalse(sql.toUpperCase().contains("ON DELETE CASCADE"));
    }
    @Test void tariffReferencesCannotAccidentallyActivateNativeMoney() throws Exception {
        for(String name:new String[]{"PayrollService","PayrollNativeQualifiedQuantityService"}){
            String java=Files.readString(Path.of("src/main/java/ru/daniil/shifts/service/"+name+".java"));
            assertFalse(java.contains("Article153Tariff"));
        }
    }
}
