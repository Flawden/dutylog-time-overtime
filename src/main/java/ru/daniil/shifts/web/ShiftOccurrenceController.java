package ru.daniil.shifts.web;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.daniil.shifts.dto.Dtos.LegacyShiftMigrationPreviewDto;
import ru.daniil.shifts.dto.Dtos.LegacyShiftMigrationRequest;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.service.CurrentUserService;
import ru.daniil.shifts.service.ShiftOccurrenceService;

import java.security.Principal;

@RestController
@RequestMapping({"/api/shifts/legacy-migration", "/api/v1/shifts/legacy-migration"})
public class ShiftOccurrenceController {
    private final CurrentUserService currentUserService;
    private final ShiftOccurrenceService shiftOccurrenceService;

    public ShiftOccurrenceController(CurrentUserService currentUserService,
                                     ShiftOccurrenceService shiftOccurrenceService) {
        this.currentUserService = currentUserService;
        this.shiftOccurrenceService = shiftOccurrenceService;
    }

    @GetMapping("/preview")
    public LegacyShiftMigrationPreviewDto preview(@RequestParam("sourceTimezone") String sourceTimezone,
                                                  Principal principal) {
        AppUser user = currentUserService.requireUser(principal);
        return shiftOccurrenceService.preview(user, sourceTimezone);
    }

    @PostMapping
    public LegacyShiftMigrationPreviewDto migrate(@Valid @RequestBody LegacyShiftMigrationRequest request,
                                                  Principal principal) {
        AppUser user = currentUserService.requireUser(principal);
        return shiftOccurrenceService.migrate(user, request);
    }
}
