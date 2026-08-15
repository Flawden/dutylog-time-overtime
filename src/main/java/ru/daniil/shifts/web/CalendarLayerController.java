package ru.daniil.shifts.web;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.daniil.shifts.dto.Dtos.CalendarLayerCreateRequest;
import ru.daniil.shifts.dto.Dtos.CalendarLayerDto;
import ru.daniil.shifts.dto.Dtos.CalendarLayerOverrideDto;
import ru.daniil.shifts.dto.Dtos.CalendarLayerOverrideRequest;
import ru.daniil.shifts.dto.Dtos.CalendarLayerUpdateRequest;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.service.CalendarLayerService;
import ru.daniil.shifts.service.CurrentUserService;
import ru.daniil.shifts.service.ModuleService;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping({"/api/calendar-layers", "/api/v1/calendar-layers"})
public class CalendarLayerController {
    private final CurrentUserService currentUserService;
    private final ModuleService moduleService;
    private final CalendarLayerService layerService;

    public CalendarLayerController(CurrentUserService currentUserService,
                                   ModuleService moduleService,
                                   CalendarLayerService layerService) {
        this.currentUserService = currentUserService;
        this.moduleService = moduleService;
        this.layerService = layerService;
    }

    @GetMapping
    public List<CalendarLayerDto> list(Principal principal) {
        AppUser user = currentUserService.requireUser(principal);
        moduleService.requireEnabled(user, ModuleService.CALENDAR);
        return layerService.list(user);
    }

    @PostMapping
    public ResponseEntity<CalendarLayerDto> create(@Valid @RequestBody(required = false) CalendarLayerCreateRequest req,
                                                   Principal principal) {
        AppUser user = currentUserService.requireUser(principal);
        moduleService.requireEnabled(user, ModuleService.CALENDAR);
        return ResponseEntity.status(HttpStatus.CREATED).body(layerService.create(user, req));
    }

    @PatchMapping("/{id}")
    public CalendarLayerDto update(@PathVariable("id") Long id,
                                   @Valid @RequestBody(required = false) CalendarLayerUpdateRequest req,
                                   Principal principal) {
        AppUser user = currentUserService.requireUser(principal);
        moduleService.requireEnabled(user, ModuleService.CALENDAR);
        return layerService.update(user, id, req);
    }

    @PutMapping("/{id}/overrides/{date}")
    public CalendarLayerOverrideDto upsertOverride(@PathVariable("id") Long id,
                                                   @PathVariable("date") String date,
                                                   @Valid @RequestBody(required = false) CalendarLayerOverrideRequest req,
                                                   Principal principal) {
        AppUser user = currentUserService.requireUser(principal);
        moduleService.requireEnabled(user, ModuleService.CALENDAR);
        return layerService.upsertOverride(user, id, date, req);
    }

    @DeleteMapping("/{id}/overrides/{date}")
    public ResponseEntity<Void> deleteOverride(@PathVariable("id") Long id,
                                               @PathVariable("date") String date,
                                               Principal principal) {
        AppUser user = currentUserService.requireUser(principal);
        moduleService.requireEnabled(user, ModuleService.CALENDAR);
        layerService.deleteOverride(user, id, date);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id, Principal principal) {
        AppUser user = currentUserService.requireUser(principal);
        moduleService.requireEnabled(user, ModuleService.CALENDAR);
        layerService.delete(user, id);
        return ResponseEntity.noContent().build();
    }
}
