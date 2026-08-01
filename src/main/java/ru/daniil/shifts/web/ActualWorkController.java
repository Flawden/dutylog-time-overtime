package ru.daniil.shifts.web;

import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.daniil.shifts.dto.Dtos.ActualWorkIntervalDto;
import ru.daniil.shifts.dto.Dtos.ActualWorkIntervalRequest;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.service.*;

import java.security.Principal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping({"/api/actual-work", "/api/v1/actual-work"})
public class ActualWorkController {
    private final CurrentUserService users;
    private final ModuleService modules;
    private final DayEntryService dates;
    private final ActualWorkService actualWork;

    public ActualWorkController(CurrentUserService users, ModuleService modules,
                                DayEntryService dates, ActualWorkService actualWork) {
        this.users = users;
        this.modules = modules;
        this.dates = dates;
        this.actualWork = actualWork;
    }

    @GetMapping
    public ResponseEntity<List<ActualWorkIntervalDto>> list(@RequestParam("from") String from,
                                                            @RequestParam("to") String to,
                                                            Principal principal) {
        AppUser user = user(principal);
        LocalDate rangeFrom = dates.parseDate(from, "Дата from должна быть в формате yyyy-MM-dd");
        LocalDate rangeTo = dates.parseDate(to, "Дата to должна быть в формате yyyy-MM-dd");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(actualWork.list(user, rangeFrom, rangeTo));
    }

    @PostMapping
    public ResponseEntity<ActualWorkIntervalDto> create(
            @Valid @RequestBody(required = false) ActualWorkIntervalRequest request,
            Principal principal) {
        return ResponseEntity.status(201).body(actualWork.create(user(principal), request));
    }

    @PutMapping("/{id}")
    public ActualWorkIntervalDto update(@PathVariable("id") Long id,
                                        @Valid @RequestBody(required = false) ActualWorkIntervalRequest request,
                                        Principal principal) {
        return actualWork.update(user(principal), id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id, Principal principal) {
        actualWork.delete(user(principal), id);
        return ResponseEntity.noContent().build();
    }

    private AppUser user(Principal principal) {
        AppUser user = users.requireUser(principal);
        modules.requireEnabled(user, ModuleService.OVERTIME);
        return user;
    }
}
