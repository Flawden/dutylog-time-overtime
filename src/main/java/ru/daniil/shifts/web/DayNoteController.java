package ru.daniil.shifts.web;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.daniil.shifts.dto.Dtos.DayNoteCreateRequest;
import ru.daniil.shifts.dto.Dtos.DayNoteDto;
import ru.daniil.shifts.dto.Dtos.DayNoteMoveRequest;
import ru.daniil.shifts.dto.Dtos.DayNoteUpdateRequest;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.service.CurrentUserService;
import ru.daniil.shifts.service.DayNoteService;
import ru.daniil.shifts.service.ModuleService;

import java.net.URI;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping({"/api/notes", "/api/v1/notes"})
public class DayNoteController {
    private final CurrentUserService currentUserService;
    private final DayNoteService notes;
    private final ModuleService modules;

    public DayNoteController(CurrentUserService currentUserService, DayNoteService notes, ModuleService modules) {
        this.currentUserService = currentUserService;
        this.notes = notes;
        this.modules = modules;
    }

    @GetMapping
    public List<DayNoteDto> list(@RequestParam(value = "date", required = false) String date,
                                 @RequestParam(value = "from", required = false) String from,
                                 @RequestParam(value = "to", required = false) String to,
                                 Principal principal) {
        AppUser user = requireNotes(principal);
        if (date != null && !date.isBlank()) return notes.listDate(user, parseDate(date));
        if (from == null || to == null) throw ru.daniil.shifts.service.exception.ApiException.badRequest("Нужна date или пара from/to");
        return notes.listRange(user, parseDate(from), parseDate(to));
    }

    @PostMapping
    public ResponseEntity<DayNoteDto> create(@Valid @RequestBody(required = false) DayNoteCreateRequest request,
                                              Principal principal) {
        DayNoteDto created = notes.create(requireNotes(principal), request);
        return ResponseEntity.created(URI.create("/api/notes/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    public DayNoteDto update(@PathVariable Long id,
                             @Valid @RequestBody(required = false) DayNoteUpdateRequest request,
                             Principal principal) {
        return notes.update(requireNotes(principal), id, request);
    }

    @PostMapping("/{id}/move")
    public List<DayNoteDto> move(@PathVariable Long id,
                                 @Valid @RequestBody(required = false) DayNoteMoveRequest request,
                                 Principal principal) {
        return notes.move(requireNotes(principal), id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, Principal principal) {
        notes.delete(requireNotes(principal), id);
        return ResponseEntity.noContent().build();
    }

    private LocalDate parseDate(String value) {
        try { return LocalDate.parse(value); }
        catch (java.time.format.DateTimeParseException | NullPointerException ex) {
            throw ru.daniil.shifts.service.exception.ApiException.badRequest("Дата должна быть в формате yyyy-MM-dd");
        }
    }

    private AppUser requireNotes(Principal principal) {
        AppUser user = currentUserService.requireUser(principal);
        modules.requireEnabled(user, ModuleService.NOTES);
        return user;
    }
}
