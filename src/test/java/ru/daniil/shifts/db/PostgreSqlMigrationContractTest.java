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
