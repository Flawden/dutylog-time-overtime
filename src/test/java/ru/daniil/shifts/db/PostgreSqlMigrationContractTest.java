package ru.daniil.shifts.db;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostgreSqlMigrationContractTest {

    private static final Path MIGRATION_ROOT = Path.of(
            "src", "main", "resources", "db", "migration", "postgresql"
    );
    private static final Pattern VERSION = Pattern.compile("V(\\d+)__.+\\.sql");
    private static final Pattern CREATE_TABLE = Pattern.compile(
            "(?i)\\bcreate\\s+table\\s+(?:if\\s+not\\s+exists\\s+)?([a-z_][a-z0-9_]*)"
    );
    private static final Pattern REFERENCES_TABLE = Pattern.compile(
            "(?i)\\breferences\\s+([a-z_][a-z0-9_]*)\\s*\\("
    );

    @Test
    void everyForeignKeyTargetsATableCreatedByTheSameOrAnEarlierMigration() throws IOException {
        assertTrue(Files.isDirectory(MIGRATION_ROOT), "PostgreSQL migration directory is missing");

        List<Path> migrations;
        try (var files = Files.list(MIGRATION_ROOT)) {
            migrations = files
                    .filter(path -> VERSION.matcher(path.getFileName().toString()).matches())
                    .sorted(Comparator.comparingInt(this::migrationVersion))
                    .toList();
        }

        assertFalse(migrations.isEmpty(), "No PostgreSQL migrations found");

        Set<String> knownTables = new HashSet<>();
        for (Path migration : migrations) {
            String sql = Files.readString(migration);
            Set<String> tablesCreatedHere = matches(CREATE_TABLE, sql);
            Set<String> availableTables = new HashSet<>(knownTables);
            availableTables.addAll(tablesCreatedHere);

            for (String referencedTable : matches(REFERENCES_TABLE, sql)) {
                assertTrue(
                        availableTables.contains(referencedTable),
                        () -> migration.getFileName() + " references missing table '" + referencedTable
                                + "'; available tables: " + availableTables
                );
            }
            knownTables.addAll(tablesCreatedHere);
        }
    }

    @Test
    void timeFoundationMigrationPreservesUnzonedLegacyDeliveriesWithoutGuessing() throws IOException {
        String sql = Files.readString(MIGRATION_ROOT.resolve("V29__time_foundation.sql"));

        assertTrue(sql.contains("ADD COLUMN IF NOT EXISTS display_timezone"));
        assertTrue(sql.contains("remind_at_instant TIMESTAMPTZ"));
        assertTrue(sql.contains("DROP CONSTRAINT IF EXISTS uq_tg_notification_once"));
        assertTrue(sql.contains("UNIQUE (user_id, reminder_id, remind_at_instant)"));
        assertTrue(sql.contains("WHERE remind_at_instant IS NULL"));
        assertFalse(sql.contains("UPDATE telegram_notification_deliveries"),
                "legacy local timestamps must not be reinterpreted through the owner's current timezone");
        assertFalse(sql.contains("ALTER COLUMN remind_at_instant SET NOT NULL"),
                "legacy rows intentionally have no trustworthy absolute identity");
    }

    @Test
    void zonedWorkIntervalsPreserveLegacyOvertimeWithoutGuessing() throws IOException {
        String sql = Files.readString(MIGRATION_ROOT.resolve("V30__zoned_work_intervals.sql"));

        assertTrue(sql.contains("start_at_instant TIMESTAMPTZ"));
        assertTrue(sql.contains("end_at_instant TIMESTAMPTZ"));
        assertTrue(sql.contains("source_timezone VARCHAR(80)"));
        assertTrue(sql.contains("idx_overtime_credits_absolute_interval"));
        assertFalse(sql.contains("UPDATE overtime_credits"),
                "historical local overtime must not be assigned a guessed source timezone");
        assertFalse(sql.contains("ALTER COLUMN start_at_instant SET NOT NULL"),
                "legacy calculated rows intentionally remain local-only");
    }

    @Test
    void overtimeIntervalEngineBackfillsOnlyTrustworthyAbsoluteSources() throws IOException {
        String sql = Files.readString(MIGRATION_ROOT.resolve("V31__overtime_interval_engine.sql"));

        assertTrue(sql.contains("credited_minutes INTEGER"));
        assertTrue(sql.contains("allocated_minutes INTEGER"));
        assertTrue(sql.contains("credited_start_at_instant TIMESTAMPTZ"));
        assertTrue(sql.contains("start_at_instant TIMESTAMPTZ"));
        assertTrue(sql.contains("WHERE start_at_instant IS NOT NULL"));
        assertTrue(sql.contains("reconstructed = TRUE"));
        assertFalse(sql.contains("AT TIME ZONE"),
                "V31 must not guess a timezone for legacy local-only overtime rows");
        assertFalse(sql.contains("ALTER COLUMN credited_start_at_instant SET NOT NULL"),
                "quantity-only legacy credits intentionally remain non-exact");
    }

    @Test
    void vacationPlannerMigrationKeepsAbsencesSeparateFromShiftRows() throws IOException {
        String sql = Files.readString(MIGRATION_ROOT.resolve("V40__vacation_planner.sql"));

        assertTrue(sql.contains("CREATE TABLE vacation_settings"));
        assertTrue(sql.contains("INSERT INTO vacation_settings(user_id)"));
        assertTrue(sql.contains("ON CONFLICT (user_id) DO NOTHING"));
        assertTrue(sql.contains("CREATE TABLE absence_types"));
        assertTrue(sql.contains("CREATE TABLE absence_periods"));
        assertTrue(sql.contains("counts_against_allowance"));
        assertTrue(sql.contains("CALENDAR_DAYS"));
        assertTrue(sql.contains("WEEKDAYS"));
        assertTrue(sql.contains("PLANNED"));
        assertTrue(sql.contains("APPROVED"));
        assertFalse(sql.contains("ALTER TABLE day_entries"),
                "vacation periods must not be encoded as shift rows");
        assertFalse(sql.contains("INSERT INTO shift_types"),
                "absence types are a separate domain from shift types");
    }

    private Set<String> matches(Pattern pattern, String sql) {
        Set<String> values = new HashSet<>();
        Matcher matcher = pattern.matcher(sql);
        while (matcher.find()) {
            values.add(matcher.group(1).toLowerCase());
        }
        return values;
    }

    private int migrationVersion(Path path) {
        Matcher matcher = VERSION.matcher(path.getFileName().toString());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid migration file name: " + path.getFileName());
        }
        return Integer.parseInt(matcher.group(1));
    }
}
