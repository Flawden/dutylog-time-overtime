package ru.daniil.shifts.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.dto.Dtos.ActualWorkIntervalRequest;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.repo.UserRepository;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class ActualWorkServiceHistoricalIdentityTest {

    @Autowired
    ActualWorkService actualWork;

    @Autowired
    WorkTimezoneHistoryService timezoneHistory;

    @Autowired
    UserRepository users;

    AppUser user;

    @BeforeEach
    void setUp() {
        user = users.save(
                new AppUser(
                        "actual-work-service-identity-user",
                        "{noop}irrelevant"
                )
        );
    }

    @Test
    void createPersistsHistoricalIdentityAndRealDstMinutes() {
        timezoneHistory.upsert(
                user,
                LocalDate.of(2026, 3, 1).atStartOfDay(),
                "Europe/Berlin"
        );

        var saved = actualWork.create(
                user,
                new ActualWorkIntervalRequest(
                        "2026-03-29",
                        "2026-03-29",
                        "00:00",
                        "08:00",
                        0,
                        "DST proof"
                )
        );

        assertEquals("Europe/Berlin", saved.sourceTimezone());
        assertEquals(
                "2026-03-28T23:00:00Z",
                saved.startInstant()
        );
        assertEquals(
                "2026-03-29T06:00:00Z",
                saved.endInstant()
        );

        assertEquals(420, saved.workedMinutes());
        assertFalse(saved.identityReconstructed());
    }
}
