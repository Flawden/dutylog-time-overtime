package ru.daniil.shifts.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.dto.Dtos.*;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.MobileAuthToken;
import ru.daniil.shifts.model.RepeatMode;
import ru.daniil.shifts.model.ShiftType;
import ru.daniil.shifts.repo.MobileAuthTokenRepository;
import ru.daniil.shifts.repo.ShiftTypeRepository;
import ru.daniil.shifts.repo.UserRepository;
import ru.daniil.shifts.service.exception.ApiException;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/** Cross-user IDOR regression suite. Every rejection must remain 404. */
@SpringBootTest
@Transactional
class OwnershipIsolationTest {

    @Autowired TaskService tasks;
    @Autowired QuickScenarioService scenarios;
    @Autowired ImportantDayService importantDays;
    @Autowired ShiftTypeService shiftTypes;
    @Autowired OvertimeService overtime;
    @Autowired MobileAuthService mobileAuth;
    @Autowired DefaultShiftSeedService seeder;
    @Autowired ShiftTypeRepository shiftTypeRepo;
    @Autowired MobileAuthTokenRepository mobileTokens;
    @Autowired UserRepository users;

    AppUser victim;
    AppUser attacker;

    @BeforeEach
    void setUp() {
        victim = users.save(new AppUser("victim", "{noop}x"));
        attacker = users.save(new AppUser("attacker", "{noop}x"));
        seeder.seedDefaults(victim);
        seeder.seedDefaults(attacker);
    }

    @Test
    void чужуюЗадачуНельзяИзменитьИлиУдалить() {
        TaskDto task = tasks.create(victim, new TaskCreateRequest(
                "2026-07-10", "секретная задача жертвы", null, null, null, null, null, null));
        TaskUpdateRequest hijack = new TaskUpdateRequest(
                "взломано", true, null, null, null, null, null, null, null);

        assertNotFound(() -> tasks.update(attacker, task.id(), hijack));
        assertNotFound(() -> tasks.delete(attacker, task.id()));
    }

    @Test
    void чужойСценарийНеДоступенПоId() {
        QuickScenarioDto scenario = scenarios.create(victim, new QuickScenarioCreateRequest(
                "сценарий жертвы", null, null,
                "SHIFT_END", "ADD_MINUTES", 120, null, false,
                "ZERO", 0, "ZERO", 0.0, null, 100));

        assertNotFound(() -> scenarios.requireOwned(attacker, scenario.id()));
        assertNotFound(() -> scenarios.delete(attacker, scenario.id()));
    }

    @Test
    void чужойВажныйДеньНельзяТронуть() {
        ImportantDayDto day = importantDays.create(victim,
                new ImportantDayCreateRequest("др жертвы", "2026-08-15", RepeatMode.YEARLY, "#F5B841"));
        ImportantDayUpdateRequest hijack =
                new ImportantDayUpdateRequest("взломано", "2026-08-15", RepeatMode.YEARLY, "#FF0000");

        assertNotFound(() -> importantDays.update(attacker, day.id(), hijack));
        assertNotFound(() -> importantDays.delete(attacker, day.id()));
    }

    @Test
    void чужуюСменуНельзяРезольвитьИзменитьУдалить() {
        ShiftType victimShift = shiftTypeRepo.findByOwner(victim).get(0);
        ShiftTypeUpdateRequest hijack = new ShiftTypeUpdateRequest(
                "взломано", 99.0, "#FF0000", null, null, null, null, null, null);

        assertNotFound(() -> shiftTypes.requireOwnedShiftType(attacker, victimShift.getId()));
        assertNotFound(() -> shiftTypes.update(attacker, victimShift.getId(), hijack));
        assertNotFound(() -> shiftTypes.delete(attacker, victimShift.getId()));
    }

    @Test
    void чужиеНачислениеИСписаниеПереработкиНедоступны() {
        OvertimeAccountDto afterCredit = overtime.createCredit(victim,
                new OvertimeCreditCreateRequest("2026-07-10", null, null, null,
                        0, 0.0, 4.0, "секретное начисление"));
        long creditId = afterCredit.credits().get(0).id();
        OvertimeAccountDto afterUsage = overtime.createUsage(victim,
                new OvertimeUsageCreateRequest("2026-07-11", 1.0, "секретный отгул"));
        long usageId = afterUsage.usages().get(0).id();

        assertNotFound(() -> overtime.updateCredit(attacker, creditId,
                new OvertimeCreditUpdateRequest(null, null, null, null, null, null, 3.0, null)));
        assertNotFound(() -> overtime.deleteCredit(attacker, creditId));
        assertNotFound(() -> overtime.updateUsage(attacker, usageId,
                new OvertimeUsageUpdateRequest(null, 0.5, null)));
        assertNotFound(() -> overtime.deleteUsage(attacker, usageId));
    }

    @Test
    void чужуюМобильнуюСессиюНельзяОтозвать() {
        MobileAuthToken token = mobileTokens.save(new MobileAuthToken(
                victim,
                MobileAuthService.hash("victim-access"),
                MobileAuthService.hash("victim-refresh"),
                Instant.now().plusSeconds(3600),
                Instant.now().plusSeconds(7200),
                "victim-phone"));

        assertNotFound(() -> mobileAuth.revokeSession(attacker, token.getId()));
    }

    @Test
    void владелецСвойРесурсТрогаетСвободно() {
        TaskDto task = tasks.create(victim, new TaskCreateRequest(
                "2026-07-10", "моя задача", null, null, null, null, null, null));
        assertDoesNotThrow(() -> tasks.update(victim, task.id(),
                new TaskUpdateRequest("обновлено", true, null, null, null, null, null, null, null)));
        assertDoesNotThrow(() -> tasks.delete(victim, task.id()));
    }

    private void assertNotFound(org.junit.jupiter.api.function.Executable action) {
        ApiException error = assertThrows(ApiException.class, action);
        assertEquals(HttpStatus.NOT_FOUND, error.getStatus(),
                "IDOR rejection must not reveal whether the foreign resource exists");
    }
}
