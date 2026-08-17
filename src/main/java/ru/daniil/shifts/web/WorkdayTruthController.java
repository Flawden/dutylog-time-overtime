package ru.daniil.shifts.web;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.daniil.shifts.dto.Dtos.WorkdayTruthDto;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.service.CurrentUserService;
import ru.daniil.shifts.service.DayEntryService;
import ru.daniil.shifts.service.ModuleService;
import ru.daniil.shifts.service.WorkdayTruthService;

import java.security.Principal;
import java.time.LocalDate;

/** Native Calendar/Today read model: the user describes reality once, domains stay internal. */
@RestController
@RequestMapping({"/api/workdays", "/api/v1/workdays"})
public class WorkdayTruthController {
    private final CurrentUserService users;
    private final ModuleService modules;
    private final DayEntryService dates;
    private final WorkdayTruthService workdays;

    public WorkdayTruthController(CurrentUserService users, ModuleService modules,
                                  DayEntryService dates, WorkdayTruthService workdays) {
        this.users = users;
        this.modules = modules;
        this.dates = dates;
        this.workdays = workdays;
    }

    @GetMapping("/{date}")
    public ResponseEntity<WorkdayTruthDto> day(@PathVariable("date") String date, Principal principal) {
        AppUser user = users.requireUser(principal);
        modules.requireEnabled(user, ModuleService.SHIFTS);
        LocalDate parsed = dates.parseDate(date, "Дата должна быть в формате yyyy-MM-dd");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(workdays.truth(user, parsed));
    }
}
