package ru.daniil.shifts.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.dto.Dtos.MobileDayChangeRequest;
import ru.daniil.shifts.dto.Dtos.MobileSyncItemResultDto;
import ru.daniil.shifts.dto.Dtos.MobileV1DayOperationRequest;
import ru.daniil.shifts.dto.Dtos.MobileV1SyncRequest;
import ru.daniil.shifts.dto.Dtos.MobileV1SyncResultDto;
import ru.daniil.shifts.dto.Dtos.ModuleSettingsUpdateRequest;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.DayEntry;
import ru.daniil.shifts.repo.DayEntryRepository;
import ru.daniil.shifts.repo.MobileSyncOperationRepository;
import ru.daniil.shifts.repo.UserRepository;
import ru.daniil.shifts.service.exception.ApiException;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Service-level contract for the idempotent Android v1 offline queue. */
@SpringBootTest
@Transactional
class MobileSyncServiceTest {

    @Autowired MobileSyncService mobileSyncService;
    @Autowired MobileSyncOperationRepository operations;
    @Autowired DayEntryRepository days;
    @Autowired UserRepository users;
    @Autowired ModuleService moduleService;
    @Autowired ShiftTypeService shiftTypeService;

    AppUser owner;
    AppUser other;

    @BeforeEach
    void setUp() {
        owner = users.save(new AppUser("mobile-sync-owner", "{noop}unused"));
        other = users.save(new AppUser("mobile-sync-other", "{noop}unused"));
    }

    @Test
    void nullEmptyAndStructurallyMalformedRequestsUseValidationFailed() {
        assertValidationFailed(() -> mobileSyncService.sync(owner, null));
        assertValidationFailed(() -> mobileSyncService.sync(owner, new MobileV1SyncRequest(null)));
        assertValidationFailed(() -> mobileSyncService.sync(owner, new MobileV1SyncRequest(List.of())));
        assertValidationFailed(() -> mobileSyncService.sync(owner,
                new MobileV1SyncRequest(Arrays.asList((MobileV1DayOperationRequest) null))));
        assertValidationFailed(() -> mobileSyncService.sync(owner,
                new MobileV1SyncRequest(List.of(new MobileV1DayOperationRequest(
                        "", 0L, change("2026-08-01", "note"))))));
        assertValidationFailed(() -> mobileSyncService.sync(owner,
                new MobileV1SyncRequest(List.of(new MobileV1DayOperationRequest(
                        "missing-version", null, change("2026-08-01", "note"))))));
        assertValidationFailed(() -> mobileSyncService.sync(owner,
                new MobileV1SyncRequest(List.of(new MobileV1DayOperationRequest(
                        "missing-day", 0L, null)))));
    }

    @Test
    void appliedOperationIsStoredOnceAndReplayedAsAlreadyApplied() {
        MobileV1DayOperationRequest operation = operation(
                "sync-apply-once", 0L, change("2026-08-01", "first note"));

        MobileSyncItemResultDto first = only(mobileSyncService.sync(owner, request(operation)));
        MobileSyncItemResultDto replay = only(mobileSyncService.sync(owner, request(operation)));

        assertEquals("APPLIED", first.status());
        assertEquals(1L, first.serverVersion());
        assertEquals("first note", first.entity().note());
        assertEquals("ALREADY_APPLIED", replay.status());
        assertEquals(1L, replay.serverVersion());
        assertNull(replay.entity());
        assertEquals(1, operations.count());
        assertEquals("first note", days.findByOwnerAndDate(owner, LocalDate.parse("2026-08-01"))
                .orElseThrow().getNote());
    }

    @Test
    void operationIdNamespaceIsIsolatedPerOwner() {
        MobileV1DayOperationRequest sameIdForOwner = operation(
                "shared-operation-id", 0L, change("2026-08-02", "owner note"));
        MobileV1DayOperationRequest sameIdForOther = operation(
                "shared-operation-id", 0L, change("2026-08-02", "other note"));

        assertEquals("APPLIED", only(mobileSyncService.sync(owner, request(sameIdForOwner))).status());
        assertEquals("APPLIED", only(mobileSyncService.sync(other, request(sameIdForOther))).status());

        assertEquals(2, operations.count());
        assertEquals("owner note", days.findByOwnerAndDate(owner, LocalDate.parse("2026-08-02"))
                .orElseThrow().getNote());
        assertEquals("other note", days.findByOwnerAndDate(other, LocalDate.parse("2026-08-02"))
                .orElseThrow().getNote());
    }

    @Test
    void staleBaseVersionConflictsAndTheConflictItselfIsIdempotent() {
        MobileSyncItemResultDto created = only(mobileSyncService.sync(owner, request(operation(
                "create-versioned-day", 0L, change("2026-08-03", "v1")))));
        assertEquals(1L, created.serverVersion());

        MobileV1DayOperationRequest stale = operation(
                "stale-version-write", 0L, change("2026-08-03", "must not win"));
        MobileSyncItemResultDto conflict = only(mobileSyncService.sync(owner, request(stale)));
        MobileSyncItemResultDto replay = only(mobileSyncService.sync(owner, request(stale)));

        assertEquals("CONFLICT", conflict.status());
        assertEquals("VERSION_CONFLICT", conflict.errorCode());
        assertEquals(1L, conflict.serverVersion());
        assertEquals("CONFLICT", replay.status());
        assertEquals("VERSION_CONFLICT", replay.errorCode());
        assertEquals("v1", days.findByOwnerAndDate(owner, LocalDate.parse("2026-08-03"))
                .orElseThrow().getNote());

        MobileSyncItemResultDto corrected = only(mobileSyncService.sync(owner, request(operation(
                "correct-version-write", 1L, change("2026-08-03", "v2")))));
        assertEquals("APPLIED", corrected.status());
        assertEquals(2L, corrected.serverVersion());
    }

    @Test
    void noChangesAreRejectedWithoutCreatingADayRowAndReplayTheSameResult() {
        MobileV1DayOperationRequest noChanges = new MobileV1DayOperationRequest(
                "no-changes", 0L,
                new MobileDayChangeRequest("2026-08-04", null, null, null, null,
                        null, null, null, null));

        MobileSyncItemResultDto first = only(mobileSyncService.sync(owner, request(noChanges)));
        MobileSyncItemResultDto replay = only(mobileSyncService.sync(owner, request(noChanges)));

        assertEquals("REJECTED", first.status());
        assertEquals("NO_CHANGES", first.errorCode());
        assertEquals(0L, first.serverVersion());
        assertEquals("REJECTED", replay.status());
        assertEquals("NO_CHANGES", replay.errorCode());
        assertTrue(days.findByOwnerAndDate(owner, LocalDate.parse("2026-08-04")).isEmpty());
        assertEquals(1, operations.count());
    }

    @Test
    void disabledOptionalModuleRejectsOnlyItsItemAndDoesNotBlockNeighbour() {
        moduleService.update(owner, new ModuleSettingsUpdateRequest(Map.of("notes", false)));

        MobileV1SyncResultDto result = mobileSyncService.sync(owner, new MobileV1SyncRequest(List.of(
                operation("disabled-note", 0L, change("2026-08-05", "blocked")),
                operation("allowed-emoji", 0L,
                        new MobileDayChangeRequest("2026-08-06", null, null, null, null,
                                "✅", null, null, null))
        )));

        assertEquals(2, result.items().size());
        assertEquals("REJECTED", result.items().get(0).status());
        assertEquals("MODULE_DISABLED", result.items().get(0).errorCode());
        assertEquals("APPLIED", result.items().get(1).status());
        assertTrue(days.findByOwnerAndDate(owner, LocalDate.parse("2026-08-05")).isEmpty());
        assertEquals("✅", days.findByOwnerAndDate(owner, LocalDate.parse("2026-08-06"))
                .orElseThrow().getDayEmoji());
    }

    @Test
    void malformedDateIsAPerItemRejectionAndNoLongerAbortsTheBatch() {
        MobileV1DayOperationRequest malformed = operation(
                "malformed-date", 0L, change("not-a-date", "blocked"));
        MobileV1DayOperationRequest neighbour = operation(
                "valid-neighbour", 0L, change("2026-08-07", "saved"));

        MobileV1SyncResultDto result = mobileSyncService.sync(owner,
                new MobileV1SyncRequest(List.of(malformed, neighbour)));

        assertEquals("REJECTED", result.items().get(0).status());
        assertEquals("BAD_REQUEST", result.items().get(0).errorCode());
        assertEquals("not-a-date", result.items().get(0).entityId());
        assertNull(result.items().get(0).serverVersion());
        assertEquals("APPLIED", result.items().get(1).status());
        assertEquals("saved", days.findByOwnerAndDate(owner, LocalDate.parse("2026-08-07"))
                .orElseThrow().getNote());

        MobileSyncItemResultDto replay = only(mobileSyncService.sync(owner, request(malformed)));
        assertEquals("REJECTED", replay.status());
        assertEquals("BAD_REQUEST", replay.errorCode());
        assertEquals(2, operations.count());
    }

    @Test
    void clearCreatesAVersionedTombstoneSoStaleOfflineCreatesCannotOverwriteIt() {
        MobileSyncItemResultDto created = only(mobileSyncService.sync(owner, request(operation(
                "tombstone-create", 0L, change("2026-08-08", "temporary")))));
        assertEquals(1L, created.serverVersion());

        MobileDayChangeRequest clear = new MobileDayChangeRequest(
                "2026-08-08", null, null, null, true, null, null, null, null);
        MobileSyncItemResultDto cleared = only(mobileSyncService.sync(owner, request(operation(
                "tombstone-clear", 1L, clear))));
        assertEquals("APPLIED", cleared.status());
        assertEquals(2L, cleared.serverVersion());
        assertNull(cleared.entity().note());

        DayEntry tombstone = days.findByOwnerAndDate(owner, LocalDate.parse("2026-08-08")).orElseThrow();
        assertTrue(tombstone.isEmpty());
        assertEquals(2L, tombstone.getSyncVersion());

        MobileSyncItemResultDto staleCreate = only(mobileSyncService.sync(owner, request(operation(
                "stale-after-clear", 0L, change("2026-08-08", "resurrected")))));
        assertEquals("CONFLICT", staleCreate.status());
        assertEquals(2L, staleCreate.serverVersion());
        assertTrue(days.findByOwnerAndDate(owner, LocalDate.parse("2026-08-08")).orElseThrow().isEmpty());
    }

    @Test
    void explicitClearFlagsWinOverValuesInTheSamePatch() {
        MobileDayChangeRequest initial = new MobileDayChangeRequest(
                "2026-08-09", null, null, "note", null, "🔥", null, null, null);
        only(mobileSyncService.sync(owner, request(operation("clear-precedence-create", 0L, initial))));

        MobileDayChangeRequest contradictory = new MobileDayChangeRequest(
                "2026-08-09", null, null, "ignored note", true, "ignored emoji", true, null, null);
        MobileSyncItemResultDto cleared = only(mobileSyncService.sync(owner, request(operation(
                "clear-precedence-update", 1L, contradictory))));

        assertEquals("APPLIED", cleared.status());
        assertNull(cleared.entity().note());
        assertNull(cleared.entity().dayEmoji());
        assertTrue(days.findByOwnerAndDate(owner, LocalDate.parse("2026-08-09")).orElseThrow().isEmpty());
    }

    @Test
    void foreignShiftIsRejectedWithoutLeakingItAndAValidNeighbourStillApplies() {
        Long foreignShiftId = shiftTypeService.list(other).get(0).id();
        MobileDayChangeRequest foreignShift = new MobileDayChangeRequest(
                "2026-08-10", foreignShiftId, null, null, null, null, null, null, null);
        MobileDayChangeRequest valid = new MobileDayChangeRequest(
                "2026-08-11", null, null, null, null, "🛠️", null, null, null);

        MobileV1SyncResultDto result = mobileSyncService.sync(owner, new MobileV1SyncRequest(List.of(
                operation("foreign-shift", 0L, foreignShift),
                operation("foreign-shift-neighbour", 0L, valid)
        )));

        assertEquals("REJECTED", result.items().get(0).status());
        assertEquals("NOT_FOUND", result.items().get(0).errorCode());
        assertFalse(result.items().get(0).message().contains(other.getUsername()));
        assertEquals("APPLIED", result.items().get(1).status());
        assertTrue(days.findByOwnerAndDate(owner, LocalDate.parse("2026-08-10")).isEmpty());
        assertEquals("🛠️", days.findByOwnerAndDate(owner, LocalDate.parse("2026-08-11"))
                .orElseThrow().getDayEmoji());
    }

    private static MobileV1SyncRequest request(MobileV1DayOperationRequest operation) {
        return new MobileV1SyncRequest(List.of(operation));
    }

    private static MobileV1DayOperationRequest operation(String id, Long baseVersion,
                                                           MobileDayChangeRequest change) {
        return new MobileV1DayOperationRequest(id, baseVersion, change);
    }

    private static MobileDayChangeRequest change(String date, String note) {
        return new MobileDayChangeRequest(date, null, null, note, null,
                null, null, null, null);
    }

    private static MobileSyncItemResultDto only(MobileV1SyncResultDto result) {
        assertEquals("v1", result.apiVersion());
        assertEquals(1, result.items().size());
        return result.items().get(0);
    }

    private static void assertValidationFailed(org.junit.jupiter.api.function.Executable action) {
        ApiException error = assertThrows(ApiException.class, action);
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
        assertEquals("VALIDATION_FAILED", error.getCode());
    }
}
