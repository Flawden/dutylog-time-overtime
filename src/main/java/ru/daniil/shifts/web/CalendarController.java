package ru.daniil.shifts.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.daniil.shifts.dto.Dtos.CalendarRangeDto;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.service.CalendarService;
import ru.daniil.shifts.service.CurrentUserService;
import ru.daniil.shifts.service.DayEntryService;

import java.security.Principal;
import java.time.LocalDate;

/**
 * Android/PWA-friendly API: одним запросом отдаёт диапазон календаря,
 * типы смен и сводку переработок.
 */
@RestController
@RequestMapping({"/api/calendar", "/api/v1/calendar"})
public class CalendarController {
    private final CurrentUserService currentUserService;
    private final DayEntryService dayEntryService;
    private final CalendarService calendarService;

    public CalendarController(CurrentUserService currentUserService,
                              DayEntryService dayEntryService,
                              CalendarService calendarService) {
        this.currentUserService = currentUserService;
        this.dayEntryService = dayEntryService;
        this.calendarService = calendarService;
    }

    /**
     * GET /api/calendar?from=2026-06-01&to=2026-07-31
     */
    @GetMapping
    public CalendarRangeDto range(@RequestParam("from") String from,
                                  @RequestParam("to") String to,
                                  Principal principal) {
        AppUser current = currentUserService.requireUser(principal);
        LocalDate fromDate = dayEntryService.parseDate(from, "Дата from должна быть в формате yyyy-MM-dd");
        LocalDate toDate = dayEntryService.parseDate(to, "Дата to должна быть в формате yyyy-MM-dd");
        return calendarService.range(current, fromDate, toDate);
    }
}
