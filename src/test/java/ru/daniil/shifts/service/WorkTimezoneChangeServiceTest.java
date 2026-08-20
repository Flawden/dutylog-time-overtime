package ru.daniil.shifts.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.daniil.shifts.dto.Dtos.ActualWorkIntervalRequest;
import ru.daniil.shifts.dto.Dtos.OvertimeUsageCreateRequest;
import ru.daniil.shifts.model.ActualWorkInterval;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.repo.ActualWorkIntervalRepository;
import ru.daniil.shifts.repo.UserRepository;
import ru.daniil.shifts.service.exception.ApiException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class WorkTimezoneChangeServiceTest {

    @Autowired
    WorkTimezoneChangeService changes;

    @Autowired
    WorkTimezoneHistoryService history;

    @Autowired
    ActualWorkService actualWorkService;

    @Autowired
    ActualWorkIntervalRepository actualWork;

    @Autowired
    OvertimeService overtime;

    @Autowired
    LedgerIntegrityService ledger;

    @Autowired
    UserRepository users;

    private AppUser newUser(String zone) {
        AppUser user = new AppUser(
                "tz-change-" + UUID.randomUUID().toString().substring(0, 12),
                "{noop}irrelevant"
        );
        user.setWorkTimezone(zone);
        return users.save(user);
    }

    @Test
    void middleTermReconstructsOnlyItsOwnHalfOpenWindow() {
        AppUser user = newUser("Europe/Moscow");

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

        var june = actualWorkService.create(
                user,
                new ActualWorkIntervalRequest(
                        "2026-06-01",
                        "2026-06-01",
                        "08:00",
                        "16:00",
                        0,
                        "inside middle window"
                )
        );

        var september = actualWorkService.create(
                user,
                new ActualWorkIntervalRequest(
                        "2026-09-01",
                        "2026-09-01",
                        "08:00",
                        "16:00",
                        0,
                        "after next boundary"
                )
        );

        assertEquals(
                "Asia/Yekaterinburg",
                june.sourceTimezone()
        );

        assertEquals(
                "Europe/Moscow",
                september.sourceTimezone()
        );

        var result = changes.upsertAndReconcile(
                user,
                LocalDate.of(2026, 5, 15).atStartOfDay(),
                "Europe/Samara"
        );

        assertEquals(
                LocalDateTime.of(2026, 5, 15, 0, 0),
                result.effectiveFrom()
        );

        assertEquals(
                LocalDateTime.of(2026, 8, 3, 0, 0),
                result.effectiveToExclusive()
        );

        assertEquals(1, result.reconstructedIntervals());

        ActualWorkInterval juneReloaded =
                actualWork.findByOwnerAndId(
                        user,
                        june.id()
                ).orElseThrow();

        ActualWorkInterval septemberReloaded =
                actualWork.findByOwnerAndId(
                        user,
                        september.id()
                ).orElseThrow();

        assertEquals(
                "Europe/Samara",
                juneReloaded.getSourceTimezone()
        );
        assertTrue(
                juneReloaded.isIdentityReconstructed()
        );

        assertEquals(
                "Europe/Moscow",
                septemberReloaded.getSourceTimezone()
        );
        assertFalse(
                septemberReloaded.isIdentityReconstructed()
        );
    }

    @Test
    void historicalTimezoneCorrectionCanRebuildActualWorkInClosedMonth() {
        AppUser user = newUser("UTC");

        var saved = actualWorkService.create(
                user,
                new ActualWorkIntervalRequest(
                        "2026-08-18",
                        "2026-08-18",
                        "08:00",
                        "16:00",
                        0,
                        "closed-period proof"
                )
        );

        ledger.closePeriod(user, "2026-08");

        var result = changes.upsertAndReconcile(
                user,
                LocalDate.of(2026, 8, 1).atStartOfDay(),
                "Europe/Moscow"
        );

        assertEquals(1, result.reconstructedIntervals());

        ActualWorkInterval reloaded =
                actualWork.findByOwnerAndId(
                        user,
                        saved.id()
                ).orElseThrow();

        assertEquals(
                "Europe/Moscow",
                reloaded.getSourceTimezone()
        );
        assertTrue(
                reloaded.isIdentityReconstructed()
        );
    }

    @Test
    void usedDerivedOvertimeConflictRollsBackTimezoneAndActualIdentity() {
        AppUser user = newUser("UTC");

        var saved = actualWorkService.create(
                user,
                new ActualWorkIntervalRequest(
                        "2026-03-29",
                        "2026-03-29",
                        "00:00",
                        "08:00",
                        0,
                        "DST rollback proof"
                )
        );

        assertEquals("UTC", saved.sourceTimezone());
        assertEquals(480, saved.workedMinutes());

        /*
         * With no planned shift this explicit 8h fact creates an 8h
         * SYSTEM_ACTUAL_WORK derived credit under the current semantics.
         */
        overtime.createUsage(
                user,
                new OvertimeUsageCreateRequest(
                        "2026-03-30",
                        8.0,
                        "consume the derived credit"
                )
        );

        ApiException error = assertThrows(
                ApiException.class,
                () -> changes.upsertAndReconcile(
                        user,
                        LocalDate.of(2026, 3, 29)
                                .atStartOfDay(),
                        "Europe/Berlin"
                )
        );

        assertTrue(
                error.getMessage().contains("уже")
                        || error.getMessage().contains(
                                "DERIVED_OVERTIME_ALREADY_USED"
                        ),
                error.getMessage()
        );

        /*
         * Europe/Berlin spring-forward would have turned 00:00-08:00
         * into 420 real minutes. Because all 480 derived minutes were already
         * consumed, the change must fail atomically.
         */

        ActualWorkInterval actualReloaded =
                actualWork.findByOwnerAndId(
                        user,
                        saved.id()
                ).orElseThrow();

        assertEquals(
                "UTC",
                actualReloaded.getSourceTimezone()
        );

        assertEquals(480, actualReloaded.getWorkedMinutes());

        assertFalse(
                actualReloaded.isIdentityReconstructed()
        );

        assertFalse(
                history.history(user).stream().anyMatch(
                        term -> term.getEffectiveFrom().equals(
                                LocalDate.of(2026, 3, 29)
                                        .atStartOfDay()
                        )
                ),
                "failed correction must roll back inserted timezone term"
        );

        var account = overtime.account(user);

        var derived = account.credits().stream()
                .filter(row ->
                        "2026-03-29".equals(row.workedDate())
                )
                .filter(row ->
                        "SYSTEM_ACTUAL_WORK".equals(row.sourceKind())
                )
                .findFirst()
                .orElseThrow();

        assertEquals(
                480,
                derived.creditedMinutes(),
                "failed correction must preserve original derived credit"
        );

        assertEquals(
                8.0,
                account.totalUsedHours(),
                0.001
        );
    }

    @Test
    void timezoneBoundaryInsideActualWorkRollsBackTermAndIdentity() {
        AppUser user = newUser("UTC");

        var saved = actualWorkService.create(
                user,
                new ActualWorkIntervalRequest(
                        "2026-08-10",
                        "2026-08-10",
                        "13:00",
                        "17:00",
                        0,
                        "boundary rollback proof"
                )
        );

        assertEquals("UTC", saved.sourceTimezone());
        assertEquals(240, saved.workedMinutes());
        assertFalse(saved.identityReconstructed());

        LocalDateTime boundary =
                LocalDateTime.of(2026, 8, 10, 14, 0);

        assertThrows(
                ApiException.class,
                () -> changes.upsertAndReconcile(
                        user,
                        boundary,
                        "Europe/Moscow"
                )
        );

        /*
         * The newly inserted context boundary lies strictly inside the
         * 13:00-17:00 factual interval. The correction is invalid, therefore
         * both the Work Context term and any attempted identity reconstruction
         * must roll back atomically.
         */
        ActualWorkInterval reloaded =
                actualWork.findByOwnerAndId(
                        user,
                        saved.id()
                ).orElseThrow();

        assertEquals(
                "UTC",
                reloaded.getSourceTimezone()
        );
        assertEquals(
                240,
                reloaded.getWorkedMinutes()
        );
        assertFalse(
                reloaded.isIdentityReconstructed()
        );

        assertFalse(
                history.history(user).stream().anyMatch(
                        term -> term.getEffectiveFrom().equals(boundary)
                ),
                "failed boundary insertion must roll back timezone term"
        );
    }

}
