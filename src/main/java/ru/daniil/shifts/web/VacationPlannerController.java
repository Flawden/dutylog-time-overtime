package ru.daniil.shifts.web;

import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.daniil.shifts.dto.Dtos.*;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.service.CurrentUserService;
import ru.daniil.shifts.service.DayEntryService;
import ru.daniil.shifts.service.ModuleService;
import ru.daniil.shifts.service.VacationPlannerService;

import java.security.Principal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping({"/api/vacation-planner", "/api/v1/vacation-planner"})
public class VacationPlannerController {
    private final CurrentUserService currentUserService;
    private final ModuleService moduleService;
    private final VacationPlannerService plannerService;
    private final DayEntryService dayEntryService;

    public VacationPlannerController(CurrentUserService currentUserService,
                                     ModuleService moduleService,
                                     VacationPlannerService plannerService,
                                     DayEntryService dayEntryService) {
        this.currentUserService = currentUserService;
        this.moduleService = moduleService;
        this.plannerService = plannerService;
        this.dayEntryService = dayEntryService;
    }

    @GetMapping
    public ResponseEntity<VacationPlannerDto> planner(@RequestParam(value = "referenceDate", required = false) String referenceDate,
                                                      @RequestParam(value = "from", required = false) String from,
                                                      @RequestParam(value = "to", required = false) String to,
                                                      Principal principal) {
        AppUser user = user(principal);
        LocalDate reference = parseOptional(referenceDate, "referenceDate должна быть в формате yyyy-MM-dd");
        LocalDate rangeFrom = parseOptional(from, "Дата from должна быть в формате yyyy-MM-dd");
        LocalDate rangeTo = parseOptional(to, "Дата to должна быть в формате yyyy-MM-dd");
        if ((rangeFrom == null) != (rangeTo == null)) {
            throw ru.daniil.shifts.service.exception.ApiException.badRequest("Параметры from и to задаются вместе");
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(plannerService.planner(user, reference, rangeFrom, rangeTo));
    }

    @PatchMapping("/settings")
    public VacationSettingsDto updateSettings(@Valid @RequestBody(required = false) VacationSettingsUpdateRequest req,
                                               Principal principal) {
        return plannerService.updateSettings(user(principal), req);
    }

    @GetMapping("/types")
    public List<AbsenceTypeDto> types(Principal principal) {
        return plannerService.types(user(principal));
    }

    @PostMapping("/types")
    public ResponseEntity<AbsenceTypeDto> createType(@Valid @RequestBody(required = false) AbsenceTypeCreateRequest req,
                                                     Principal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(plannerService.createType(user(principal), req));
    }

    @PatchMapping("/types/{id}")
    public AbsenceTypeDto updateType(@PathVariable("id") Long id,
                                     @Valid @RequestBody(required = false) AbsenceTypeUpdateRequest req,
                                     Principal principal) {
        return plannerService.updateType(user(principal), id, req);
    }

    @DeleteMapping("/types/{id}")
    public ResponseEntity<Void> deleteType(@PathVariable("id") Long id, Principal principal) {
        plannerService.deleteType(user(principal), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/preview")
    public AbsencePreviewDto preview(@Valid @RequestBody(required = false) AbsencePreviewRequest req,
                                     Principal principal) {
        return plannerService.preview(user(principal), req);
    }

    @PostMapping("/absences")
    public ResponseEntity<AbsencePeriodDto> createAbsence(@Valid @RequestBody(required = false) AbsencePeriodCreateRequest req,
                                                          Principal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(plannerService.createPeriod(user(principal), req));
    }

    @PatchMapping("/absences/{id}")
    public AbsencePeriodDto updateAbsence(@PathVariable("id") Long id,
                                          @Valid @RequestBody(required = false) AbsencePeriodUpdateRequest req,
                                          Principal principal) {
        return plannerService.updatePeriod(user(principal), id, req);
    }

    @DeleteMapping("/absences/{id}")
    public ResponseEntity<Void> deleteAbsence(@PathVariable("id") Long id, Principal principal) {
        plannerService.deletePeriod(user(principal), id);
        return ResponseEntity.noContent().build();
    }

    private AppUser user(Principal principal) {
        AppUser user = currentUserService.requireUser(principal);
        moduleService.requireEnabled(user, ModuleService.VACATION);
        return user;
    }

    private LocalDate parseOptional(String value, String message) {
        return value == null || value.isBlank() ? null : dayEntryService.parseDate(value, message);
    }
}
