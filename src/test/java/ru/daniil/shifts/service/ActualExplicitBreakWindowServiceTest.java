package ru.daniil.shifts.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import ru.daniil.shifts.dto.Dtos.ActualWorkBreakWindowRequest;
import ru.daniil.shifts.dto.Dtos.ActualWorkIntervalRequest;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.repo.UserRepository;
import ru.daniil.shifts.service.exception.ApiException;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ActualExplicitBreakWindowServiceTest {

    @Autowired ActualWorkService actualWork;
    @Autowired UserRepository users;

    private AppUser newUser(String zone) {
        AppUser user = new AppUser(
                "u1c-actual-" + UUID.randomUUID().toString().substring(0, 12),
                "{noop}irrelevant"
        );
        user.setWorkTimezone(zone);
        return users.save(user);
    }

    @Test
    void createExplicitNightPersistsExactSnapshotAndDerivesCompatibilityTotal() {
        AppUser user = newUser("UTC");

        var saved = actualWork.create(
                user,
                explicitNightRequest(null, "exact night")
        );

        assertEquals("EXPLICIT_WINDOWS", saved.breakAuthority());
        assertEquals(60, saved.breakMinutes());
        assertEquals(660, saved.workedMinutes());
        assertEquals(1, saved.breakWindows().size());

        var window = saved.breakWindows().get(0);
        assertEquals(0, window.position());
        assertEquals("2026-09-01T23:30", window.sourceStartLocal());
        assertEquals("2026-09-02T00:30", window.sourceEndLocal());
        assertEquals("2026-09-01T23:30:00Z", window.startInstant());
        assertEquals("2026-09-02T00:30:00Z", window.endInstant());
        assertEquals("UTC", window.sourceTimezone());
    }

    @Test
    void legacyScalarCreateRemainsLegacyAndDoesNotInventWindows() {
        AppUser user = newUser("UTC");

        var saved = actualWork.create(
                user,
                new ActualWorkIntervalRequest(
                        "2026-09-01",
                        "2026-09-02",
                        "20:00",
                        "08:00",
                        60,
                        "legacy compatibility"
                )
        );

        assertEquals("LEGACY_EARLY_TOTAL", saved.breakAuthority());
        assertEquals(60, saved.breakMinutes());
        assertEquals(660, saved.workedMinutes());
        assertTrue(saved.breakWindows().isEmpty());
    }

    @Test
    void overlappingExplicitWindowsAreRejected() {
        AppUser user = newUser("UTC");

        ApiException error = assertThrows(
                ApiException.class,
                () -> actualWork.create(
                        user,
                        explicitRequest(
                                null,
                                List.of(
                                        window(0, "2026-09-01T22:00", "2026-09-01T23:00"),
                                        window(1, "2026-09-01T22:30", "2026-09-01T23:30")
                                )
                        )
                )
        );

        assertTrue(error.getMessage().contains("перерыв"));
    }

    @Test
    void explicitWindowOutsideActualIntervalIsRejected() {
        AppUser user = newUser("UTC");

        ApiException error = assertThrows(
                ApiException.class,
                () -> actualWork.create(
                        user,
                        explicitRequest(
                                null,
                                List.of(window(
                                        0,
                                        "2026-09-01T19:30",
                                        "2026-09-01T20:30"
                                ))
                        )
                )
        );

        assertTrue(error.getMessage().contains("перерыв"));
    }

    @Test
    void malformedExplicitLocalBoundaryIsRejected() {
        AppUser user = newUser("UTC");

        ApiException error = assertThrows(
                ApiException.class,
                () -> actualWork.create(
                        user,
                        explicitRequest(
                                null,
                                List.of(window(
                                        0,
                                        "not-a-local-date-time",
                                        "2026-09-02T00:30"
                                ))
                        )
                )
        );

        assertTrue(error.getMessage().contains("формате"));
    }

    @Test
    void conflictingScalarCannotOverrideExplicitWindowTruth() {
        AppUser user = newUser("UTC");

        ApiException error = assertThrows(
                ApiException.class,
                () -> actualWork.create(
                        user,
                        explicitNightRequest(30, "conflicting dual truth")
                )
        );

        assertTrue(error.getMessage().contains("breakMinutes"));
    }

    @Test
    void shapeNeutralLegacyClientUpdatePreservesExistingExplicitSnapshot() {
        AppUser user = newUser("UTC");
        var created = actualWork.create(
                user,
                explicitNightRequest(null, "before")
        );
        var before = created.breakWindows().get(0);

        var updated = actualWork.update(
                user,
                created.id(),
                new ActualWorkIntervalRequest(
                        "2026-09-01",
                        "2026-09-02",
                        "20:00",
                        "08:00",
                        60,
                        "note-only old client update"
                )
        );

        assertEquals("EXPLICIT_WINDOWS", updated.breakAuthority());
        assertEquals(1, updated.breakWindows().size());
        assertEquals(before.startInstant(), updated.breakWindows().get(0).startInstant());
        assertEquals(before.endInstant(), updated.breakWindows().get(0).endInstant());
        assertEquals("note-only old client update", updated.note());
    }

    @Test
    void shapeChangeWithoutFreshExplicitWindowsIsRejected() {
        AppUser user = newUser("UTC");
        var created = actualWork.create(
                user,
                explicitNightRequest(null, "before shape edit")
        );

        ApiException error = assertThrows(
                ApiException.class,
                () -> actualWork.update(
                        user,
                        created.id(),
                        new ActualWorkIntervalRequest(
                                "2026-09-01",
                                "2026-09-02",
                                "20:00",
                                "09:00",
                                60,
                                "unsafe shape edit"
                        )
                )
        );

        assertTrue(error.getMessage().contains("breakWindows"));
    }

    @Test
    void explicitDstWindowUsesDeterministicRealElapsedDuration() {
        AppUser user = newUser("Europe/Berlin");

        var saved = actualWork.create(
                user,
                new ActualWorkIntervalRequest(
                        "2026-03-29",
                        "2026-03-29",
                        "00:00",
                        "08:00",
                        null,
                        List.of(window(
                                0,
                                "2026-03-29T01:30",
                                "2026-03-29T03:30"
                        )),
                        "spring-forward exact break"
                )
        );

        assertEquals(420, saved.workedMinutes() + saved.breakMinutes());
        assertEquals(60, saved.breakMinutes());
        assertEquals(360, saved.workedMinutes());
        assertEquals("2026-03-29T00:30:00Z", saved.breakWindows().get(0).startInstant());
        assertEquals("2026-03-29T01:30:00Z", saved.breakWindows().get(0).endInstant());
    }

    private ActualWorkIntervalRequest explicitNightRequest(
            Integer compatibilityBreakMinutes,
            String note
    ) {
        return explicitRequest(
                compatibilityBreakMinutes,
                List.of(window(
                        0,
                        "2026-09-01T23:30",
                        "2026-09-02T00:30"
                )),
                note
        );
    }

    private ActualWorkIntervalRequest explicitRequest(
            Integer compatibilityBreakMinutes,
            List<ActualWorkBreakWindowRequest> windows
    ) {
        return explicitRequest(compatibilityBreakMinutes, windows, "explicit");
    }

    private ActualWorkIntervalRequest explicitRequest(
            Integer compatibilityBreakMinutes,
            List<ActualWorkBreakWindowRequest> windows,
            String note
    ) {
        return new ActualWorkIntervalRequest(
                "2026-09-01",
                "2026-09-02",
                "20:00",
                "08:00",
                compatibilityBreakMinutes,
                windows,
                note
        );
    }

    private ActualWorkBreakWindowRequest window(
            int position,
            String start,
            String end
    ) {
        return new ActualWorkBreakWindowRequest(position, start, end);
    }
}
