package ru.daniil.shifts.service;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorkBreakWindowAuthorityServiceTest {
    private final WorkBreakWindowAuthorityService service =
            new WorkBreakWindowAuthorityService(new UserTimeService());

    @Test
    void noBreaksKeepTheWholeShiftPaid() {
        Instant start = Instant.parse("2026-09-01T17:00:00Z");
        Instant end = Instant.parse("2026-09-02T05:00:00Z");

        assertEquals(
                720,
                service.paidMinutes(start, end, List.of())
        );
    }

    @Test
    void nightBreakAcrossMidnightResolvesFromSourceShiftOffset() {
        // Asia/Yekaterinburg is UTC+05:00 here:
        // 20:00 local -> 15:00Z and 08:00 next day -> 03:00Z.
        Instant start = Instant.parse("2026-09-01T15:00:00Z");
        Instant end = Instant.parse("2026-09-02T03:00:00Z");

        var resolved = service.resolve(
                start,
                end,
                LocalDateTime.parse("2026-09-01T20:00"),
                "Asia/Yekaterinburg",
                List.of(new WorkBreakWindowAuthorityService.TemplateBreakWindow(
                        0,
                        210,
                        60
                ))
        );

        assertEquals(1, resolved.size());
        assertEquals(
                LocalDateTime.parse("2026-09-01T23:30"),
                resolved.get(0).sourceStart()
        );
        assertEquals(
                LocalDateTime.parse("2026-09-02T00:30"),
                resolved.get(0).sourceEnd()
        );
        assertEquals(60, resolved.get(0).elapsedMinutes());
    }

    @Test
    void subtractionKeepsBothPaidSidesOfCrossMidnightLunch() {
        // Keep absolute occurrence identity consistent with the same
        // Asia/Yekaterinburg 20:00 -> 08:00 source-wall-clock shift.
        Instant start = Instant.parse("2026-09-01T15:00:00Z");
        Instant end = Instant.parse("2026-09-02T03:00:00Z");

        var resolved = service.resolve(
                start,
                end,
                LocalDateTime.parse("2026-09-01T20:00"),
                "Asia/Yekaterinburg",
                List.of(new WorkBreakWindowAuthorityService.TemplateBreakWindow(
                        0,
                        210,
                        60
                ))
        );

        var paid = service.subtract(start, end, resolved);

        assertEquals(2, paid.size());
        assertEquals(210, paid.get(0).minutes());
        assertEquals(450, paid.get(1).minutes());
        assertEquals(660, service.paidMinutes(start, end, resolved));
    }

    @Test
    void multipleTouchingBreaksAreAllowedAndSortedByAbsoluteTime() {
        Instant start = Instant.parse("2026-09-01T03:00:00Z");
        Instant end = Instant.parse("2026-09-01T15:00:00Z");

        var resolved = service.resolve(
                start,
                end,
                LocalDateTime.parse("2026-09-01T08:00"),
                "Asia/Yekaterinburg",
                List.of(
                        new WorkBreakWindowAuthorityService.TemplateBreakWindow(1, 270, 15),
                        new WorkBreakWindowAuthorityService.TemplateBreakWindow(0, 240, 30)
                )
        );

        assertEquals(List.of(0, 1), resolved.stream()
                .map(WorkBreakWindowAuthorityService.ResolvedBreakWindow::position)
                .toList());
        assertEquals(675, service.paidMinutes(start, end, resolved));
    }

    @Test
    void overlappingBreaksAreRejected() {
        Instant start = Instant.parse("2026-09-01T03:00:00Z");
        Instant end = Instant.parse("2026-09-01T15:00:00Z");

        assertThrows(
                IllegalArgumentException.class,
                () -> service.resolve(
                        start,
                        end,
                        LocalDateTime.parse("2026-09-01T08:00"),
                        "Asia/Yekaterinburg",
                        List.of(
                                new WorkBreakWindowAuthorityService.TemplateBreakWindow(0, 240, 60),
                                new WorkBreakWindowAuthorityService.TemplateBreakWindow(1, 270, 60)
                        )
                )
        );
    }

    @Test
    void duplicatePositionsAreRejected() {
        Instant start = Instant.parse("2026-09-01T03:00:00Z");
        Instant end = Instant.parse("2026-09-01T15:00:00Z");

        assertThrows(
                IllegalArgumentException.class,
                () -> service.resolve(
                        start,
                        end,
                        LocalDateTime.parse("2026-09-01T08:00"),
                        "Asia/Yekaterinburg",
                        List.of(
                                new WorkBreakWindowAuthorityService.TemplateBreakWindow(0, 120, 15),
                                new WorkBreakWindowAuthorityService.TemplateBreakWindow(0, 240, 15)
                        )
                )
        );
    }

    @Test
    void breakOutsideTheOccurrenceIsRejected() {
        Instant start = Instant.parse("2026-09-01T03:00:00Z");
        Instant end = Instant.parse("2026-09-01T11:00:00Z");

        assertThrows(
                IllegalArgumentException.class,
                () -> service.resolve(
                        start,
                        end,
                        LocalDateTime.parse("2026-09-01T08:00"),
                        "Asia/Yekaterinburg",
                        List.of(new WorkBreakWindowAuthorityService.TemplateBreakWindow(
                                0,
                                450,
                                60
                        ))
                )
        );
    }

    @Test
    void invalidTemplateShapeIsRejectedAtConstruction() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new WorkBreakWindowAuthorityService.TemplateBreakWindow(
                        0,
                        1430,
                        30
                )
        );
    }
}
