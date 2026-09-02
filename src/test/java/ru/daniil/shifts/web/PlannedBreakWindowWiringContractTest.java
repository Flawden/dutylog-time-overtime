package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PlannedBreakWindowWiringContractTest {
    @Test
    void plannedModelsMapV78AuthorityAndSnapshotTables() throws Exception {
        String shift = read("src/main/java/ru/daniil/shifts/model/ShiftType.java");
        String day = read("src/main/java/ru/daniil/shifts/model/DayEntry.java");
        String template = read("src/main/java/ru/daniil/shifts/model/ShiftTypeBreakWindow.java");
        String snapshot = read("src/main/java/ru/daniil/shifts/model/DayEntryShiftBreakWindow.java");

        assertTrue(shift.contains("name = \"break_authority\""));
        assertTrue(shift.contains("mappedBy = \"shiftType\""));
        assertTrue(day.contains("name = \"shift_break_authority\""));
        assertTrue(day.contains("mappedBy = \"dayEntry\""));
        assertTrue(template.contains("@Table(name = \"shift_type_break_windows\")"));
        assertTrue(snapshot.contains("@Table(name = \"day_entry_shift_break_windows\")"));
    }

    @Test
    void currentAssignmentsSnapshotExplicitBreaksButLegacyMigrationDoesNotGuess() throws Exception {
        String source = read(
                "src/main/java/ru/daniil/shifts/service/ShiftOccurrenceService.java"
        );

        assertTrue(source.contains(
                "captureCurrentAssignment(entry, userTimeService.workZone(user))"
        ));
        assertTrue(source.contains(
                "breakSnapshots.captureCurrentAssignment(entry, interval)"
        ));
        assertTrue(source.contains(
                "breakSnapshots.captureLegacyEvidence(entry, interval)"
        ));
        assertTrue(source.contains(
                "captureLegacyEvidence(entry, sourceZone)"
        ));
    }

    @Test
    void plannedAllocationConsumesFrozenAbsoluteWindowsInsteadOfTemplateState() throws Exception {
        String source = read(
                "src/main/java/ru/daniil/shifts/service/"
                        + "PlannedWorkDayAllocationService.java"
        );

        assertTrue(source.contains(
                "day.getShiftBreakAuthority() == WorkBreakAuthority.EXPLICIT_WINDOWS"
        ));
        assertTrue(source.contains(
                "day.getShiftBreakWindows().stream()"
        ));
        assertTrue(source.contains(
                "breakAuthority.subtractAbsolute("
        ));
        assertFalse(source.contains(
                "day.getShiftType().getBreakWindows()"
        ));
    }

    @Test
    void actualWorkAllocatorRemainsLegacyUntilU1C() throws Exception {
        String actual = read(
                "src/main/java/ru/daniil/shifts/service/"
                        + "ActualWorkDayAllocationService.java"
        );

        assertFalse(actual.contains("WorkBreakWindowAuthorityService"));
        assertTrue(actual.contains(
                "Unpaid break minutes are consumed from the earliest actual minutes."
        ));
    }

    private static String read(String relative) throws Exception {
        return Files.readString(Path.of(relative));
    }
}
