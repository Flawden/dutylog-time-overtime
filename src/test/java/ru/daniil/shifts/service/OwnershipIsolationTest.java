package ru.daniil.shifts.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.dto.Dtos.*;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.ShiftType;
import ru.daniil.shifts.repo.ShiftTypeRepository;
import ru.daniil.shifts.repo.UserRepository;
import ru.daniil.shifts.service.exception.ApiException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IDOR-регрессия (Insecure Direct Object Reference).
 *
 * Атака: злоумышленник логинится СВОИМ валидным аккаунтом и обращается
 * к чужим ресурсам по их id (угадав/перебрав). Каждый доступ по id
 * ОБЯЗАН проверять владельца и отвечать «не найдено» — не «доступ запрещён»
 * (последнее подтверждает существование объекта и помогает перебору).
 *
 * Эти тесты — замок на IIDOR-проверки в сервисах. Кто-то (в т.ч. ИИ)
 * может однажды «упростить» сервис, убрав .filter(owner). Тогда красный
 * тест в CI остановит это до того, как оно уедет к пользователям.
 *
 * Добавил новую сущность с доступом по id — добавь сюда атаку на неё.
 */
@SpringBootTest
@Transactional
class OwnershipIsolationTest {

    @Autowired TaskService tasks;
    @Autowired QuickScenarioService scenarios;
    @Autowired ImportantDayService importantDays;
    @Autowired ShiftTypeService shiftTypes;
    @Autowired DefaultShiftSeedService seeder;
    @Autowired ShiftTypeRepository shiftTypeRepo;
    @Autowired UserRepository users;

    AppUser victim;   // жертва — создаёт ресурсы
    AppUser attacker; // атакующий — пытается их достать по id

    @BeforeEach
    void setUp() {
        victim = users.save(new AppUser("victim", "{noop}x"));
        attacker = users.save(new AppUser("attacker", "{noop}x"));
        seeder.seedDefaults(victim);
        seeder.seedDefaults(attacker);
    }

    /* ── Задачи ── */
    @Test
    void чужуюЗадачуНельзяИзменитьИлиУдалить() {
        TaskDto task = tasks.create(victim, new TaskCreateRequest(
                "2026-07-10", "секретная задача жертвы", null, null, null, null, null));

        TaskUpdateRequest hijack = new TaskUpdateRequest(
                "взломано", true, null, null, null, null, null, null);
        assertThrows(ApiException.class, () -> tasks.update(attacker, task.id(), hijack),
                "чужую задачу нельзя редактировать");
        assertThrows(ApiException.class, () -> tasks.delete(attacker, task.id()),
                "чужую задачу нельзя удалить");
    }

    /* ── Быстрые сценарии ── */
    @Test
    void чужойСценарийНеДоступенПоId() {
        QuickScenarioDto sc = scenarios.create(victim, new QuickScenarioCreateRequest(
                "сценарий жертвы", null, null, "SHIFT_END", "OFFSET",
                120, null, false, "NONE", null, "NONE", null, null, null));

        assertThrows(ApiException.class, () -> scenarios.requireOwned(attacker, sc.id()),
                "чужой сценарий не должен резолвиться");
        assertThrows(ApiException.class, () -> scenarios.delete(attacker, sc.id()));
    }

    /* ── Важные дни ── */
    @Test
    void чужойВажныйДеньНельзяТронуть() {
        ImportantDayDto day = importantDays.create(victim,
                new ImportantDayCreateRequest("др жертвы", "2026-08-15", "#F5B841"));

        ImportantDayUpdateRequest hijack =
                new ImportantDayUpdateRequest("взломано", "2026-08-15", "#FF0000");
        assertThrows(ApiException.class, () -> importantDays.update(attacker, day.id(), hijack));
        assertThrows(ApiException.class, () -> importantDays.delete(attacker, day.id()));
    }

    /* ── Типы смен ── */
    @Test
    void чужуюСменуНельзяРезольвитьИзменитьУдалить() {
        ShiftType victimShift = shiftTypeRepo.findByOwner(victim).get(0);

        assertThrows(ApiException.class,
                () -> shiftTypes.requireOwnedShiftType(attacker, victimShift.getId()),
                "чужой тип смены не должен резолвиться");

        ShiftTypeUpdateRequest hijack = new ShiftTypeUpdateRequest(
                "взломано", 99.0, "#FF0000", null, null, null, null, null, null);
        assertThrows(ApiException.class,
                () -> shiftTypes.update(attacker, victimShift.getId(), hijack));
        assertThrows(ApiException.class,
                () -> shiftTypes.delete(attacker, victimShift.getId()));
    }

    /* ── Позитивный контроль: владелец СВОЙ ресурс трогает свободно ── */
    @Test
    void владелецСвойРесурсТрогаетСвободно() {
        TaskDto task = tasks.create(victim, new TaskCreateRequest(
                "2026-07-10", "моя задача", null, null, null, null, null));
        assertDoesNotThrow(() -> tasks.update(victim, task.id(),
                new TaskUpdateRequest("обновлено", true, null, null, null, null, null, null)));
        assertDoesNotThrow(() -> tasks.delete(victim, task.id()));
    }
}
