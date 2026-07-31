package ru.daniil.shifts.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.service.CalendarIcsService;
import ru.daniil.shifts.service.CalendarSubscriptionService;
import ru.daniil.shifts.service.UserTimeService;

/** Token-authenticated read-only iCalendar feed for external calendar clients. */
@RestController
public class CalendarFeedController {
    private final CalendarSubscriptionService subscriptionService;
    private final CalendarIcsService calendarIcsService;
    private final UserTimeService userTimeService;

    public CalendarFeedController(CalendarSubscriptionService subscriptionService,
                                  CalendarIcsService calendarIcsService,
                                  UserTimeService userTimeService) {
        this.subscriptionService = subscriptionService;
        this.calendarIcsService = calendarIcsService;
        this.userTimeService = userTimeService;
    }

    @GetMapping(value = "/calendar-feed.ics", produces = "text/calendar")
    public ResponseEntity<byte[]> feed(@RequestParam("token") String token) {
        AppUser user = subscriptionService.resolveOwner(token);
        CalendarSubscriptionService.DateRange range = subscriptionService.feedRange(userTimeService.today(user));
        CalendarIcsService.IcsExport export = calendarIcsService.exportFeed(user, range.from(), range.to());
        return CalendarSyncController.calendarResponse(export.bytes(), "dutylog-calendar.ics", false);
    }
}
