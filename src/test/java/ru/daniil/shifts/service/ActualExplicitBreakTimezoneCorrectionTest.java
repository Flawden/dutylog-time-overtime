package ru.daniil.shifts.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import ru.daniil.shifts.dto.Dtos.ActualWorkBreakWindowRequest;
import ru.daniil.shifts.dto.Dtos.ActualWorkIntervalRequest;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.repo.UserRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ActualExplicitBreakTimezoneCorrectionTest {

    @Autowired WorkTimezoneChangeService changes;
    @Autowired ActualWorkService actualWork;
    @Autowired UserRepository users;

    private AppUser newUser(String zone) {
        AppUser user = new AppUser(
                "u1c-tz-" + UUID.randomUUID().toString().substring(0, 12),
                "{noop}irrelevant"
        );
        user.setWorkTimezone(zone);
        return users.save(user);
    }

    @Test
    void historicalCorrectionReResolvesBreakInstantsFromFrozenSourceLocalEvidence() {
        AppUser user = newUser("UTC");

        var before = actualWork.create(
                user,
                new ActualWorkIntervalRequest(
                        "2026-03-29",
                        "2026-03-29",
                        "00:00",
                        "08:00",
                        null,
                        List.of(new ActualWorkBreakWindowRequest(
                                0,
                                "2026-03-29T01:30",
                                "2026-03-29T03:30"
                        )),
                        "historical correction proof"
                )
        );

        assertEquals("UTC", before.sourceTimezone());
        assertEquals(120, before.breakMinutes());
        assertEquals("2026-03-29T01:30:00Z", before.breakWindows().get(0).startInstant());
        assertEquals("2026-03-29T03:30:00Z", before.breakWindows().get(0).endInstant());

        var result = changes.upsertAndReconcile(
                user,
                LocalDate.of(2026, 3, 29).atStartOfDay(),
                "Europe/Berlin"
        );

        assertEquals(1, result.reconstructedIntervals());

        var after = actualWork.list(
                user,
                LocalDate.of(2026, 3, 29),
                LocalDate.of(2026, 3, 29)
        ).stream().filter(row -> row.id().equals(before.id())).findFirst().orElseThrow();

        assertEquals("Europe/Berlin", after.sourceTimezone());
        assertTrue(after.identityReconstructed());
        assertEquals("EXPLICIT_WINDOWS", after.breakAuthority());
        assertEquals(60, after.breakMinutes());
        assertEquals(360, after.workedMinutes());

        var breakAfter = after.breakWindows().get(0);
        assertEquals("2026-03-29T01:30", breakAfter.sourceStartLocal());
        assertEquals("2026-03-29T03:30", breakAfter.sourceEndLocal());
        assertEquals("2026-03-29T00:30:00Z", breakAfter.startInstant());
        assertEquals("2026-03-29T01:30:00Z", breakAfter.endInstant());
        assertEquals("Europe/Berlin", breakAfter.sourceTimezone());
    }

    @Test
    void unrelatedCurrentTimezoneChangeDoesNotMoveHistoricalExplicitSnapshot() {
        AppUser user = newUser("UTC");

        var before = actualWork.create(
                user,
                new ActualWorkIntervalRequest(
                        "2026-06-01",
                        "2026-06-01",
                        "08:00",
                        "16:00",
                        null,
                        List.of(new ActualWorkBreakWindowRequest(
                                0,
                                "2026-06-01T12:00",
                                "2026-06-01T13:00"
                        )),
                        "historical frozen proof"
                )
        );

        var breakBefore = before.breakWindows().get(0);

        var result = changes.upsertAndReconcile(
                user,
                LocalDate.of(2026, 9, 1).atStartOfDay(),
                "Europe/Moscow"
        );

        assertEquals(0, result.reconstructedIntervals());

        var after = actualWork.list(
                user,
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 1)
        ).stream().filter(row -> row.id().equals(before.id())).findFirst().orElseThrow();

        assertEquals("UTC", after.sourceTimezone());
        assertFalse(after.identityReconstructed());
        assertEquals(breakBefore.startInstant(), after.breakWindows().get(0).startInstant());
        assertEquals(breakBefore.endInstant(), after.breakWindows().get(0).endInstant());
        assertEquals("UTC", after.breakWindows().get(0).sourceTimezone());
    }
}
