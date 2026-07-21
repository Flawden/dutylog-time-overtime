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
    void todayAndNowUseTheUsersPersistedIanaTimezone() {
        Clock fixed = Clock.fixed(Instant.parse("2026-07-20T21:30:00Z"), ZoneOffset.UTC);
        UserTimeService time = new UserTimeService(fixed);
        AppUser user = new AppUser("timezone-owner", "hash");

        user.setWorkTimezone("Europe/Chisinau");
        assertEquals(LocalDate.of(2026, 7, 21), time.today(user));
        assertEquals(LocalDateTime.of(2026, 7, 21, 0, 30), time.now(user));

        user.setWorkTimezone("America/New_York");
        assertEquals(LocalDate.of(2026, 7, 20), time.today(user));
        assertEquals(LocalDateTime.of(2026, 7, 20, 17, 30), time.now(user));
    }

    @Test
    void invalidOrMissingTimezoneFallsBackToEuropeMoscow() {
        Clock fixed = Clock.fixed(Instant.parse("2026-01-01T21:30:00Z"), ZoneOffset.UTC);
        UserTimeService time = new UserTimeService(fixed);
        AppUser user = new AppUser("timezone-fallback", "hash");

        user.setWorkTimezone("Not/A_Zone");
        assertEquals("Europe/Moscow", time.zone(user).getId());
        assertEquals(LocalDate.of(2026, 1, 2), time.today(user));

        assertEquals("Europe/Moscow", time.zone(null).getId());
    }
}
