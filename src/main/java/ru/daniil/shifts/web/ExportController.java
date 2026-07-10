package ru.daniil.shifts.web;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.DayEntry;
import ru.daniil.shifts.repo.DayEntryRepository;
import ru.daniil.shifts.service.CurrentUserService;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Экспорт заметок пользователя: ZIP из markdown-файлов вида 2026/2026-07-03.md.
 *
 * Зачем: данные принадлежат пользователю, а не приложению. Один клик —
 * и весь дневник у тебя на диске, в формате, который понимает Obsidian
 * и переживёт любое приложение, включая это.
 *
 * Содержимое файла: YAML front matter (дата, смена, эмодзи — Obsidian
 * показывает их как свойства) + заметка БЕЗ каких-либо изменений.
 * Правило экспорта: данные отдаются как есть, экспорт ничего не «улучшает».
 */
@RestController
@RequestMapping("/api/export")
public class ExportController {

    private final DayEntryRepository days;
    private final CurrentUserService currentUserService;

    public ExportController(DayEntryRepository days, CurrentUserService currentUserService) {
        this.days = days;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/notes")
    public ResponseEntity<byte[]> exportNotes(Principal principal) {
        AppUser user = currentUserService.requireUser(principal);

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int count = 0;
        try (ZipOutputStream zip = new ZipOutputStream(buffer, StandardCharsets.UTF_8)) {
            for (DayEntry entry : days.findByOwnerOrderByDateAsc(user)) {
                String note = entry.getNote();
                if (note == null || note.isBlank()) continue;

                String date = entry.getDate().toString(); // yyyy-MM-dd
                zip.putNextEntry(new ZipEntry(entry.getDate().getYear() + "/" + date + ".md"));
                zip.write(markdownFor(entry, date).getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
                count++;
            }
            zip.putNextEntry(new ZipEntry("README.md"));
            zip.write(readme(user, count).getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        String filename = "dutylog-notes-" +
                LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".zip";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(buffer.toByteArray());
    }

    private static String markdownFor(DayEntry entry, String date) {
        StringBuilder sb = new StringBuilder();
        sb.append("---\n").append("date: ").append(date).append('\n');
        if (entry.getShiftType() != null) {
            sb.append("shift: \"").append(entry.getShiftType().getName().replace("\"", "'")).append("\"\n");
        }
        if (entry.getDayEmoji() != null && !entry.getDayEmoji().isBlank()) {
            sb.append("emoji: \"").append(entry.getDayEmoji()).append("\"\n");
        }
        sb.append("---\n\n");
        sb.append(entry.getNote());
        if (!entry.getNote().endsWith("\n")) sb.append('\n');
        return sb.toString();
    }

    private static String readme(AppUser user, int count) {
        return "# Заметки DutyLog\n\n"
                + "Пользователь: " + user.getUsername() + "\n"
                + "Экспортировано: " + LocalDate.now() + "\n"
                + "Файлов: " + count + "\n\n"
                + "Каждый файл — заметка одного дня, как она была написана.\n"
                + "Папку можно открыть как хранилище Obsidian или положить в существующее.\n";
    }
}
