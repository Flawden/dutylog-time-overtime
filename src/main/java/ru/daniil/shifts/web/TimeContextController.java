package ru.daniil.shifts.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.daniil.shifts.dto.Dtos.TimeContextDto;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.service.CurrentUserService;
import ru.daniil.shifts.service.UserTimeService;

import java.security.Principal;
import java.time.Instant;
import java.time.ZonedDateTime;

/** Read-only server clock contract for web, Android and future sync clients. */
@RestController
@RequestMapping({"/api/time/context", "/api/v1/time/context"})
public class TimeContextController {
    private final CurrentUserService currentUserService;
    private final UserTimeService userTimeService;

    public TimeContextController(CurrentUserService currentUserService, UserTimeService userTimeService) {
        this.currentUserService = currentUserService;
        this.userTimeService = userTimeService;
    }

    @GetMapping
    public TimeContextDto context(Principal principal) {
        AppUser user = currentUserService.requireUser(principal);
        Instant now = userTimeService.nowInstant();
        ZonedDateTime work = userTimeService.inWorkZone(now, user);
        ZonedDateTime display = userTimeService.inDisplayZone(now, user);
        return new TimeContextDto(
                now.toString(),
                work.getZone().getId(),
                display.getZone().getId(),
                work.toLocalDateTime().toString(),
                display.toLocalDateTime().toString(),
                work.toLocalDate().toString(),
                display.toLocalDate().toString(),
                work.getOffset().toString(),
                display.getOffset().toString(),
                work.getZone().equals(display.getZone())
        );
    }
}
