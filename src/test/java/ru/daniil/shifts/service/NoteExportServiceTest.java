package ru.daniil.shifts.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import ru.daniil.shifts.config.SecurityEventLogger;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.DayEntry;
import ru.daniil.shifts.repo.DayEntryRepository;
import ru.daniil.shifts.service.exception.ApiException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NoteExportServiceTest {

    @Test
    void countLimitRejectsBeforeRowsAreLoaded() {
        DayEntryRepository repository = mock(DayEntryRepository.class);
        AppUser user = new AppUser("export-limit", "{noop}x");
        when(repository.countByOwnerAndNoteIsNotNull(user)).thenReturn(3L);

        NoteExportService service = new NoteExportService(repository, mock(SecurityEventLogger.class), 2, 4096);
        ApiException error = assertThrows(ApiException.class, () -> service.prepare(user));

        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, error.getStatus());
        verify(repository, never()).findNotesForExport(user);
    }

    @Test
    void uncompressedByteLimitRejectsOversizedExport() {
        DayEntryRepository repository = mock(DayEntryRepository.class);
        AppUser user = new AppUser("export-bytes", "{noop}x");
        DayEntry entry = new DayEntry(user, LocalDate.of(2026, 7, 10));
        entry.setNote("x".repeat(2048));
        when(repository.countByOwnerAndNoteIsNotNull(user)).thenReturn(1L);
        when(repository.findNotesForExport(user)).thenReturn(List.of(entry));

        NoteExportService service = new NoteExportService(repository, mock(SecurityEventLogger.class), 10, 1024);
        ApiException error = assertThrows(ApiException.class, () -> service.prepare(user));

        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, error.getStatus());
    }

    @Test
    void postReadLimitProtectsAgainstRowsChangingBetweenCountAndSelect() {
        DayEntryRepository repository = mock(DayEntryRepository.class);
        AppUser user = new AppUser("export-race", "{noop}x");
        DayEntry first = note(user, LocalDate.of(2026, 7, 1), "one");
        DayEntry second = note(user, LocalDate.of(2026, 7, 2), "two");
        when(repository.countByOwnerAndNoteIsNotNull(user)).thenReturn(1L);
        when(repository.findNotesForExport(user)).thenReturn(List.of(first, second));

        ApiException error = assertThrows(ApiException.class,
                () -> new NoteExportService(repository, mock(SecurityEventLogger.class), 1, 4096)
                        .prepare(user));

        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, error.getStatus());
    }

    @Test
    void blankRowsAreFilteredAndSuccessfulPlanIsAudited() {
        DayEntryRepository repository = mock(DayEntryRepository.class);
        SecurityEventLogger audit = mock(SecurityEventLogger.class);
        AppUser user = new AppUser("export-plan", "{noop}x");
        DayEntry blank = note(user, LocalDate.of(2026, 7, 1), "   \n");
        DayEntry actual = note(user, LocalDate.of(2026, 7, 2), "hello");
        when(repository.countByOwnerAndNoteIsNotNull(user)).thenReturn(2L);
        when(repository.findNotesForExport(user)).thenReturn(List.of(blank, actual));

        NoteExportService.NoteExportPlan plan =
                new NoteExportService(repository, audit, 10, 4096).prepare(user);

        assertEquals(1, plan.rows().size());
        assertEquals(LocalDate.of(2026, 7, 2), plan.rows().get(0).date());
        assertTrue(plan.uncompressedBytes() > 0);
        verify(audit).info(eq("DATA_EXPORT_NOTES"), eq("export-plan"), eq("accepted"), anyString());
    }

    @Test
    void zipWriterCreatesYearFoldersAndReadme() throws Exception {
        NoteExportService service = new NoteExportService(
                mock(DayEntryRepository.class), mock(SecurityEventLogger.class), 10, 4096);
        NoteExportService.NoteExportPlan plan = new NoteExportService.NoteExportPlan(
                "zip-owner",
                java.time.Instant.parse("2026-07-17T00:00:00Z"),
                List.of(new NoteExportService.NoteExportRow(
                        LocalDate.of(2026, 7, 17), "body", "Night", "🌙")),
                100L);
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        service.writeZip(plan, output);
        Map<String, String> files = unzip(output.toByteArray());

        assertTrue(files.containsKey("2026/2026-07-17.md"));
        assertTrue(files.containsKey("README.md"));
        assertTrue(files.get("2026/2026-07-17.md").contains("shift: \"Night\""));
        assertTrue(files.get("README.md").contains("Пользователь: zip-owner"));
    }

    @Test
    void yamlScalarEscapingCoversNullQuotesSlashesAndControlCharacters() {
        assertEquals("\"\"", NoteExportService.yamlString(null));
        String escaped = NoteExportService.yamlString("a\\b\"c\r\nd\te");
        assertEquals("\"a\\\\b\\\"c\\r\\nd\\te\"", escaped);
        assertFalse(escaped.contains("\r\n"));
    }

    private static DayEntry note(AppUser user, LocalDate date, String text) {
        DayEntry entry = new DayEntry(user, date);
        entry.setNote(text);
        return entry;
    }

    private static Map<String, String> unzip(byte[] body) throws Exception {
        Map<String, String> files = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(
                new ByteArrayInputStream(body), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                files.put(entry.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return files;
    }
}
