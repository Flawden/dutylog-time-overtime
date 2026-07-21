package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import ru.daniil.shifts.model.AppUser;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** Central source of user-local dates and times. */
@Service
public class UserTimeService {
    private final Clock clock;

    public UserTimeService() {
        this(Clock.systemUTC());
    }

    UserTimeService(Clock clock) {
        this.clock = clock;
    }

    public ZoneId zone(AppUser user) {
        String value = user == null ? null : user.getWorkTimezone();
        try {
            return ZoneId.of(value == null || value.isBlank() ? "Europe/Moscow" : value);
        } catch (DateTimeException e) {
            return ZoneId.of("Europe/Moscow");
        }
    }

    public LocalDate today(AppUser user) {
        return LocalDate.now(clock.withZone(zone(user)));
    }

    public LocalDateTime now(AppUser user) {
        return LocalDateTime.now(clock.withZone(zone(user)));
    }
}
