package ru.daniil.shifts.web;

import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.service.CurrentUserService;
import ru.daniil.shifts.service.NoteExportService;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/** Owner-scoped export endpoints. */
@RestController
@RequestMapping("/api/export")
public class ExportController {
    private final NoteExportService noteExportService;
    private final CurrentUserService currentUserService;

    public ExportController(NoteExportService noteExportService,
                            CurrentUserService currentUserService) {
        this.noteExportService = noteExportService;
        this.currentUserService = currentUserService;
    }

    @GetMapping(value = "/notes", produces = "application/zip")
    public ResponseEntity<StreamingResponseBody> exportNotes(Principal principal) {
        AppUser user = currentUserService.requireUser(principal);
        NoteExportService.NoteExportPlan plan = noteExportService.prepare(user);
        String filename = "dutylog-notes-"
                + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".zip";

        StreamingResponseBody body = output -> noteExportService.writeZip(plan, output);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(filename, StandardCharsets.UTF_8)
                                .build().toString())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header(HttpHeaders.EXPIRES, "0")
                .cacheControl(CacheControl.noStore().mustRevalidate())
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(body);
    }
}
