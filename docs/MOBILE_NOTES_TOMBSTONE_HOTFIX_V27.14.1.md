# DutyLog v27.14.1 — Mobile Notes Tombstone Hotfix

## Problem

Multiple Daily Notes keeps `day_entries.note` as a compatibility shadow of the first independent note. During Android API v1 `clearNote`, `DayEntryService` correctly saved an empty versioned day row, but the subsequent shadow synchronization deleted that same row because it was empty. The response still contained version 2, while the database no longer contained the tombstone.

Consequences:

- three mobile sync regression tests ended in `NoSuchElementException`;
- a stale offline create with `baseVersion=0` could potentially resurrect content after the clear;
- legacy and v1 clear semantics became accidentally identical.

## Fix

`DayNoteService.syncPrimaryFromLegacy` now accepts an explicit `preserveEmptyDayEntry` policy. `DayEntryService.patchMobileDayVersioned` forwards `true`; ordinary web and legacy mobile flows keep the default `false`.

Therefore:

- `/api/mobile/sync` still deletes a completely empty legacy row;
- `/api/v1/mobile/sync` keeps the empty row and its optimistic version;
- independent sibling notes and the primary compatibility shadow behave as before;
- clear flags still take precedence over contradictory values.

## Schema and compatibility

No database migration is required. Flyway remains V36. API payloads and endpoints are unchanged.

## Regression coverage

The existing three failing tests are the release gate for this hotfix:

- `MobileSyncControllerTest.legacyClearDeletesEmptyRowWhileV1ClearKeepsVersionedTombstone`;
- `MobileSyncServiceTest.clearCreatesAVersionedTombstoneSoStaleOfflineCreatesCannotOverwriteIt`;
- `MobileSyncServiceTest.explicitClearFlagsWinOverValuesInTheSamePatch`.
