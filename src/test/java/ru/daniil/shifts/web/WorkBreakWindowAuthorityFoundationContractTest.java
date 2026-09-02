package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkBreakWindowAuthorityFoundationContractTest {
    @Test
    void migrationPreservesLegacyRowsAndAddsThreeExplicitWindowStores() throws Exception {
        String sql = read(
                "src/main/resources/db/migration/postgresql/"
                        + "V78__work_break_window_authority.sql"
        );

        assertTrue(sql.contains(
                "DEFAULT 'LEGACY_EARLY_TOTAL'"
        ));
        assertTrue(sql.contains(
                "'EXPLICIT_WINDOWS'"
        ));
        assertTrue(sql.contains(
                "CREATE TABLE shift_type_break_windows"
        ));
        assertTrue(sql.contains(
                "CREATE TABLE day_entry_shift_break_windows"
        ));
        assertTrue(sql.contains(
                "CREATE TABLE actual_work_break_windows"
        ));
    }

    @Test
    void authorityResolvesBreaksBeforePaidTimeClassification() throws Exception {
        String source = read(
                "src/main/java/ru/daniil/shifts/service/"
                        + "WorkBreakWindowAuthorityService.java"
        );

        assertTrue(source.contains(
                "public List<ResolvedBreakWindow> resolve("
        ));
        assertTrue(source.contains(
                "public List<PaidWorkInterval> subtract("
        ));
        assertTrue(source.contains(
                "sourceShiftStart.plusMinutes(window.startOffsetMinutes())"
        ));
        assertTrue(source.contains(
                "Explicit break windows must not overlap"
        ));
    }

    @Test
    void actualWiringAdvancesAtU1CWhileLegacyRowsRemainBackwardCompatible()
            throws Exception {
        String planned = read(
                "src/main/java/ru/daniil/shifts/service/"
                        + "PlannedWorkDayAllocationService.java"
        );
        String actual = read(
                "src/main/java/ru/daniil/shifts/service/"
                        + "ActualWorkDayAllocationService.java"
        );

        assertTrue(planned.contains(
                "day.getShiftBreakAuthority() == WorkBreakAuthority.EXPLICIT_WINDOWS"
        ));
        assertTrue(planned.contains(
                "breakAuthority.subtractAbsolute("
        ));
        assertTrue(planned.contains(
                "unpaid break minutes are consumed from the earliest clock minutes"
        ));

        assertTrue(actual.contains(
                "interval.getBreakAuthority() == WorkBreakAuthority.EXPLICIT_WINDOWS"
        ));
        assertTrue(actual.contains(
                "breakAuthority.subtractAbsolute("
        ));
        assertTrue(actual.contains(
                "legacy scalar earliest-break semantics"
        ));
    }

    @Test
    void schemaDoesNotGuessHistoricalBreakPlacement() throws Exception {
        String sql = read(
                "src/main/resources/db/migration/postgresql/"
                        + "V78__work_break_window_authority.sql"
        );

        assertFalse(sql.contains("INSERT INTO shift_type_break_windows"));
        assertFalse(sql.contains("INSERT INTO day_entry_shift_break_windows"));
        assertFalse(sql.contains("INSERT INTO actual_work_break_windows"));
        assertTrue(sql.contains(
                "Existing rows intentionally keep LEGACY_EARLY_TOTAL semantics"
        ));
    }

    private static String read(String relative) throws Exception {
        return Files.readString(Path.of(relative));
    }
}
