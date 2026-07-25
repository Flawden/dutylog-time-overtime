package ru.daniil.shifts.web;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import ru.daniil.shifts.dto.Dtos.CalendarRangeDto;
import ru.daniil.shifts.dto.Dtos.MobileUserDto;
import ru.daniil.shifts.dto.Dtos.MobileV1BootstrapDto;
import ru.daniil.shifts.dto.Dtos.MobileV1SyncRequest;
import ru.daniil.shifts.dto.Dtos.MobileV1SyncResultDto;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.service.CalendarService;
import ru.daniil.shifts.service.CurrentUserService;
import ru.daniil.shifts.service.DayEntryService;
import ru.daniil.shifts.service.MobileSyncService;

import java.security.Principal;
import java.time.Instant;
import java.time.LocalDate;

/** Stable Android API v1. Existing v1 fields are additive-only. */
@RestController
@RequestMapping("/api/v1/mobile")
public class MobileV1Controller {
    private final CurrentUserService currentUserService;
    private final DayEntryService dayEntryService;
    private final CalendarService calendarService;
    private final MobileSyncService mobileSyncService;

    public MobileV1Controller(CurrentUserService currentUserService,
                              DayEntryService dayEntryService,
                              CalendarService calendarService,
                              MobileSyncService mobileSyncService) {
        this.currentUserService = currentUserService;
        this.dayEntryService = dayEntryService;
        this.calendarService = calendarService;
        this.mobileSyncService = mobileSyncService;
    }

    @GetMapping("/bootstrap")
    public MobileV1BootstrapDto bootstrap(@RequestParam("from") String from,
                                          @RequestParam("to") String to,
                                          Principal principal) {
        AppUser current = currentUserService.requireUser(principal);
        LocalDate fromDate = dayEntryService.parseDate(from, "Дата from должна быть в формате yyyy-MM-dd");
        LocalDate toDate = dayEntryService.parseDate(to, "Дата to должна быть в формате yyyy-MM-dd");
        CalendarRangeDto calendar = calendarService.range(current, fromDate, toDate);
        return new MobileV1BootstrapDto(
                MobileSyncService.API_VERSION,
                Instant.now().toString(),
                MobileUserDto.from(current),
                calendar
        );
    }

    @PostMapping("/sync")
    public MobileV1SyncResultDto sync(@Valid @RequestBody(required = false) MobileV1SyncRequest request,
                                      Principal principal) {
        AppUser current = currentUserService.requireUser(principal);
        return mobileSyncService.sync(current, request);
    }
}
