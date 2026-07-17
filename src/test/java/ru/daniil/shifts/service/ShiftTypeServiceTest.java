package ru.daniil.shifts.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.dto.Dtos.DayUpsertRequest;
import ru.daniil.shifts.dto.Dtos.ShiftTypeCreateRequest;
import ru.daniil.shifts.dto.Dtos.ShiftTypeDto;
import ru.daniil.shifts.dto.Dtos.ShiftTypeUpdateRequest;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.DayEntry;
import ru.daniil.shifts.model.ShiftType;
import ru.daniil.shifts.repo.DayEntryRepository;
import ru.daniil.shifts.repo.ShiftTypeRepository;
import ru.daniil.shifts.repo.UserRepository;
import ru.daniil.shifts.service.exception.ApiException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Behavioural coverage for built-in and user-defined shift types. */
@SpringBootTest
@Transactional
class ShiftTypeServiceTest {

    @Autowired ShiftTypeService shiftTypeService;
    @Autowired DayEntryService dayEntryService;
    @Autowired ShiftTypeRepository shiftTypes;
    @Autowired DayEntryRepository days;
    @Autowired UserRepository users;

    AppUser owner;
    AppUser other;

    @BeforeEach
    void setUp() {
        owner = users.save(new AppUser("shift-type-service-owner", "{noop}unused"));
        other = users.save(new AppUser("shift-type-service-other", "{noop}unused"));
    }

    @Test
    void listSeedsTheThreeBuiltinsExactlyOnceWithExpectedDefaults() {
        List<ShiftTypeDto> first = shiftTypeService.list(owner);
        List<ShiftTypeDto> second = shiftTypeService.list(owner);

        assertEquals(3, first.size());
        assertEquals(3, second.size());
        Map<String, ShiftTypeDto> byName = first.stream()
                .collect(Collectors.toMap(ShiftTypeDto::name, java.util.function.Function.identity()));

        ShiftTypeDto day = byName.get("Дневная");
        assertTrue(day.builtin());
        assertEquals(8.0, day.hours());
        assertEquals("08:30", day.startTime());
        assertEquals("17:00", day.endTime());
        assertEquals(30, day.breakMinutes());
        assertEquals(8.0, day.plannedHours());

        ShiftTypeDto night = byName.get("Ночная");
        assertEquals("20:00", night.startTime());
        assertEquals("08:00", night.endTime());
        assertEquals(60, night.breakMinutes());
        assertEquals(11.0, night.plannedHours());

        ShiftTypeDto off = byName.get("Выходной");
        assertEquals(0.0, off.hours());
        assertNull(off.startTime());
        assertNull(off.endTime());
    }

    @Test
    void createTrimsTheNameAndAppliesSafeDefaults() {
        ShiftTypeDto created = shiftTypeService.create(owner, new ShiftTypeCreateRequest(
                "  Дежурная  ", null, null, "", null,
                null, null, false, -1));

        assertEquals("Дежурная", created.name());
        assertEquals(0.0, created.hours());
        assertEquals("#8B929E", created.color());
        assertFalse(created.builtin());
        assertNull(created.startTime());
        assertNull(created.endTime());
        assertEquals(0, created.breakMinutes());
        assertEquals(0.0, created.plannedHours());
        assertFalse(created.notificationsEnabled());
        assertNull(created.notificationMinutesBefore());
    }

    @Test
    void customShiftCanUpdateEveryFieldAndClearOptionalTimeOverrides() {
        ShiftTypeDto created = shiftTypeService.create(owner, new ShiftTypeCreateRequest(
                "Дежурная", 12.0, "#123456", "07:00", "19:00",
                45, 11.25, true, 30));

        ShiftTypeDto updated = shiftTypeService.update(owner, created.id(), new ShiftTypeUpdateRequest(
                "  Аварийная  ", 10.0, "#ABCDEF", "06:15", "16:45",
                60, 9.0, false, 15));
        assertEquals("Аварийная", updated.name());
        assertEquals(10.0, updated.hours());
        assertEquals("#ABCDEF", updated.color());
        assertEquals("06:15", updated.startTime());
        assertEquals("16:45", updated.endTime());
        assertEquals(60, updated.breakMinutes());
        assertEquals(9.0, updated.plannedHours());
        assertFalse(updated.notificationsEnabled());
        assertEquals(15, updated.notificationMinutesBefore());

        ShiftTypeDto cleared = shiftTypeService.update(owner, created.id(), new ShiftTypeUpdateRequest(
                null, null, null, "", "", null, null, null, -1));
        assertNull(cleared.startTime());
        assertNull(cleared.endTime());
        assertNull(cleared.notificationMinutesBefore());
    }

    @Test
    void builtinTimingIsEditableButIdentityAndDeletionAreProtected() {
        ShiftTypeDto day = shiftTypeService.list(owner).stream()
                .filter(s -> "Дневная".equals(s.name())).findFirst().orElseThrow();

        ShiftTypeDto updated = shiftTypeService.update(owner, day.id(), new ShiftTypeUpdateRequest(
                null, 8.5, null, "09:00", "18:00", 30, 8.5, true, 20));
        assertEquals("Дневная", updated.name());
        assertEquals("09:00", updated.startTime());
        assertEquals(8.5, updated.plannedHours());

        assertConflict(() -> shiftTypeService.update(owner, day.id(), new ShiftTypeUpdateRequest(
                "Новая дневная", null, null, null, null, null, null, null, null)));
        assertConflict(() -> shiftTypeService.update(owner, day.id(), new ShiftTypeUpdateRequest(
                null, null, "#000000", null, null, null, null, null, null)));
        assertConflict(() -> shiftTypeService.delete(owner, day.id()));
    }

    @Test
    void deletingCustomShiftDetachesItAndPreservesOtherDayData() {
        ShiftTypeDto custom = shiftTypeService.create(owner, new ShiftTypeCreateRequest(
                "Резерв", 4.0, "#112233", null, null, 0, 4.0, true, null));
        dayEntryService.upsert(owner, "2026-08-10",
                new DayUpsertRequest(custom.id(), null, null, null, null));
        dayEntryService.upsert(owner, "2026-08-11",
                new DayUpsertRequest(custom.id(), "сохранить", "🛠️", 2.5, 0.5));

        shiftTypeService.delete(owner, custom.id());

        assertTrue(shiftTypes.findById(custom.id()).isEmpty());
        assertTrue(days.findByOwnerAndDate(owner, LocalDate.parse("2026-08-10")).isEmpty(),
                "день, содержавший только удалённую смену, должен исчезнуть");
        DayEntry preserved = days.findByOwnerAndDate(owner, LocalDate.parse("2026-08-11")).orElseThrow();
        assertNull(preserved.getShiftType());
        assertEquals("сохранить", preserved.getNote());
        assertEquals("🛠️", preserved.getDayEmoji());
        assertEquals(2.5, preserved.getOvertimeHours());
        assertEquals(0.5, preserved.getTimeOffHours());
    }

    @Test
    void ensureBuiltinsRepairsLegacyDefaultsWithoutCreatingDuplicates() {
        ShiftType legacy = new ShiftType(owner, "Дневная", 8.0, "#123456", false);
        legacy.setStartTime(LocalTime.parse("06:30"));
        legacy.setEndTime(LocalTime.parse("17:00"));
        legacy.setBreakMinutes(0);
        legacy.setPlannedHours(null);
        shiftTypes.save(legacy);

        shiftTypeService.ensureBuiltinShiftTypes(owner);

        List<ShiftType> dayRows = shiftTypes.findByOwnerAndName(owner, "Дневная");
        assertEquals(1, dayRows.size());
        ShiftType repaired = dayRows.get(0);
        assertTrue(repaired.isBuiltin());
        assertEquals(LocalTime.parse("08:30"), repaired.getStartTime());
        assertEquals(LocalTime.parse("17:00"), repaired.getEndTime());
        assertEquals(30, repaired.getBreakMinutes());
        assertEquals(8.0, repaired.getPlannedHours());
        assertEquals("#123456", repaired.getColor(), "пользовательский цвет не должен сбрасываться");
        assertEquals(3, shiftTypes.findByOwner(owner).size());
    }

    @Test
    void nullBlankForeignAndMissingRequestsUseStableErrors() {
        assertBadRequest(() -> shiftTypeService.create(owner, null));
        assertBadRequest(() -> shiftTypeService.create(owner, new ShiftTypeCreateRequest(
                "   ", 8.0, "#123456", null, null, null, null, null, null)));

        ShiftTypeDto own = shiftTypeService.create(owner, new ShiftTypeCreateRequest(
                "Своя", 8.0, "#123456", null, null, null, null, null, null));
        assertBadRequest(() -> shiftTypeService.update(owner, own.id(), null));

        ShiftTypeDto foreign = shiftTypeService.create(other, new ShiftTypeCreateRequest(
                "Чужая", 8.0, "#654321", null, null, null, null, null, null));
        assertNotFound(() -> shiftTypeService.requireOwnedShiftType(owner, foreign.id()));
        assertNotFound(() -> shiftTypeService.update(owner, foreign.id(), new ShiftTypeUpdateRequest(
                "Взлом", null, null, null, null, null, null, null, null)));
        assertNotFound(() -> shiftTypeService.delete(owner, foreign.id()));
        assertNotFound(() -> shiftTypeService.delete(owner, Long.MAX_VALUE));
    }

    private static void assertBadRequest(org.junit.jupiter.api.function.Executable action) {
        ApiException error = assertThrows(ApiException.class, action);
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
        assertEquals("BAD_REQUEST", error.getCode());
    }

    private static void assertNotFound(org.junit.jupiter.api.function.Executable action) {
        ApiException error = assertThrows(ApiException.class, action);
        assertEquals(HttpStatus.NOT_FOUND, error.getStatus());
        assertEquals("NOT_FOUND", error.getCode());
    }

    private static void assertConflict(org.junit.jupiter.api.function.Executable action) {
        ApiException error = assertThrows(ApiException.class, action);
        assertEquals(HttpStatus.CONFLICT, error.getStatus());
        assertEquals("CONFLICT", error.getCode());
    }
}
