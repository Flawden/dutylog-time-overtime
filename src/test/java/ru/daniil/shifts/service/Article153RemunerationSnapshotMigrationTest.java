package ru.daniil.shifts.service;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
class Article153RemunerationSnapshotMigrationTest {
    String sql()throws Exception{return Files.readString(Path.of("src/main/resources/db/migration/postgresql/V89__article153_remuneration_snapshot.sql"));}
    @Test void bindsTariffRevisionAndExactSourceReview()throws Exception{String s=sql();assertTrue(s.contains("PRIMARY KEY REFERENCES payroll_snapshot_article153_tariff(snapshot_id)"));assertTrue(s.contains("REFERENCES article153_remuneration_authorities(id)"));assertFalse(s.contains("CASCADE"));}
    @Test void noMoneyActivationOrHistoricalBackfill()throws Exception{String s=sql().toUpperCase();assertFalse(s.contains("INSERT INTO"));assertFalse(s.contains("UPDATE "));assertFalse(s.contains("HOLIDAY_PAY"));}
}
