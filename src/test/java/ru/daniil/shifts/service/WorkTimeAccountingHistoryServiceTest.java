package ru.daniil.shifts.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.WorkTimeAccountingMode;
import ru.daniil.shifts.model.WorkTimeAccountingTerm;
import ru.daniil.shifts.repo.UserRepository;
import ru.daniil.shifts.repo.WorkTimeAccountingTermRepository;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class WorkTimeAccountingHistoryServiceTest {

    @Autowired
    WorkTimeAccountingHistoryService history;

    @Autowired
    WorkTimeAccountingTermRepository terms;

    @Autowired
    UserRepository users;

    AppUser user;

    @BeforeEach
    void setUp() {
        user = users.saveAndFlush(
                new AppUser(
                        "work-mode-" + UUID.randomUUID().toString().substring(0, 12),
                        "{noop}irrelevant"
                )
        );
    }

    @Test
    void missingHistoryRemainsUnknownWithoutCompatibilityBaseline() {
        LocalDate date = LocalDate.of(2026, 8, 1);

        var resolution = history.resolveAt(user, date);

        assertFalse(resolution.ready());
        assertNull(resolution.fact());
        assertEquals(
                WorkTimeAccountingHistoryService.MODE_FACT_MISSING + ":" + date,
                resolution.blockingReason()
        );
        assertTrue(history.history(user).isEmpty());
        assertTrue(terms.findAll().isEmpty());
    }

    @Test
    void effectiveDatedTermsOwnHalfOpenHistoricalRanges() {
        var daily = history.upsert(
                user,
                LocalDate.of(2025, 1, 1),
                WorkTimeAccountingMode.DAILY
        );
        var summarized = history.upsert(
                user,
                LocalDate.of(2026, 4, 15),
                WorkTimeAccountingMode.SUMMARIZED
        );

        assertEquals(
                daily.termId(),
                history.resolveAt(user, LocalDate.of(2026, 4, 14)).fact().termId()
        );
        assertEquals(
                WorkTimeAccountingMode.DAILY,
                history.resolveAt(user, LocalDate.of(2026, 4, 14)).fact().mode()
        );
        assertEquals(
                summarized.termId(),
                history.resolveAt(user, LocalDate.of(2026, 4, 15)).fact().termId()
        );
        assertEquals(
                WorkTimeAccountingMode.SUMMARIZED,
                history.resolveAt(user, LocalDate.of(2035, 1, 1)).fact().mode()
        );
    }

    @Test
    void rangeResolutionPreservesEveryPersistedAuthorityBoundary() {
        var first = history.upsert(
                user,
                LocalDate.of(2025, 1, 1),
                WorkTimeAccountingMode.DAILY
        );
        var second = history.upsert(
                user,
                LocalDate.of(2025, 6, 10),
                WorkTimeAccountingMode.SUMMARIZED
        );
        var third = history.upsert(
                user,
                LocalDate.of(2025, 9, 1),
                WorkTimeAccountingMode.DAILY
        );

        var resolved = history.resolveRange(
                user,
                LocalDate.of(2025, 5, 1),
                LocalDate.of(2025, 10, 31)
        );

        assertTrue(resolved.ready());
        assertNull(resolved.blockingReason());
        assertEquals(3, resolved.slices().size());

        var one = resolved.slices().get(0);
        assertEquals(first.termId(), one.termId());
        assertEquals(LocalDate.of(2025, 5, 1), one.overlapFrom());
        assertEquals(LocalDate.of(2025, 6, 9), one.overlapTo());
        assertEquals(WorkTimeAccountingMode.DAILY, one.mode());

        var two = resolved.slices().get(1);
        assertEquals(second.termId(), two.termId());
        assertEquals(LocalDate.of(2025, 6, 10), two.overlapFrom());
        assertEquals(LocalDate.of(2025, 8, 31), two.overlapTo());
        assertEquals(WorkTimeAccountingMode.SUMMARIZED, two.mode());

        var three = resolved.slices().get(2);
        assertEquals(third.termId(), three.termId());
        assertEquals(LocalDate.of(2025, 9, 1), three.overlapFrom());
        assertEquals(LocalDate.of(2025, 10, 31), three.overlapTo());
        assertEquals(WorkTimeAccountingMode.DAILY, three.mode());
    }

    @Test
    void uncoveredRangeStartBlocksWithoutLeakingLaterPartialFacts() {
        history.upsert(
                user,
                LocalDate.of(2025, 6, 1),
                WorkTimeAccountingMode.SUMMARIZED
        );

        LocalDate from = LocalDate.of(2025, 1, 1);
        var blocked = history.resolveRange(
                user,
                from,
                LocalDate.of(2025, 12, 31)
        );

        assertFalse(blocked.ready());
        assertTrue(blocked.slices().isEmpty());
        assertEquals(
                WorkTimeAccountingHistoryService.MODE_FACT_MISSING + ":" + from,
                blocked.blockingReason()
        );
    }

    @Test
    void exactEffectiveDateUpsertReplacesModeWithoutCreatingDuplicateIdentity() {
        LocalDate effective = LocalDate.of(2026, 3, 1);

        var first = history.upsert(
                user,
                effective,
                WorkTimeAccountingMode.DAILY
        );
        var replaced = history.upsert(
                user,
                effective,
                WorkTimeAccountingMode.SUMMARIZED
        );

        assertEquals(first.termId(), replaced.termId());
        assertEquals(1, history.history(user).size());
        assertEquals(
                WorkTimeAccountingMode.SUMMARIZED,
                history.resolveAt(user, effective).fact().mode()
        );
    }

    @Test
    void deletingBoundaryRestoresPreviousAuthorityButNeverInventsEarlierHistory() {
        LocalDate first = LocalDate.of(2025, 2, 1);
        LocalDate second = LocalDate.of(2026, 2, 1);

        history.upsert(user, first, WorkTimeAccountingMode.DAILY);
        history.upsert(user, second, WorkTimeAccountingMode.SUMMARIZED);

        history.delete(user, second);

        assertEquals(
                WorkTimeAccountingMode.DAILY,
                history.resolveAt(user, LocalDate.of(2026, 8, 1)).fact().mode()
        );

        history.delete(user, first);

        assertFalse(
                history.resolveAt(user, LocalDate.of(2026, 8, 1)).ready()
        );
    }

    @Test
    void repositoryLookupIsOwnerScopedAndUsesLatestTermNotAfterDate() {
        AppUser other = users.saveAndFlush(
                new AppUser(
                        "work-mode-other-" + UUID.randomUUID().toString().substring(0, 8),
                        "{noop}irrelevant"
                )
        );

        history.upsert(
                user,
                LocalDate.of(2026, 1, 1),
                WorkTimeAccountingMode.DAILY
        );
        history.upsert(
                user,
                LocalDate.of(2026, 7, 1),
                WorkTimeAccountingMode.SUMMARIZED
        );
        history.upsert(
                other,
                LocalDate.of(2025, 1, 1),
                WorkTimeAccountingMode.SUMMARIZED
        );

        WorkTimeAccountingTerm effective = terms
                .findFirstByOwnerAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                        user,
                        LocalDate.of(2026, 6, 30)
                )
                .orElseThrow();

        assertEquals(LocalDate.of(2026, 1, 1), effective.getEffectiveFrom());
        assertEquals(WorkTimeAccountingMode.DAILY, effective.getAccountingMode());
        assertEquals(2, history.history(user).size());
        assertEquals(1, history.history(other).size());
    }

    @Test
    void sameModeReplacementBoundaryIsStillPreservedForAuditProvenance() {
        var first = history.upsert(
                user,
                LocalDate.of(2025, 1, 1),
                WorkTimeAccountingMode.DAILY
        );
        var second = history.upsert(
                user,
                LocalDate.of(2025, 7, 1),
                WorkTimeAccountingMode.DAILY
        );

        var range = history.resolveRange(
                user,
                LocalDate.of(2025, 6, 1),
                LocalDate.of(2025, 8, 1)
        );

        assertEquals(2, range.slices().size());
        assertEquals(first.termId(), range.slices().get(0).termId());
        assertEquals(second.termId(), range.slices().get(1).termId());
        assertEquals(LocalDate.of(2025, 6, 30), range.slices().get(0).overlapTo());
        assertEquals(LocalDate.of(2025, 7, 1), range.slices().get(1).overlapFrom());
    }

    @Test
    void invalidArgumentsAndRecordContradictionsFailClosed() {
        assertThrows(
                NullPointerException.class,
                () -> history.resolveAt(null, LocalDate.of(2026, 1, 1))
        );
        assertThrows(
                NullPointerException.class,
                () -> history.upsert(user, LocalDate.of(2026, 1, 1), null)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> history.resolveRange(
                        user,
                        LocalDate.of(2026, 2, 1),
                        LocalDate.of(2026, 1, 31)
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new WorkTimeAccountingHistoryService.ModeSlice(
                        1L,
                        LocalDate.of(2026, 2, 1),
                        WorkTimeAccountingMode.DAILY,
                        LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 1, 31)
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> WorkTimeAccountingHistoryService.RangeResolution.ready(
                        LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 1, 31),
                        java.util.List.of()
                )
        );
    }
}
