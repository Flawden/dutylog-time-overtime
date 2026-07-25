package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import ru.daniil.shifts.model.AppUser;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.zone.ZoneOffsetTransition;
import java.time.zone.ZoneRules;
import java.util.List;

/**
 * Central time boundary for DutyLog.
 *
 * <p>v27.9 exposes one canonical IANA timezone per user. The historical
 * work/display methods remain as compatibility aliases, but both resolve to
 * the same zone. Floating dates such as birthdays remain {@link LocalDate};
 * absolute instants such as migrated overtime are reprojected when the user
 * changes the canonical timezone.</p>
 *
 * <p>DST policy is deterministic: nonexistent wall-clock values are shifted
 * forward by the transition gap; ambiguous values use the earlier offset.</p>
 */
@Service
public class UserTimeService {
    public static final ZoneId FALLBACK_ZONE = ZoneId.of("Europe/Moscow");

    private final Clock clock;

    public UserTimeService() {
        this(Clock.systemUTC());
    }

    UserTimeService(Clock clock) {
        this.clock = clock;
    }

    /** Backward-compatible alias for the work timezone. */
    public ZoneId zone(AppUser user) {
        return workZone(user);
    }

    public ZoneId workZone(AppUser user) {
        return resolveZone(user == null ? null : user.getWorkTimezone(), FALLBACK_ZONE);
    }

    public ZoneId displayZone(AppUser user) {
        // v27.9: one canonical timezone for calendar semantics and display.
        return workZone(user);
    }

    public Instant nowInstant() {
        return clock.instant();
    }

    /** Backward-compatible aliases: today/now always mean work-local time. */
    public LocalDate today(AppUser user) {
        return workToday(user);
    }

    public LocalDateTime now(AppUser user) {
        return workNow(user);
    }

    public LocalDate workToday(AppUser user) {
        return inWorkZone(nowInstant(), user).toLocalDate();
    }

    public LocalDate displayToday(AppUser user) {
        return inDisplayZone(nowInstant(), user).toLocalDate();
    }

    public LocalDateTime workNow(AppUser user) {
        return inWorkZone(nowInstant(), user).toLocalDateTime();
    }

    public LocalDateTime displayNow(AppUser user) {
        return inDisplayZone(nowInstant(), user).toLocalDateTime();
    }

    public ZonedDateTime inWorkZone(Instant instant, AppUser user) {
        return requireInstant(instant).atZone(workZone(user));
    }

    public ZonedDateTime inDisplayZone(Instant instant, AppUser user) {
        return requireInstant(instant).atZone(displayZone(user));
    }

    public Instant toWorkInstant(AppUser user, LocalDate date, LocalTime time) {
        if (date == null || time == null) {
            throw new IllegalArgumentException("Date and time are required for an absolute moment");
        }
        return toWorkInstant(user, LocalDateTime.of(date, time));
    }

    public Instant toWorkInstant(AppUser user, LocalDateTime localDateTime) {
        return resolveLocalDateTime(localDateTime, workZone(user)).toInstant();
    }

    public ZonedDateTime resolveLocalDateTime(LocalDateTime localDateTime, ZoneId zone) {
        if (localDateTime == null) throw new IllegalArgumentException("Local date-time is required");
        ZoneId safeZone = zone == null ? FALLBACK_ZONE : zone;
        ZoneRules rules = safeZone.getRules();
        List<ZoneOffset> offsets = rules.getValidOffsets(localDateTime);
        if (offsets.size() == 1) {
            return ZonedDateTime.ofLocal(localDateTime, safeZone, offsets.get(0));
        }
        if (offsets.size() > 1) {
            return ZonedDateTime.ofLocal(localDateTime, safeZone, offsets.get(0));
        }

        ZoneOffsetTransition transition = rules.getTransition(localDateTime);
        if (transition == null) {
            return localDateTime.atZone(safeZone);
        }
        LocalDateTime shifted = localDateTime.plusSeconds(transition.getDuration().getSeconds());
        return ZonedDateTime.ofLocal(shifted, safeZone, transition.getOffsetAfter());
    }

    /** Resolves a persisted IANA identifier without throwing on legacy/corrupt data. */
    public ZoneId resolveZone(String value, ZoneId fallback) {
        try {
            return ZoneId.of(value == null || value.isBlank() ? fallback.getId() : value.trim());
        } catch (DateTimeException e) {
            return fallback;
        }
    }

    private Instant requireInstant(Instant instant) {
        if (instant == null) throw new IllegalArgumentException("Instant is required");
        return instant;
    }
}
