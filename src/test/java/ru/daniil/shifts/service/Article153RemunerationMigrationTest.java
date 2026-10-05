package ru.daniil.shifts.service;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
class Article153RemunerationMigrationTest {
    String sql()throws Exception{return Files.readString(Path.of("src/main/resources/db/migration/postgresql/V88__article153_remuneration_authority.sql"));}
    @Test void schemaRequiresOwnerAndAppendOnlyRevision()throws Exception{String s=sql();assertTrue(s.contains("REFERENCES users(id)"));assertTrue(s.contains("UNIQUE (owner_id, period_month, revision)"));assertTrue(s.contains("EXTRACT(DAY FROM period_month) = 1"));assertFalse(s.contains("CASCADE"));}
    @Test void noImplicitReviewBackfillOrPayrollActivation()throws Exception{String s=sql().toUpperCase();assertFalse(s.contains("INSERT INTO"));assertFalse(s.contains("UPDATE "));assertFalse(s.contains("HOLIDAY_PAY"));}
}
