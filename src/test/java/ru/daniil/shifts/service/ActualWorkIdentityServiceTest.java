package ru.daniil.shifts.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.repo.UserRepository;
import ru.daniil.shifts.service.exception.ApiException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class ActualWorkIdentityServiceTest {

    @Autowired
    ActualWorkIdentityService identity;

    @Autowired
    WorkTimezoneHistoryService timezoneHistory;

    @Autowired
    UserRepository users;

    AppUser user;

    @BeforeEach
    void setUp() {
        user = users.save(
                new AppUser(
                        "actual-work-identity-user",
                        "{noop}irrelevant"
                )
        );
    }

    @Test
    void usesHistoricalTimezoneInsteadOfCurrentProfileGuess() {
        timezoneHistory.upsert(
                user,
                LocalDate.of(2026, 3, 3).atStartOfDay(),
                "Asia/Yekaterinburg"
        );

        timezoneHistory.upsert(
                user,
                LocalDate.of(2026, 8, 3).atStartOfDay(),
                "Europe/Moscow"
        );

        var result = identity.resolve(
                user,
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 2),
                LocalTime.of(20, 0),
                LocalTime.of(8, 0)
        );

        assertEquals(
                "Asia/Yekaterinburg",
                result.sourceTimezone()
        );

        assertEquals(
                "2026-08-01T15:00:00Z",
                result.startInstant().toString()
        );

        assertEquals(
                "2026-08-02T03:00:00Z",
                result.endInstant().toString()
        );

        assertEquals(720, result.elapsedMinutes());
    }

    @Test
    void springForwardUsesRealElapsedMinutes() {
        timezoneHistory.upsert(
                user,
                LocalDate.of(2026, 3, 1).atStartOfDay(),
                "Europe/Berlin"
        );

        var result = identity.resolve(
                user,
                LocalDate.of(2026, 3, 29),
                LocalDate.of(2026, 3, 29),
                LocalTime.of(0, 0),
                LocalTime.of(8, 0)
        );

        // Wall clock says eight hours, but DST spring-forward removes one.
        assertEquals(420, result.elapsedMinutes());
    }

    @Test
    void contextBoundaryInsideFactFailsClosed() {
        timezoneHistory.upsert(
                user,
                LocalDateTime.of(2026, 8, 3, 14, 0),
                "Asia/Yekaterinburg"
        );

        ApiException error = assertThrows(
                ApiException.class,
                () -> identity.resolve(
                        user,
                        LocalDate.of(2026, 8, 3),
                        LocalDate.of(2026, 8, 3),
                        LocalTime.of(13, 0),
                        LocalTime.of(17, 0)
                )
        );

        assertTrue(
                error.getMessage().contains(
                        "пересекает изменение рабочего часового пояса"
                ),
                error.getMessage()
        );
    }

    @Test
    void contextBoundaryExactlyAtFactEndIsAllowed() {
        timezoneHistory.upsert(
                user,
                LocalDateTime.of(2026, 8, 3, 17, 0),
                "Asia/Yekaterinburg"
        );

        assertDoesNotThrow(
                () -> identity.resolve(
                        user,
                        LocalDate.of(2026, 8, 3),
                        LocalDate.of(2026, 8, 3),
                        LocalTime.of(13, 0),
                        LocalTime.of(17, 0)
                )
        );
    }
}
