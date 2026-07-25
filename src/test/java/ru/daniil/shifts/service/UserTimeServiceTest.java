package ru.daniil.shifts.service;

import org.junit.jupiter.api.Test;
import ru.daniil.shifts.model.AppUser;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UserTimeServiceTest {

    @Test
    void workAndDisplayZonesProjectTheSameInstantWithoutMutatingEachOther() {
        Clock fixed = Clock.fixed(Instant.parse("2026-07-20T21:30:00Z"), ZoneOffset.UTC);
        UserTimeService time = new UserTimeService(fixed);
        AppUser user = new AppUser("timezone-owner", "hash");
        user.setWorkTimezone("Europe/Chisinau");
        user.setDisplayTimezone("America/New_York");

        assertEquals(Instant.parse("2026-07-20T21:30:00Z"), time.nowInstant());
        assertEquals(LocalDate.of(2026, 7, 21), time.workToday(user));
        assertEquals(LocalDateTime.of(2026, 7, 21, 0, 30), time.workNow(user));
        assertEquals(LocalDate.of(2026, 7, 20), time.displayToday(user));
        assertEquals(LocalDateTime.of(2026, 7, 20, 17, 30), time.displayNow(user));
        assertEquals("Europe/Chisinau", time.workZone(user).getId());
        assertEquals("America/New_York", time.displayZone(user).getId());
    }

    @Test
    void invalidOrMissingZonesUseStableFallbacks() {
        Clock fixed = Clock.fixed(Instant.parse("2026-01-01T21:30:00Z"), ZoneOffset.UTC);
        UserTimeService time = new UserTimeService(fixed);
        AppUser user = new AppUser("timezone-fallback", "hash");

        user.setWorkTimezone("Not/A_Zone");
        user.setDisplayTimezone("Also/Invalid");
        assertEquals("Europe/Moscow", time.workZone(user).getId());
        assertEquals("Europe/Moscow", time.displayZone(user).getId());
        assertEquals(LocalDate.of(2026, 1, 2), time.today(user));
        assertEquals("Europe/Moscow", time.zone(null).getId());
    }

    @Test
    void dstGapMovesForwardAndOverlapUsesEarlierOffset() {
        UserTimeService time = new UserTimeService(Clock.systemUTC());
        AppUser user = new AppUser("dst-owner", "hash");
        user.setWorkTimezone("Europe/Berlin");

        assertEquals(
                Instant.parse("2026-03-29T01:30:00Z"),
                time.toWorkInstant(user, LocalDateTime.of(2026, 3, 29, 2, 30)),
                "nonexistent 02:30 is shifted forward by the one-hour DST gap"
        );
        assertEquals(
                Instant.parse("2026-10-25T00:30:00Z"),
                time.toWorkInstant(user, LocalDateTime.of(2026, 10, 25, 2, 30)),
                "ambiguous 02:30 uses the earlier summer offset"
        );
    }
}
