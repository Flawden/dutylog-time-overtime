package ru.daniil.shifts.service;

import org.junit.jupiter.api.Test;
import ru.daniil.shifts.model.AppUser;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkIntervalServiceTest {

    private final UserTimeService time = new UserTimeService(Clock.systemUTC());
    private final WorkIntervalService intervals = new WorkIntervalService(time);

    @Test
    void overnightShiftBecomesOneAbsoluteIntervalWithNetMinutes() {
        AppUser user = user("UTC");
        var interval = intervals.resolve(
                user, LocalDate.of(2026, 7, 20), LocalTime.of(20, 0), LocalTime.of(8, 0), 60);

        assertEquals("2026-07-20T20:00:00Z", interval.startInstant().toString());
        assertEquals("2026-07-21T08:00:00Z", interval.endInstant().toString());
        assertEquals(720, interval.elapsedMinutes());
        assertEquals(660, interval.netMinutes());
        assertTrue(interval.crossesMidnight());
    }

    @Test
    void daylightSavingChangesActualElapsedDuration() {
        AppUser user = user("Europe/Berlin");
        var spring = intervals.resolve(
                user, LocalDate.of(2026, 3, 29), LocalTime.MIDNIGHT, LocalTime.of(8, 0), 0);
        var autumn = intervals.resolve(
                user, LocalDate.of(2026, 10, 25), LocalTime.MIDNIGHT, LocalTime.of(8, 0), 0);

        assertEquals(420, spring.elapsedMinutes(), "spring-forward shift loses one actual hour");
        assertEquals(540, autumn.elapsedMinutes(), "fall-back shift gains one actual hour");
    }

    @Test
    void equalStartAndEndRepresentsAFullDayShift() {
        AppUser user = user("UTC");
        var interval = intervals.resolve(
                user, LocalDate.of(2026, 7, 20), LocalTime.of(8, 0), LocalTime.of(8, 0), 30);

        assertEquals(1440, interval.elapsedMinutes());
        assertEquals(1410, interval.netMinutes());
        assertTrue(interval.crossesMidnight());
    }

    private AppUser user(String timezone) {
        AppUser user = new AppUser("interval-" + timezone.replace('/', '-'), "hash");
        user.setWorkTimezone(timezone);
        user.setDisplayTimezone(timezone);
        return user;
    }
}
