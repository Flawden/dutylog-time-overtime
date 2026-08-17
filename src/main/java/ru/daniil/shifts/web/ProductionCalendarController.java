package ru.daniil.shifts.web;

import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.daniil.shifts.dto.Dtos.ProductionCalendarDayDto;
import ru.daniil.shifts.dto.Dtos.ProductionCalendarDayUpdateRequest;
import ru.daniil.shifts.dto.Dtos.ProductionCalendarMonthDto;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.service.CurrentUserService;
import ru.daniil.shifts.service.ModuleService;
import ru.daniil.shifts.service.ProductionCalendarService;

import java.security.Principal;

/** Production work-norm calendar; independent from absence and money calculation semantics. */
@RestController
@RequestMapping({"/api/production-calendar", "/api/v1/production-calendar"})
public class ProductionCalendarController {
    private final CurrentUserService users;
    private final ModuleService modules;
    private final ProductionCalendarService calendar;

    public ProductionCalendarController(CurrentUserService users, ModuleService modules,
                                        ProductionCalendarService calendar) {
        this.users = users;
        this.modules = modules;
        this.calendar = calendar;
    }

    @GetMapping("/months/{month}")
    public ResponseEntity<ProductionCalendarMonthDto> month(@PathVariable("month") String month,
                                                            Principal principal) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(calendar.month(user(principal), month));
    }

    @PutMapping("/days/{date}")
    public ProductionCalendarDayDto upsert(@PathVariable("date") String date,
                                           @Valid @RequestBody(required = false) ProductionCalendarDayUpdateRequest request,
                                           Principal principal) {
        return calendar.upsertLocal(user(principal), date, request);
    }

    @DeleteMapping("/days/{date}")
    public ResponseEntity<Void> delete(@PathVariable("date") String date, Principal principal) {
        calendar.deleteLocal(user(principal), date);
        return ResponseEntity.noContent().build();
    }

    private AppUser user(Principal principal) {
        AppUser user = users.requireUser(principal);
        modules.requireEnabled(user, ModuleService.SHIFTS);
        return user;
    }
}
