package ru.daniil.shifts.web;

import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import ru.daniil.shifts.dto.Dtos.CalendarSubscriptionDto;
import ru.daniil.shifts.dto.Dtos.CalendarSyncStatusDto;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.service.CalendarIcsService;
import ru.daniil.shifts.service.CalendarSubscriptionService;
import ru.daniil.shifts.service.CurrentUserService;
import ru.daniil.shifts.service.DayEntryService;
import ru.daniil.shifts.service.ModuleService;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.LocalDate;

/** Authenticated .ics export and private subscription management. */
@RestController
@RequestMapping({"/api/calendar-sync", "/api/v1/calendar-sync"})
public class CalendarSyncController {
    private static final MediaType TEXT_CALENDAR = MediaType.parseMediaType("text/calendar;charset=UTF-8");

    private final CurrentUserService currentUserService;
    private final ModuleService moduleService;
    private final DayEntryService dayEntryService;
    private final CalendarIcsService calendarIcsService;
    private final CalendarSubscriptionService subscriptionService;

    public CalendarSyncController(CurrentUserService currentUserService,
                                  ModuleService moduleService,
                                  DayEntryService dayEntryService,
                                  CalendarIcsService calendarIcsService,
                                  CalendarSubscriptionService subscriptionService) {
        this.currentUserService = currentUserService;
        this.moduleService = moduleService;
        this.dayEntryService = dayEntryService;
        this.calendarIcsService = calendarIcsService;
        this.subscriptionService = subscriptionService;
    }

    @GetMapping("/status")
    public ResponseEntity<CalendarSyncStatusDto> status(Principal principal) {
        AppUser user = requireEnabledUser(principal);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(subscriptionService.status(user));
    }

    @PostMapping("/subscription")
    public ResponseEntity<CalendarSubscriptionDto> issue(Principal principal) {
        AppUser user = requireEnabledUser(principal);
        CalendarSubscriptionService.IssueResult result = subscriptionService.issue(user);
        String url = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/calendar-feed.ics")
                .queryParam("token", result.rawToken())
                .build(true)
                .toUriString();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(subscriptionService.toIssuedDto(result, url));
    }

    @DeleteMapping("/subscription")
    public ResponseEntity<Void> revoke(Principal principal) {
        AppUser user = requireEnabledUser(principal);
        subscriptionService.revoke(user);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @GetMapping(value = "/export", produces = "text/calendar")
    public ResponseEntity<byte[]> exportRange(@RequestParam("from") String from,
                                              @RequestParam("to") String to,
                                              Principal principal) {
        AppUser user = requireEnabledUser(principal);
        LocalDate fromDate = dayEntryService.parseDate(from, "Дата from должна быть в формате yyyy-MM-dd");
        LocalDate toDate = dayEntryService.parseDate(to, "Дата to должна быть в формате yyyy-MM-dd");
        CalendarIcsService.IcsExport export = calendarIcsService.exportRange(user, fromDate, toDate);
        String filename = "dutylog-calendar-" + fromDate + "-" + toDate + ".ics";
        return calendarResponse(export.bytes(), filename, true);
    }

    @GetMapping(value = "/events/{id}.ics", produces = "text/calendar")
    public ResponseEntity<byte[]> exportImportantEvent(@PathVariable("id") Long id, Principal principal) {
        AppUser user = requireEnabledUser(principal);
        moduleService.requireEnabled(user, ModuleService.IMPORTANT_DATES);
        CalendarIcsService.IcsExport export = calendarIcsService.exportImportantEvent(user, id);
        return calendarResponse(export.bytes(), "dutylog-event-" + id + ".ics", true);
    }

    private AppUser requireEnabledUser(Principal principal) {
        AppUser user = currentUserService.requireUser(principal);
        moduleService.requireEnabled(user, ModuleService.CALENDAR_SYNC);
        return user;
    }

    static ResponseEntity<byte[]> calendarResponse(byte[] bytes, String filename, boolean attachment) {
        ContentDisposition disposition = attachment
                ? ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build()
                : ContentDisposition.inline().filename(filename, StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header(HttpHeaders.EXPIRES, "0")
                .cacheControl(CacheControl.noStore().mustRevalidate())
                .contentType(TEXT_CALENDAR)
                .contentLength(bytes.length)
                .body(bytes);
    }
}
