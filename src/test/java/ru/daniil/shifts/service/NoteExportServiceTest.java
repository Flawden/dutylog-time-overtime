package ru.daniil.shifts.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.DayEntry;
import ru.daniil.shifts.repo.DayEntryRepository;
import ru.daniil.shifts.service.exception.ApiException;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

        NoteExportService service = new NoteExportService(repository, mock(ru.daniil.shifts.config.SecurityEventLogger.class), 2, 4096);
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

        NoteExportService service = new NoteExportService(repository, mock(ru.daniil.shifts.config.SecurityEventLogger.class), 10, 1024);
        ApiException error = assertThrows(ApiException.class, () -> service.prepare(user));

        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, error.getStatus());
    }
}
