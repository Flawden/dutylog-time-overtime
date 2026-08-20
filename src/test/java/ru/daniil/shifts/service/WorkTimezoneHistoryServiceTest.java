package ru.daniil.shifts.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.repo.UserRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class WorkTimezoneHistoryServiceTest {

    @Autowired
    WorkTimezoneHistoryService history;

    @Autowired
    UserRepository users;

    AppUser user;

    @BeforeEach
    void setUp() {
        user = users.save(
                new AppUser(
                        "timezone-history-user",
                        "{noop}irrelevant"
                )
        );
    }

    @Test
    void baselinePreservesCurrentProfileTimezone() {
        user.setWorkTimezone("Europe/Chisinau");
        users.save(user);

        assertEquals(
                "Europe/Chisinau",
                history.zoneAt(
                        user,
                        LocalDate.of(2025, 1, 1)
                ).getId()
        );

        var rows = history.history(user);

        assertEquals(1, rows.size());
        assertEquals(
                WorkTimezoneHistoryService.BASELINE_EFFECTIVE_FROM,
                rows.get(0).getEffectiveFrom()
        );
        assertEquals(
                "Europe/Chisinau",
                rows.get(0).getTimezoneId()
        );
    }

    @Test
    void termsOwnHalfOpenRangesUntilTheNextChange() {
        history.upsert(
                user,
                LocalDate.of(2026, 3, 3).atStartOfDay(),
                "Asia/Yekaterinburg"
        );

        history.upsert(
                user,
                LocalDate.of(2026, 8, 3).atStartOfDay(),
                "Europe/Moscow"
        );

        assertEquals(
                "Europe/Moscow",
                history.zoneAt(
                        user,
                        LocalDate.of(2026, 3, 2)
                ).getId()
        );

        assertEquals(
                "Asia/Yekaterinburg",
                history.zoneAt(
                        user,
                        LocalDate.of(2026, 3, 3)
                ).getId()
        );

        assertEquals(
                "Asia/Yekaterinburg",
                history.zoneAt(
                        user,
                        LocalDate.of(2026, 8, 2)
                ).getId()
        );

        assertEquals(
                "Europe/Moscow",
                history.zoneAt(
                        user,
                        LocalDate.of(2026, 8, 3)
                ).getId()
        );

        assertEquals(
                "Europe/Moscow",
                history.zoneAt(
                        user,
                        LocalDate.of(2035, 1, 1)
                ).getId()
        );
    }

    @Test
    void insertingMiddleTermOnlyChangesUntilExistingNextBoundary() {
        history.upsert(
                user,
                LocalDate.of(2026, 3, 3).atStartOfDay(),
                "Asia/Yekaterinburg"
        );

        history.upsert(
                user,
                LocalDate.of(2026, 8, 3).atStartOfDay(),
                "Europe/Moscow"
        );

        var change = history.upsert(
                user,
                LocalDate.of(2026, 5, 15).atStartOfDay(),
                "Europe/Samara"
        );

        assertEquals(
                LocalDateTime.of(2026, 5, 15, 0, 0),
                change.effectiveFrom()
        );

        assertEquals(
                LocalDateTime.of(2026, 8, 3, 0, 0),
                change.effectiveToExclusive()
        );

        assertEquals(
                "Asia/Yekaterinburg",
                change.previousTimezone()
        );

        assertEquals(
                "Europe/Samara",
                change.timezone()
        );

        assertEquals(
                "Asia/Yekaterinburg",
                history.zoneAt(
                        user,
                        LocalDate.of(2026, 5, 14)
                ).getId()
        );

        assertEquals(
                "Europe/Samara",
                history.zoneAt(
                        user,
                        LocalDate.of(2026, 5, 15)
                ).getId()
        );

        assertEquals(
                "Europe/Moscow",
                history.zoneAt(
                        user,
                        LocalDate.of(2026, 8, 3)
                ).getId()
        );
    }

    @Test
    void replacingExistingTermKeepsItsSameAffectedWindow() {
        history.upsert(
                user,
                LocalDate.of(2026, 3, 3).atStartOfDay(),
                "Asia/Yekaterinburg"
        );

        history.upsert(
                user,
                LocalDate.of(2026, 8, 3).atStartOfDay(),
                "Europe/Moscow"
        );

        var changed = history.upsert(
                user,
                LocalDate.of(2026, 3, 3).atStartOfDay(),
                "Europe/Samara"
        );

        assertEquals(
                "Asia/Yekaterinburg",
                changed.previousTimezone()
        );

        assertEquals(
                LocalDateTime.of(2026, 8, 3, 0, 0),
                changed.effectiveToExclusive()
        );

        assertEquals(
                "Europe/Samara",
                history.zoneAt(
                        user,
                        LocalDate.of(2026, 4, 1)
                ).getId()
        );

        assertEquals(
                "Europe/Moscow",
                history.zoneAt(
                        user,
                        LocalDate.of(2026, 8, 3)
                ).getId()
        );
    }

    @Test
    void preciseMomentInsideOneDayIsSupportedByDomain() {
        history.upsert(
                user,
                LocalDateTime.of(2026, 8, 3, 14, 0),
                "Asia/Yekaterinburg"
        );

        assertEquals(
                "Europe/Moscow",
                history.zoneAt(
                        user,
                        LocalDateTime.of(2026, 8, 3, 13, 59)
                ).getId()
        );

        assertEquals(
                "Asia/Yekaterinburg",
                history.zoneAt(
                        user,
                        LocalDateTime.of(2026, 8, 3, 14, 0)
                ).getId()
        );
    }
}
