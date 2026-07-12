package ru.daniil.shifts.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.dto.Dtos.DayDto;
import ru.daniil.shifts.dto.Dtos.DayFillRequest;
import ru.daniil.shifts.dto.Dtos.DayUpsertRequest;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.ShiftType;
import ru.daniil.shifts.repo.ShiftTypeRepository;
import ru.daniil.shifts.repo.UserRepository;
import ru.daniil.shifts.service.exception.ApiException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Сердце календаря — upsert записи дня. Проверяем контракты, на которых
 * держится всё остальное: пустая запись исчезает, чужая смена не назначается,
 * пользователи не видят чужих дней.
 */
@SpringBootTest
@Transactional
class DayEntryServiceTest {

    @Autowired DayEntryService dayEntries;
    @Autowired DefaultShiftSeedService seeder;
    @Autowired ShiftTypeRepository shiftTypes;
    @Autowired UserRepository users;
    @Autowired EntityManager entityManager;

    AppUser user;
    ShiftType userShift;

    @BeforeEach
    void setUp() {
        user = users.save(new AppUser("day-test-user", "{noop}x"));
        seeder.seedDefaults(user);
        userShift = shiftTypes.findByOwner(user).get(0);
    }

    private static DayUpsertRequest req(Long shiftTypeId, String note) {
        return new DayUpsertRequest(shiftTypeId, note, null, null, null);
    }

    @Test
    void upsertСоздаётЗаписьСоСменойИЗаметкой() {
        DayDto dto = dayEntries.upsert(user, "2026-07-10", req(userShift.getId(), "# смена"));

        assertNotNull(dto);
        assertEquals("2026-07-10", dto.date());
        assertEquals(userShift.getId(), dto.shiftTypeId());
        assertEquals("# смена", dto.note());
    }

    @Test
    void опустошениеЗаписиУдаляетЕё() {
        dayEntries.upsert(user, "2026-07-10", req(userShift.getId(), "текст"));
        // снимаем смену и стираем заметку — запись должна исчезнуть, а не висеть пустой
        DayDto dto = dayEntries.upsert(user, "2026-07-10", req(null, ""));

        assertNull(dto, "пустой upsert возвращает null");
        List<DayDto> month = dayEntries.listMonth(user, 2026, 7);
        assertTrue(month.stream().noneMatch(d -> d.date().equals("2026-07-10")),
                "пустая запись не должна оставаться в месяце");
    }

    @Test
    void чужаяСменаНеНазначается() {
        AppUser other = users.save(new AppUser("day-test-other", "{noop}x"));
        seeder.seedDefaults(other);
        ShiftType foreign = shiftTypes.findByOwner(other).get(0);

        assertThrows(ApiException.class,
                () -> dayEntries.upsert(user, "2026-07-10", req(foreign.getId(), null)),
                "чужой shiftTypeId обязан отклоняться — это IDOR-защита");
    }

    @Test
    void пользователиНеВидятЧужиеДни() {
        dayEntries.upsert(user, "2026-07-10", req(userShift.getId(), "моё"));

        AppUser other = users.save(new AppUser("day-test-viewer", "{noop}x"));
        List<DayDto> othersMonth = dayEntries.listMonth(other, 2026, 7);

        assertTrue(othersMonth.isEmpty(), "чужие записи не должны попадать в выдачу");
    }

    @Test
    void криваяДатаОтклоняется() {
        assertThrows(ApiException.class,
                () -> dayEntries.upsert(user, "10.07.2026", req(null, "заметка")));
    }
    @Test
    void массовыйГрафикСохраняетсяПослеОчисткиPersistenceContext() {
        ShiftType day = shiftTypes.findByOwner(user).stream()
                .filter(s -> "Дневная".equals(s.getName()))
                .findFirst()
                .orElse(userShift);
        ShiftType off = shiftTypes.findByOwner(user).stream()
                .filter(s -> "Выходной".equals(s.getName()))
                .findFirst()
                .orElseThrow();

        DayFillRequest request = new DayFillRequest(
                "2026-07-01",
                31,
                List.of(day.getId(), day.getId(), day.getId(), day.getId(), day.getId(), off.getId(), off.getId()),
                true
        );

        List<DayDto> changed = dayEntries.fillSchedule(user, request);
        assertEquals(31, changed.size(), "endpoint должен вернуть все сохранённые дни");

        entityManager.clear(); // имитирует новый HTTP-запрос/F5/другой браузер
        List<DayDto> reloaded = dayEntries.listMonth(user, 2026, 7);

        assertEquals(31, reloaded.size(), "весь график обязан читаться из БД после reload");
        assertEquals(day.getId(), reloaded.get(0).shiftTypeId());
        assertEquals(off.getId(), reloaded.get(5).shiftTypeId());
        assertEquals(day.getId(), reloaded.get(7).shiftTypeId());
    }

    @Test
    void массовыйГрафикНеЗатираетСуществующуюСменуКогдаOverwriteВыключен() {
        ShiftType original = userShift;
        dayEntries.upsert(user, "2026-07-02", req(original.getId(), null));
        ShiftType replacement = shiftTypes.findByOwner(user).stream()
                .filter(s -> !s.getId().equals(original.getId()))
                .findFirst()
                .orElseThrow();

        dayEntries.fillSchedule(user, new DayFillRequest(
                "2026-07-01", 3, List.of(replacement.getId()), false));

        entityManager.clear();
        DayDto julySecond = dayEntries.listMonth(user, 2026, 7).stream()
                .filter(d -> "2026-07-02".equals(d.date()))
                .findFirst()
                .orElseThrow();
        assertEquals(original.getId(), julySecond.shiftTypeId());
    }

}
