package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PlannedBreakWindowWiringContractTest {
    @Test
    void plannedAndActualModelsMapV78AuthorityAndSnapshotTables() throws Exception {
        String shift = read("src/main/java/ru/daniil/shifts/model/ShiftType.java");
        String day = read("src/main/java/ru/daniil/shifts/model/DayEntry.java");
        String actual = read("src/main/java/ru/daniil/shifts/model/ActualWorkInterval.java");
        String template = read("src/main/java/ru/daniil/shifts/model/ShiftTypeBreakWindow.java");
        String plannedSnapshot = read("src/main/java/ru/daniil/shifts/model/DayEntryShiftBreakWindow.java");
        String actualSnapshot = read("src/main/java/ru/daniil/shifts/model/ActualWorkBreakWindow.java");

        assertTrue(shift.contains("name = \"break_authority\""));
        assertTrue(shift.contains("mappedBy = \"shiftType\""));
        assertTrue(day.contains("name = \"shift_break_authority\""));
        assertTrue(day.contains("mappedBy = \"dayEntry\""));
        assertTrue(actual.contains("name = \"break_authority\""));
        assertTrue(actual.contains("mappedBy = \"actualWorkInterval\""));
        assertTrue(template.contains("@Table(name = \"shift_type_break_windows\")"));
        assertTrue(plannedSnapshot.contains("@Table(name = \"day_entry_shift_break_windows\")"));
        assertTrue(actualSnapshot.contains("@Table(name = \"actual_work_break_windows\")"));
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
    void actualWorkAllocatorAndWritePathUseFrozenExplicitWindowsInU1C() throws Exception {
        String allocator = read(
                "src/main/java/ru/daniil/shifts/service/"
                        + "ActualWorkDayAllocationService.java"
        );
        String actualWork = read(
                "src/main/java/ru/daniil/shifts/service/ActualWorkService.java"
        );
        String snapshots = read(
                "src/main/java/ru/daniil/shifts/service/"
                        + "ActualBreakWindowSnapshotService.java"
        );

        assertTrue(allocator.contains("WorkBreakAuthority.EXPLICIT_WINDOWS"));
        assertTrue(allocator.contains("interval.getBreakWindows().stream()"));
        assertTrue(allocator.contains("breakAuthority.subtractAbsolute("));
        assertTrue(allocator.contains("exactLegacySegments("));

        assertTrue(actualWork.contains("req.breakWindows()"));
        assertTrue(actualWork.contains("breakSnapshots.capture(interval, explicit)"));
        assertTrue(snapshots.contains("resolveSourceLocal("));
        assertTrue(snapshots.contains("reconstructHistoricalIdentity("));
    }

    private static String read(String relative) throws Exception {
        return Files.readString(Path.of(relative));
    }
}
