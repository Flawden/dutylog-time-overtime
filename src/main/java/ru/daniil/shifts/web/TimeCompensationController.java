package ru.daniil.shifts.web;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.daniil.shifts.dto.Dtos.TimeCompensationSummaryDto;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.service.CurrentUserService;
import ru.daniil.shifts.service.DayEntryService;
import ru.daniil.shifts.service.ModuleService;
import ru.daniil.shifts.service.TimeCompensationService;

import java.security.Principal;
import java.time.LocalDate;

@RestController
@RequestMapping({"/api/time-compensation", "/api/v1/time-compensation"})
public class TimeCompensationController {
    private final CurrentUserService currentUserService;
    private final ModuleService moduleService;
    private final DayEntryService dayEntryService;
    private final TimeCompensationService service;

    public TimeCompensationController(CurrentUserService currentUserService,
                                      ModuleService moduleService,
                                      DayEntryService dayEntryService,
                                      TimeCompensationService service) {
        this.currentUserService = currentUserService;
        this.moduleService = moduleService;
        this.dayEntryService = dayEntryService;
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<TimeCompensationSummaryDto> summary(@RequestParam("from") String from,
                                                               @RequestParam("to") String to,
                                                               Principal principal) {
        AppUser user = currentUserService.requireUser(principal);
        moduleService.requireEnabled(user, ModuleService.OVERTIME);
        LocalDate rangeFrom = dayEntryService.parseDate(from, "Дата from должна быть в формате yyyy-MM-dd");
        LocalDate rangeTo = dayEntryService.parseDate(to, "Дата to должна быть в формате yyyy-MM-dd");
        dayEntryService.validateRange(rangeFrom, rangeTo);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.summary(user, rangeFrom, rangeTo));
    }
}
