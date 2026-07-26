package ru.daniil.shifts.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.dto.Dtos.QuickScenarioCreateRequest;
import ru.daniil.shifts.dto.Dtos.QuickScenarioDto;
import ru.daniil.shifts.dto.Dtos.QuickScenarioUpdateRequest;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.repo.QuickScenarioRepository;
import ru.daniil.shifts.repo.UserRepository;
import ru.daniil.shifts.service.exception.ApiException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Behavioural coverage for quick overtime-form scenarios. */
@SpringBootTest
@Transactional
class QuickScenarioServiceTest {

    @Autowired QuickScenarioService quickScenarioService;
    @Autowired QuickScenarioRepository scenarios;
    @Autowired UserRepository users;

    AppUser owner;
    AppUser other;

    @BeforeEach
    void setUp() {
        owner = users.save(new AppUser("quick-scenario-service-owner", "{noop}unused"));
        other = users.save(new AppUser("quick-scenario-service-other", "{noop}unused"));
    }

    @Test
    void firstListSeedsFiveOrderedDefaultsExactlyOnce() {
        List<QuickScenarioDto> first = quickScenarioService.list(owner);
        List<QuickScenarioDto> second = quickScenarioService.list(owner);

        assertEquals(5, first.size());
        assertEquals(5, second.size());
        assertEquals(List.of("+2 часа", "+4 часа", "Остался в ночь", "Смена + ночь", "Обычная смена"),
                first.stream().map(QuickScenarioDto::name).toList());
        assertEquals(List.of(10, 20, 30, 40, 50),
                first.stream().map(QuickScenarioDto::sortOrder).toList());
        assertEquals(5, scenarios.findByOwnerOrderBySortOrderAscIdAsc(owner).size());
    }

    @Test
    void deletingASeededScenarioDoesNotRestoreItOnLaterLists() {
        QuickScenarioDto removed = quickScenarioService.list(owner).get(0);
        quickScenarioService.delete(owner, removed.id());

        List<QuickScenarioDto> afterDelete = quickScenarioService.list(owner);
        assertEquals(4, afterDelete.size());
        assertFalse(afterDelete.stream().anyMatch(s -> removed.name().equals(s.name())));
    }

    @Test
    void createTrimsTextAndAppliesSafeDefaults() {
        QuickScenarioDto created = quickScenarioService.create(owner, new QuickScenarioCreateRequest(
                "  После смены  ", "  группа  ", "  описание  ",
                null, null, null, null, null,
                null, null, null, null,
                "  причина  ", null));

        assertEquals("После смены", created.name());
        assertEquals("группа", created.groupLabel());
        assertEquals("описание", created.description());
        assertEquals("SHIFT_END", created.startMode());
        assertEquals("ADD_MINUTES", created.endMode());
        assertEquals(120, created.endOffsetMinutes());
        assertNull(created.endFixedTime());
        assertFalse(created.endNextDay());
        assertEquals(0, created.endDayOffset());
        assertEquals("ZERO", created.breakMode());
        assertEquals(0, created.customBreakMinutes());
        assertEquals("ZERO", created.plannedMode());
        assertEquals(0.0, created.customPlannedHours());
        assertEquals("причина", created.reasonTemplate());
        assertEquals(100, created.sortOrder());
    }

    @Test
    void updateCanChangeEveryFieldAndClearOptionalTextAndTime() {
        QuickScenarioDto created = quickScenarioService.create(owner, scenario("Исходный", 100));

        QuickScenarioDto updated = quickScenarioService.update(owner, created.id(), new QuickScenarioUpdateRequest(
                "  Ночной ППР  ", "  ночь  ", "  новое описание  ",
                "SHIFT_START", "FIXED_TIME", 0, "08:00", true,
                "CUSTOM", 45, "CUSTOM", 8.5,
                "  ППР ночью  ", 5));

        assertEquals("Ночной ППР", updated.name());
        assertEquals("ночь", updated.groupLabel());
        assertEquals("новое описание", updated.description());
        assertEquals("SHIFT_START", updated.startMode());
        assertEquals("FIXED_TIME", updated.endMode());
        assertEquals("08:00", updated.endFixedTime());
        assertTrue(updated.endNextDay());
        assertEquals("CUSTOM", updated.breakMode());
        assertEquals(45, updated.customBreakMinutes());
        assertEquals("CUSTOM", updated.plannedMode());
        assertEquals(8.5, updated.customPlannedHours());
        assertEquals("ППР ночью", updated.reasonTemplate());
        assertEquals(5, updated.sortOrder());

        QuickScenarioDto cleared = quickScenarioService.update(owner, created.id(), new QuickScenarioUpdateRequest(
                null, "   ", "   ", null, "ADD_MINUTES", 90, "", false,
                null, null, null, null, "   ", null));
        assertNull(cleared.groupLabel());
        assertNull(cleared.description());
        assertNull(cleared.endFixedTime());
        assertNull(cleared.reasonTemplate());
        assertEquals("ADD_MINUTES", cleared.endMode());
        assertEquals(90, cleared.endOffsetMinutes());
    }

    @Test
    void fixedTimeModeRequiresAFixedTimeOnCreateAndUpdate() {
        assertBadRequest(() -> quickScenarioService.create(owner, new QuickScenarioCreateRequest(
                "Без времени", null, null,
                "SHIFT_END", "FIXED_TIME", 0, null, true,
                "ZERO", 0, "ZERO", 0.0, null, 100)));

        QuickScenarioDto created = quickScenarioService.create(owner, scenario("Обычный", 100));
        assertBadRequest(() -> quickScenarioService.update(owner, created.id(), new QuickScenarioUpdateRequest(
                null, null, null, null, "FIXED_TIME", null, "", null,
                null, null, null, null, null, null)));
    }

    @Test
    void fixedTimeScenarioRebasesAcrossExtremeZonesAndRoundTrips() {
        owner.setWorkTimezone("Pacific/Kiritimati");
        users.save(owner);
        QuickScenarioDto created = quickScenarioService.create(owner, new QuickScenarioCreateRequest(
                "Крайние зоны", null, null,
                "SHIFT_END", "FIXED_TIME", 0, "00:30", false, 0,
                "ZERO", 0, "ZERO", 0.0, null, 10));
        assertEquals(0, created.endDayOffset());

        assertEquals(1, quickScenarioService.rebaseForTimezoneChange(
                owner, "Pacific/Kiritimati", "Pacific/Pago_Pago"));
        QuickScenarioDto west = quickScenarioService.list(owner).get(0);
        assertEquals("23:30", west.endFixedTime());
        assertEquals(-2, west.endDayOffset());

        assertEquals(1, quickScenarioService.rebaseForTimezoneChange(
                owner, "Pacific/Pago_Pago", "Pacific/Kiritimati"));
        QuickScenarioDto back = quickScenarioService.list(owner).get(0);
        assertEquals("00:30", back.endFixedTime());
        assertEquals(0, back.endDayOffset());
    }

    @Test
    void customScenarioBeforeFirstListPreventsSurpriseDefaultSeeding() {
        quickScenarioService.create(owner, scenario("Только мой", 7));

        List<QuickScenarioDto> list = quickScenarioService.list(owner);
        assertEquals(1, list.size());
        assertEquals("Только мой", list.get(0).name());
    }

    @Test
    void nullBlankForeignAndMissingRequestsUseStableErrors() {
        assertBadRequest(() -> quickScenarioService.create(owner, null));
        assertBadRequest(() -> quickScenarioService.create(owner, scenario("   ", 100)));

        QuickScenarioDto own = quickScenarioService.create(owner, scenario("Свой", 100));
        assertBadRequest(() -> quickScenarioService.update(owner, own.id(), null));

        QuickScenarioDto foreign = quickScenarioService.create(other, scenario("Чужой", 100));
        assertNotFound(() -> quickScenarioService.requireOwned(owner, foreign.id()));
        assertNotFound(() -> quickScenarioService.update(owner, foreign.id(), new QuickScenarioUpdateRequest(
                "Взлом", null, null, null, null, null, null, null,
                null, null, null, null, null, null)));
        assertNotFound(() -> quickScenarioService.delete(owner, foreign.id()));
        assertNotFound(() -> quickScenarioService.delete(owner, Long.MAX_VALUE));
    }

    private QuickScenarioCreateRequest scenario(String name, int sortOrder) {
        return new QuickScenarioCreateRequest(
                name, null, null,
                "SHIFT_END", "ADD_MINUTES", 120, null, false,
                "ZERO", 0, "ZERO", 0.0,
                null, sortOrder);
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
}
