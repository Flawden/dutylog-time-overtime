package ru.daniil.shifts.service;

import org.junit.jupiter.api.Test;
import ru.daniil.shifts.model.ActualWorkInterval;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ActualWorkDayAllocationServiceTest {

    private final ActualWorkDayAllocationService service =
            new ActualWorkDayAllocationService();

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

        assertEquals(
                "2026-08-18T21:00",
                segments.get(0).start().toString()
        );
        assertEquals(
                "2026-08-19T00:00",
                segments.get(0).end().toString()
        );
        assertEquals(180, segments.get(0).minutes());

        assertEquals(
                "2026-08-19T00:00",
                segments.get(1).start().toString()
        );
        assertEquals(
                "2026-08-19T08:00",
                segments.get(1).end().toString()
        );
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
        assertEquals(
                "2026-08-19T00:00",
                segments.get(0).start().toString()
        );
        assertEquals(
                "2026-08-19T08:00",
                segments.get(0).end().toString()
        );
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
        interval.setStartInstant(
                java.time.Instant.parse("2026-03-28T23:00:00Z")
        );
        interval.setEndInstant(
                java.time.Instant.parse("2026-03-29T06:00:00Z")
        );

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
        interval.setBreakMinutes(breakMinutes);
        return interval;
    }
}
