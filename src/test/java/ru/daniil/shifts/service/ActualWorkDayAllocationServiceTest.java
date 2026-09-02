package ru.daniil.shifts.service;

import org.junit.jupiter.api.Test;
import ru.daniil.shifts.model.ActualWorkBreakWindow;
import ru.daniil.shifts.model.ActualWorkInterval;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ActualWorkDayAllocationServiceTest {

    private final WorkBreakWindowAuthorityService breakAuthority =
            new WorkBreakWindowAuthorityService(new UserTimeService());

    private final ActualWorkDayAllocationService service =
            new ActualWorkDayAllocationService(breakAuthority);

    @Test
    void crossMidnightKeepsHistoricalEarlyBreakSemanticsAndExactNetSegments() {
        ActualWorkInterval interval = interval(
                LocalDate.of(2026, 8, 18),
                LocalDate.of(2026, 8, 19),
                LocalTime.of(20, 0),
                LocalTime.of(8, 0),
                60
        );

        var segments = service.netSegments(interval);

        assertEquals(2, segments.size());
        assertEquals("2026-08-18T21:00", segments.get(0).start().toString());
        assertEquals("2026-08-19T00:00", segments.get(0).end().toString());
        assertEquals(180, segments.get(0).minutes());
        assertEquals("2026-08-19T00:00", segments.get(1).start().toString());
        assertEquals("2026-08-19T08:00", segments.get(1).end().toString());
        assertEquals(480, segments.get(1).minutes());

        assertEquals(
                180,
                service.netMinutesOnDate(
                        interval,
                        LocalDate.of(2026, 8, 18)
                )
        );
        assertEquals(
                480,
                service.netMinutesOnDate(
                        interval,
                        LocalDate.of(2026, 8, 19)
                )
        );
    }

    @Test
    void breakCanConsumeEntireFirstCalendarSlice() {
        ActualWorkInterval interval = interval(
                LocalDate.of(2026, 8, 18),
                LocalDate.of(2026, 8, 19),
                LocalTime.of(23, 0),
                LocalTime.of(8, 0),
                60
        );

        var segments = service.netSegments(interval);

        assertEquals(1, segments.size());
        assertEquals("2026-08-19T00:00", segments.get(0).start().toString());
        assertEquals("2026-08-19T08:00", segments.get(0).end().toString());
        assertEquals(480, segments.get(0).minutes());
    }

    @Test
    void exactIdentityUsesRealDstElapsedMinutes() {
        ActualWorkInterval interval = interval(
                LocalDate.of(2026, 3, 29),
                LocalDate.of(2026, 3, 29),
                LocalTime.of(0, 0),
                LocalTime.of(8, 0),
                0
        );

        interval.setSourceTimezone("Europe/Berlin");
        interval.setStartInstant(Instant.parse("2026-03-28T23:00:00Z"));
        interval.setEndInstant(Instant.parse("2026-03-29T06:00:00Z"));

        var segments = service.netSegments(interval);

        assertEquals(1, segments.size());
        assertEquals(true, segments.get(0).exact());
        assertEquals(420, segments.get(0).minutes());
        assertEquals(
                420,
                service.netMinutesOnDate(
                        interval,
                        LocalDate.of(2026, 3, 29)
                )
        );
    }

    @Test
    void explicitCrossMidnightBreakIsSubtractedBeforeCalendarSplit() {
        ActualWorkInterval interval = explicitNight(
                LocalDate.of(2026, 9, 1)
        );

        var segments = service.netSegments(interval);

        assertEquals(2, segments.size());
        assertEquals("2026-09-01T20:00", segments.get(0).start().toString());
        assertEquals("2026-09-01T23:30", segments.get(0).end().toString());
        assertEquals(210, segments.get(0).minutes());
        assertEquals("2026-09-02T00:30", segments.get(1).start().toString());
        assertEquals("2026-09-02T08:00", segments.get(1).end().toString());
        assertEquals(450, segments.get(1).minutes());

        assertEquals(
                210,
                service.netMinutesOnDate(
                        interval,
                        LocalDate.of(2026, 9, 1)
                )
        );
        assertEquals(
                450,
                service.netMinutesOnDate(
                        interval,
                        LocalDate.of(2026, 9, 2)
                )
        );
    }

    @Test
    void consecutiveExplicitNightsContributeElevenPaidHoursToSharedDate() {
        ActualWorkInterval first = explicitNight(LocalDate.of(2026, 9, 1));
        ActualWorkInterval second = explicitNight(LocalDate.of(2026, 9, 2));
        LocalDate shared = LocalDate.of(2026, 9, 2);

        int total = service.netMinutesOnDate(first, shared)
                + service.netMinutesOnDate(second, shared);

        assertEquals(660, total);
        assertEquals(450, service.netMinutesOnDate(first, shared));
        assertEquals(210, service.netMinutesOnDate(second, shared));
    }

    private ActualWorkInterval explicitNight(LocalDate startDate) {
        LocalDate endDate = startDate.plusDays(1);
        ActualWorkInterval interval = interval(
                startDate,
                endDate,
                LocalTime.of(20, 0),
                LocalTime.of(8, 0),
                60
        );
        interval.setSourceTimezone("UTC");
        interval.setStartInstant(startDate.atTime(20, 0).toInstant(java.time.ZoneOffset.UTC));
        interval.setEndInstant(endDate.atTime(8, 0).toInstant(java.time.ZoneOffset.UTC));

        LocalDateTime breakStart = startDate.atTime(23, 30);
        LocalDateTime breakEnd = endDate.atTime(0, 30);
        ActualWorkBreakWindow window = new ActualWorkBreakWindow(
                interval,
                0,
                breakStart,
                breakEnd,
                breakStart.toInstant(java.time.ZoneOffset.UTC),
                breakEnd.toInstant(java.time.ZoneOffset.UTC),
                "UTC"
        );
        interval.captureExplicitBreakWindows(60, List.of(window));
        return interval;
    }

    private ActualWorkInterval interval(
            LocalDate startDate,
            LocalDate endDate,
            LocalTime start,
            LocalTime end,
            int breakMinutes
    ) {
        ActualWorkInterval interval = new ActualWorkInterval(null);
        interval.setWorkDate(startDate);
        interval.setEndDate(endDate);
        interval.setStartTime(start);
        interval.setEndTime(end);
        interval.captureLegacyBreakMinutes(breakMinutes);
        return interval;
    }
}
