package ru.daniil.shifts.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.config.SecurityEventLogger;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.DayEntry;
import ru.daniil.shifts.repo.DayEntryRepository;
import ru.daniil.shifts.service.exception.ApiException;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Builds a bounded, portable Markdown export of the current user's notes. */
@Service
public class NoteExportService {
    private final DayEntryRepository days;
    private final SecurityEventLogger securityEvents;
    private final int maxNotes;
    private final long maxUncompressedBytes;

    public NoteExportService(
            DayEntryRepository days,
            SecurityEventLogger securityEvents,
            @Value("${dutylog.export.notes.max-count:10000}") int maxNotes,
            @Value("${dutylog.export.notes.max-uncompressed-bytes:52428800}") long maxUncompressedBytes) {
        this.days = days;
        this.securityEvents = securityEvents;
        this.maxNotes = Math.max(1, maxNotes);
        this.maxUncompressedBytes = Math.max(1024L, maxUncompressedBytes);
    }

    /**
     * Reads only owner-scoped, non-empty notes and detaches them into immutable rows.
     * Export remains available even when the Notes UI module is disabled: disabling a
     * module hides functionality, it never removes the user's right to retrieve data.
     */
    @Transactional(readOnly = true)
    public NoteExportPlan prepare(AppUser user) {
        long candidateCount = days.countByOwnerAndNoteIsNotNull(user);
        if (candidateCount > maxNotes) {
            throw ApiException.payloadTooLarge(
                    "Слишком много заметок для одного экспорта: " + candidateCount + ". Лимит: " + maxNotes);
        }

        List<NoteExportRow> rows = days.findNotesForExport(user).stream()
                .filter(entry -> entry.getNote() != null && !entry.getNote().isBlank())
                .map(this::toRow)
                .toList();
        // Keep the post-read check as well: rows may change between COUNT and SELECT.
        if (rows.size() > maxNotes) {
            throw ApiException.payloadTooLarge(
                    "Слишком много заметок для одного экспорта: " + rows.size() + ". Лимит: " + maxNotes);
        }

        long totalBytes = 0L;
        for (NoteExportRow row : rows) {
            totalBytes = safeAdd(totalBytes, markdownFor(row).getBytes(StandardCharsets.UTF_8).length);
            if (totalBytes > maxUncompressedBytes) {
                throw ApiException.payloadTooLarge(
                        "Экспорт заметок превышает безопасный лимит " + maxUncompressedBytes + " байт");
            }
        }

        NoteExportPlan plan = new NoteExportPlan(user.getUsername(), Instant.now(), rows, totalBytes);
        securityEvents.info("DATA_EXPORT_NOTES", user.getUsername(), "accepted",
                "notes=" + rows.size() + " uncompressedBytes=" + totalBytes);
        return plan;
    }

    public void writeZip(NoteExportPlan plan, OutputStream output) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            for (NoteExportRow row : plan.rows()) {
                String date = row.date().toString();
                zip.putNextEntry(new ZipEntry(row.date().getYear() + "/" + date + ".md"));
                zip.write(markdownFor(row).getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            zip.putNextEntry(new ZipEntry("README.md"));
            zip.write(readme(plan).getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
    }

    static String yamlString(String value) {
        String safe = value == null ? "" : value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
        return "\"" + safe + "\"";
    }

    private NoteExportRow toRow(DayEntry entry) {
        return new NoteExportRow(
                entry.getDate(),
                entry.getNote(),
                entry.getShiftType() == null ? null : entry.getShiftType().getName(),
                entry.getDayEmoji()
        );
    }

    private static String markdownFor(NoteExportRow row) {
        StringBuilder markdown = new StringBuilder();
        markdown.append("---\n")
                .append("date: ").append(row.date()).append('\n');
        if (row.shiftName() != null && !row.shiftName().isBlank()) {
            markdown.append("shift: ").append(yamlString(row.shiftName())).append('\n');
        }
        if (row.dayEmoji() != null && !row.dayEmoji().isBlank()) {
            markdown.append("emoji: ").append(yamlString(row.dayEmoji())).append('\n');
        }
        markdown.append("---\n\n").append(row.note());
        if (!row.note().endsWith("\n")) {
            markdown.append('\n');
        }
        return markdown.toString();
    }

    private static String readme(NoteExportPlan plan) {
        return "# Заметки DutyLog\n\n"
                + "Пользователь: " + plan.username() + "\n"
                + "Экспортировано: " + LocalDate.now() + "\n"
                + "Файлов: " + plan.rows().size() + "\n\n"
                + "Каждый файл — заметка одного дня, как она была написана.\n"
                + "Папку можно открыть как хранилище Obsidian или положить в существующее.\n";
    }

    private long safeAdd(long current, long value) {
        if (value > Long.MAX_VALUE - current) {
            throw ApiException.payloadTooLarge("Экспорт заметок слишком велик");
        }
        return current + value;
    }

    public record NoteExportRow(LocalDate date, String note, String shiftName, String dayEmoji) {}

    public record NoteExportPlan(
            String username,
            Instant generatedAt,
            List<NoteExportRow> rows,
            long uncompressedBytes
    ) {}
}
