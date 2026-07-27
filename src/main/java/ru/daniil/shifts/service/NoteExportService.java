package ru.daniil.shifts.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.config.SecurityEventLogger;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.DayNote;
import ru.daniil.shifts.repo.DayNoteRepository;
import ru.daniil.shifts.service.exception.ApiException;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Builds a bounded, portable Markdown export of all independent day notes. */
@Service
public class NoteExportService {
    private final DayNoteRepository notes;
    private final SecurityEventLogger securityEvents;
    private final int maxNotes;
    private final long maxUncompressedBytes;

    public NoteExportService(
            DayNoteRepository notes,
            SecurityEventLogger securityEvents,
            @Value("${dutylog.export.notes.max-count:10000}") int maxNotes,
            @Value("${dutylog.export.notes.max-uncompressed-bytes:52428800}") long maxUncompressedBytes) {
        this.notes = notes;
        this.securityEvents = securityEvents;
        this.maxNotes = Math.max(1, maxNotes);
        this.maxUncompressedBytes = Math.max(1024L, maxUncompressedBytes);
    }

    @Transactional(readOnly = true)
    public NoteExportPlan prepare(AppUser user) {
        long candidateCount = notes.countByOwner(user);
        if (candidateCount > maxNotes) {
            throw ApiException.payloadTooLarge(
                    "Слишком много заметок для одного экспорта: " + candidateCount + ". Лимит: " + maxNotes);
        }

        List<NoteExportRow> rows = notes.findByOwnerOrderByDateAscPinnedDescSortOrderAscCreatedAtAscIdAsc(user).stream()
                .filter(note -> (note.getContent() != null && !note.getContent().isBlank())
                        || (note.getTitle() != null && !note.getTitle().isBlank()))
                .map(this::toRow)
                .toList();
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
                String suffix = row.title() == null || row.title().isBlank()
                        ? "note-" + row.id()
                        : slug(row.title()) + "-" + row.id();
                zip.putNextEntry(new ZipEntry(row.date().getYear() + "/" + date + "-" + suffix + ".md"));
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

    private NoteExportRow toRow(DayNote note) {
        return new NoteExportRow(
                note.getId(), note.getDate(), note.getTitle(), note.getContent(), note.isPinned(), note.getSortOrder());
    }

    private static String markdownFor(NoteExportRow row) {
        StringBuilder markdown = new StringBuilder();
        markdown.append("---\n")
                .append("date: ").append(row.date()).append('\n')
                .append("dutylog_note_id: ").append(row.id()).append('\n')
                .append("pinned: ").append(row.pinned()).append('\n')
                .append("sort_order: ").append(row.sortOrder()).append('\n');
        if (row.title() != null && !row.title().isBlank()) {
            markdown.append("title: ").append(yamlString(row.title())).append('\n');
        }
        markdown.append("---\n\n");
        if (row.title() != null && !row.title().isBlank()) {
            markdown.append("# ").append(row.title()).append("\n\n");
        }
        markdown.append(row.content() == null ? "" : row.content());
        if (markdown.length() == 0 || markdown.charAt(markdown.length() - 1) != '\n') markdown.append('\n');
        return markdown.toString();
    }

    private static String readme(NoteExportPlan plan) {
        return "# Заметки DutyLog\n\n"
                + "Пользователь: " + plan.username() + "\n"
                + "Экспортировано: " + LocalDate.now() + "\n"
                + "Файлов: " + plan.rows().size() + "\n\n"
                + "Каждая независимая заметка экспортируется отдельным Markdown-файлом.\n"
                + "Папку можно открыть как хранилище Obsidian или положить в существующее.\n";
    }

    private static String slug(String title) {
        String value = title.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", "-")
                .replaceAll("^-+|-+$", "");
        if (value.isBlank()) return "note";
        return value.length() > 48 ? value.substring(0, 48) : value;
    }

    private long safeAdd(long current, long value) {
        if (value > Long.MAX_VALUE - current) {
            throw ApiException.payloadTooLarge("Экспорт заметок слишком велик");
        }
        return current + value;
    }

    public record NoteExportRow(Long id, LocalDate date, String title, String content, boolean pinned, int sortOrder) {}

    public record NoteExportPlan(
            String username,
            Instant generatedAt,
            List<NoteExportRow> rows,
            long uncompressedBytes
    ) {}
}
