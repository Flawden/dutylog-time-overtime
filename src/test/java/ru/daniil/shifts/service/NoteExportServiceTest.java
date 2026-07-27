package ru.daniil.shifts.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import ru.daniil.shifts.config.SecurityEventLogger;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.DayNote;
import ru.daniil.shifts.repo.DayNoteRepository;
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
        DayNoteRepository repository = mock(DayNoteRepository.class);
        AppUser user = new AppUser("export-limit", "{noop}x");
        when(repository.countByOwner(user)).thenReturn(3L);

        NoteExportService service = new NoteExportService(repository, mock(SecurityEventLogger.class), 2, 4096);
        ApiException error = assertThrows(ApiException.class, () -> service.prepare(user));

        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, error.getStatus());
        verify(repository, never()).findByOwnerOrderByDateAscPinnedDescSortOrderAscCreatedAtAscIdAsc(user);
    }

    @Test
    void uncompressedByteLimitRejectsOversizedExport() {
        DayNoteRepository repository = mock(DayNoteRepository.class);
        AppUser user = new AppUser("export-bytes", "{noop}x");
        DayNote note = note(user, LocalDate.of(2026, 7, 10), "x".repeat(2048));
        when(repository.countByOwner(user)).thenReturn(1L);
        when(repository.findByOwnerOrderByDateAscPinnedDescSortOrderAscCreatedAtAscIdAsc(user)).thenReturn(List.of(note));

        NoteExportService service = new NoteExportService(repository, mock(SecurityEventLogger.class), 10, 1024);
        ApiException error = assertThrows(ApiException.class, () -> service.prepare(user));

        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, error.getStatus());
    }

    @Test
    void postReadLimitProtectsAgainstRowsChangingBetweenCountAndSelect() {
        DayNoteRepository repository = mock(DayNoteRepository.class);
        AppUser user = new AppUser("export-race", "{noop}x");
        DayNote first = note(user, LocalDate.of(2026, 7, 1), "one");
        DayNote second = note(user, LocalDate.of(2026, 7, 2), "two");
        when(repository.countByOwner(user)).thenReturn(1L);
        when(repository.findByOwnerOrderByDateAscPinnedDescSortOrderAscCreatedAtAscIdAsc(user)).thenReturn(List.of(first, second));

        ApiException error = assertThrows(ApiException.class,
                () -> new NoteExportService(repository, mock(SecurityEventLogger.class), 1, 4096)
                        .prepare(user));

        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, error.getStatus());
    }

    @Test
    void blankRowsAreFilteredAndSuccessfulPlanIsAudited() {
        DayNoteRepository repository = mock(DayNoteRepository.class);
        SecurityEventLogger audit = mock(SecurityEventLogger.class);
        AppUser user = new AppUser("export-plan", "{noop}x");
        DayNote blank = note(user, LocalDate.of(2026, 7, 1), "   \n");
        DayNote actual = note(user, LocalDate.of(2026, 7, 2), "hello");
        actual.setTitle("Plan");
        when(repository.countByOwner(user)).thenReturn(2L);
        when(repository.findByOwnerOrderByDateAscPinnedDescSortOrderAscCreatedAtAscIdAsc(user)).thenReturn(List.of(blank, actual));

        NoteExportService.NoteExportPlan plan =
                new NoteExportService(repository, audit, 10, 4096).prepare(user);

        assertEquals(1, plan.rows().size());
        assertEquals(LocalDate.of(2026, 7, 2), plan.rows().get(0).date());
        assertEquals("Plan", plan.rows().get(0).title());
        assertTrue(plan.uncompressedBytes() > 0);
        verify(audit).info(eq("DATA_EXPORT_NOTES"), eq("export-plan"), eq("accepted"), anyString());
    }

    @Test
    void zipWriterCreatesOneFilePerNoteAndReadme() throws Exception {
        NoteExportService service = new NoteExportService(
                mock(DayNoteRepository.class), mock(SecurityEventLogger.class), 10, 4096);
        NoteExportService.NoteExportPlan plan = new NoteExportService.NoteExportPlan(
                "zip-owner",
                java.time.Instant.parse("2026-07-17T00:00:00Z"),
                List.of(new NoteExportService.NoteExportRow(
                        42L, LocalDate.of(2026, 7, 17), "Night", "body", true, 0)),
                100L);
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        service.writeZip(plan, output);
        Map<String, String> files = unzip(output.toByteArray());

        assertTrue(files.containsKey("2026/2026-07-17-night-42.md"));
        assertTrue(files.containsKey("README.md"));
        assertTrue(files.get("2026/2026-07-17-night-42.md").contains("title: \"Night\""));
        assertTrue(files.get("2026/2026-07-17-night-42.md").contains("pinned: true"));
        assertTrue(files.get("README.md").contains("Пользователь: zip-owner"));
    }

    @Test
    void yamlScalarEscapingCoversNullQuotesSlashesAndControlCharacters() {
        assertEquals("\"\"", NoteExportService.yamlString(null));
        String escaped = NoteExportService.yamlString("a\\b\"c\r\nd\te");
        assertEquals("\"a\\\\b\\\"c\\r\\nd\\te\"", escaped);
        assertFalse(escaped.contains("\r\n"));
    }

    private static DayNote note(AppUser user, LocalDate date, String text) {
        return new DayNote(user, date, text);
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
