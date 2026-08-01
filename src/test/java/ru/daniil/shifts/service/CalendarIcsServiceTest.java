package ru.daniil.shifts.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.daniil.shifts.config.SecurityEventLogger;
import ru.daniil.shifts.dto.Dtos.*;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.ImportantEventType;
import ru.daniil.shifts.model.RepeatMode;
import ru.daniil.shifts.model.TaskPriority;
import ru.daniil.shifts.service.exception.ApiException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CalendarIcsServiceTest {
    private CalendarService calendars;
    private ImportantDayService importantDays;
    private DayEntryService dayEntries;
    private UserTimeService time;
    private CalendarIcsService service;
    private AppUser user;

    @BeforeEach
    void setUp() {
        calendars = mock(CalendarService.class);
        importantDays = mock(ImportantDayService.class);
        dayEntries = mock(DayEntryService.class);
        time = mock(UserTimeService.class);
        when(time.workZone(any())).thenReturn(ZoneId.of("Europe/Chisinau"));
        when(time.nowInstant()).thenReturn(Instant.parse("2026-07-31T04:00:00Z"));
        when(time.resolveZone(any(), any())).thenAnswer(invocation -> {
            String value = invocation.getArgument(0);
            ZoneId fallback = invocation.getArgument(1);
            return value == null || value.isBlank() ? fallback : ZoneId.of(value);
        });
        when(time.resolveLocalDateTime(any(), any())).thenAnswer(invocation -> {
            LocalDateTime value = invocation.getArgument(0);
            ZoneId zone = invocation.getArgument(1);
            return value.atZone(zone);
        });
        service = new CalendarIcsService(calendars, importantDays, dayEntries, time,
                mock(SecurityEventLogger.class), 100, 1_000_000);
        user = new AppUser("ics-owner", "hash");
    }

    @Test
    void rangeExportContainsShiftsTasksImportantEventsAndAbsences() {
        LocalDate from = LocalDate.of(2026, 8, 1);
        LocalDate to = LocalDate.of(2026, 8, 14);
        when(calendars.range(user, from, to)).thenReturn(fullRange());

        CalendarIcsService.IcsExport export = service.exportRange(user, from, to);
        String text = new String(export.bytes(), StandardCharsets.UTF_8);

        assertEquals(4, export.eventCount());
        assertTrue(text.startsWith("BEGIN:VCALENDAR\r\n"));
        assertTrue(text.endsWith("END:VCALENDAR\r\n"));
        assertTrue(text.contains("UID:shift-10@dutylog"));
        assertTrue(text.contains("UID:task-20@dutylog"));
        assertTrue(text.contains("UID:important-30-2026-08-03@dutylog"));
        assertTrue(text.contains("UID:absence-40@dutylog"));
        assertTrue(text.contains("DTSTART:20260801T060000Z"));
        assertTrue(text.contains("Status: completed"));
        assertFalse(text.contains("STATUS:COMPLETED"), "VEVENT must not use VTODO-only COMPLETED status");
        assertTrue(text.contains("SUMMARY:Встреча\\, команда"));
        assertTrue(text.contains("DTEND;VALUE=DATE:20260815"), "absence end must be exclusive");
        assertFalse(text.replace("\r\n", "").contains("\n"), "RFC output must use CRLF only");
        for (String line : text.split("\r\n", -1)) {
            assertTrue(line.getBytes(StandardCharsets.UTF_8).length <= 75,
                    () -> "unfolded content line exceeds 75 octets: " + line);
        }
    }

    @Test
    void duplicateDayProjectionsBecomeOneImportantEventAndOneAbsence() {
        CalendarRangeDto range = fullRange();
        ImportantDayOccurrenceDto important = range.importantDays().get(0);
        AbsenceOccurrenceDto absence = range.absences().get(0);
        CalendarRangeDto duplicated = new CalendarRangeDto(
                range.from(), range.to(), range.shiftTypes(), range.days(), List.of(), List.of(),
                List.of(important, important), List.of(absence, absence), null, null, null,
                List.of(), List.of(), List.of(), List.of());
        when(calendars.range(eq(user), any(), any())).thenReturn(duplicated);

        CalendarIcsService.IcsExport export = service.exportRange(user,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 14));

        assertEquals(2, export.eventCount());
    }

    @Test
    void partialTimeOffExportsAsTimedEventInTheOwnersWorkZone() {
        AbsenceOccurrenceDto partial = new AbsenceOccurrenceDto(
                41L, 5L, "Отгул", "#4A90E2", "TIME_OFF", "Врач",
                "2026-08-06", "2026-08-06", "2026-08-06", "APPROVED", false, true,
                "TIME_OFF_HOURS", "PARTIAL", "09:00", "13:00", 240, false,
                "Дневная", "#F5B841", 450);
        CalendarRangeDto range = new CalendarRangeDto(
                "2026-08-06", "2026-08-06", List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(partial), null, null, null, List.of(), List.of(), List.of(), List.of());
        when(calendars.range(user, LocalDate.of(2026, 8, 6), LocalDate.of(2026, 8, 6))).thenReturn(range);

        String text = new String(service.exportRange(user, LocalDate.of(2026, 8, 6), LocalDate.of(2026, 8, 6)).bytes(),
                StandardCharsets.UTF_8);

        assertTrue(text.contains("UID:absence-41@dutylog"));
        assertTrue(text.contains("DTSTART:20260806T060000Z"));
        assertTrue(text.contains("DTEND:20260806T100000Z"));
        assertTrue(text.contains("CATEGORIES:ABSENCE,TIME-OFF"));
        assertTrue(text.contains("planned shift: Дневная"));
        assertFalse(text.contains("DTSTART;VALUE=DATE:20260806"));
    }

    @Test
    void singleImportantEventPreservesRecurrenceAndEscapesText() {
        ImportantDayDto event = new ImportantDayDto(
                77L, "Ревью; квартал", "2026-08-05", RepeatMode.MONTHLY, "#F5B841",
                ImportantEventType.EVENT, "2026-08-05", false, "09:00", "10:15",
                "2026-08-05T06:00:00Z", "2026-08-05T07:15:00Z", "Europe/Chisinau",
                "Офис, 2 этаж", "Строка 1\nСтрока 2", "★", "Работа", List.of(15));
        when(importantDays.list(user)).thenReturn(List.of(event));

        CalendarIcsService.IcsExport export = service.exportImportantEvent(user, 77L);
        String text = new String(export.bytes(), StandardCharsets.UTF_8);

        assertEquals(1, export.eventCount());
        assertTrue(text.contains("RRULE:FREQ=MONTHLY"));
        assertTrue(text.contains("SUMMARY:Ревью\\; квартал"));
        assertTrue(text.contains("LOCATION:Офис\\, 2 этаж"));
        assertTrue(text.contains("DESCRIPTION:Строка 1\\nСтрока 2"));
        assertEquals(404, assertThrows(ApiException.class,
                () -> service.exportImportantEvent(user, 999L)).getStatus().value());
    }

    @Test
    void eventCountLimitAndUtf8FoldingAreEnforced() {
        CalendarIcsService limited = new CalendarIcsService(calendars, importantDays, dayEntries, time,
                mock(SecurityEventLogger.class), 1, 1_000_000);
        when(calendars.range(eq(user), any(), any())).thenReturn(fullRange());

        assertEquals(413, assertThrows(ApiException.class, () -> limited.exportRange(user,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 14))).getStatus().value());

        StringBuilder line = new StringBuilder();
        CalendarIcsService.line(line, "SUMMARY:" + "Ж".repeat(80));
        String[] physical = line.toString().split("\r\n");
        assertTrue(physical.length > 1);
        assertTrue(physical[1].startsWith(" "));
        for (String physicalLine : physical) {
            assertTrue(physicalLine.getBytes(StandardCharsets.UTF_8).length <= 75);
        }
        assertEquals("a\\,b\\;c\\nline\\\\x", CalendarIcsService.escapeText("a,b;c\nline\\x"));
    }

    private CalendarRangeDto fullRange() {
        ShiftTypeDto shiftType = new ShiftTypeDto(1L, "Дневная", 8, "#F5B841", false,
                "09:00", "17:00", 30, 7.5, true, 30);
        ShiftOccurrenceDto shift = new ShiftOccurrenceDto(
                10L, "2026-08-01", 1L,
                "2026-08-01T06:00:00Z", "2026-08-01T14:00:00Z",
                "2026-08-01T09:00", "2026-08-01T17:00",
                "2026-08-01T09:00", "2026-08-01T17:00",
                "Europe/Chisinau", "Europe/Chisinau", 30, 480, 450, false);
        TaskDto task = new TaskDto(
                20L, "2026-08-02", "Закрыть задачу", true, "Работа", List.of("релиз"),
                TaskPriority.HIGH, "2026-08-02", "12:00", false, null, false, List.of(),
                "Описание", true, "Europe/Chisinau", "2026-08-02", "12:00", "DutyLog",
                false, "2026-08-02", "10:00", "2026-08-02", "11:30", 90L, true,
                "Europe/Chisinau", "2026-08-02", "10:00", "2026-08-02", "11:30");
        ImportantDayOccurrenceDto important = new ImportantDayOccurrenceDto(
                30L, "2026-08-03", "Встреча, команда", RepeatMode.NONE, "#4FA3A5",
                ImportantEventType.EVENT, "2026-08-03", "2026-08-03", false,
                "13:00", "14:00", "2026-08-03T10:00:00Z", "2026-08-03T11:00:00Z",
                "Europe/Chisinau", "Europe/Chisinau", "Переговорная", "План", "★", "Работа", List.of());
        AbsenceOccurrenceDto absence = new AbsenceOccurrenceDto(
                40L, 4L, "Отпуск", "#6FBF73", "VACATION", "Летний отпуск",
                "2026-08-04", "2026-08-04", "2026-08-14", "APPROVED", true, false);
        return new CalendarRangeDto(
                "2026-08-01", "2026-08-14", List.of(shiftType), List.of(), List.of(shift),
                List.of(task), List.of(important), List.of(absence), null, null, null,
                List.of(), List.of(), List.of(), List.of());
    }
}
