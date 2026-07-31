package ru.daniil.shifts.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.dto.Dtos.ModuleDto;
import ru.daniil.shifts.dto.Dtos.ModuleSettingsUpdateRequest;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.UserModuleSetting;
import ru.daniil.shifts.module.DutyLogModules;
import ru.daniil.shifts.repo.UserModuleSettingRepository;
import ru.daniil.shifts.repo.UserRepository;
import ru.daniil.shifts.service.exception.ApiException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Complete per-user module registry and persistence contract. */
@SpringBootTest
@Transactional
class ModuleServiceContractTest {

    @Autowired ModuleService modules;
    @Autowired UserRepository users;
    @Autowired UserModuleSettingRepository settings;

    AppUser regular;

    @BeforeEach
    void setUp() {
        regular = users.save(new AppUser("module-service-contract", "{noop}x"));
    }

    @Test
    void regularUserGetsLockedCoreDefaultsButDoesNotSeeAdminContract() {
        List<ModuleDto> list = modules.list(regular);
        Map<String, Boolean> effective = modules.effectiveMap(regular);

        assertTrue(effective.get(ModuleService.CORE));
        assertTrue(effective.get(ModuleService.CALENDAR));
        assertTrue(effective.get(ModuleService.SHIFTS));
        assertFalse(effective.get(ModuleService.ADMIN));
        assertFalse(effective.get(ModuleService.TELEGRAM));
        assertTrue(list.stream().noneMatch(module -> module.key().equals(ModuleService.ADMIN)));
        assertTrue(list.stream().filter(ModuleDto::locked).allMatch(ModuleDto::enabled));
    }

    @Test
    void administratorSeesEnabledLockedAdminContract() {
        AppUser admin = users.save(new AppUser("module-service-admin", "{noop}x"));
        admin.setRole("ADMIN");

        ModuleDto adminModule = modules.contracts(admin).stream()
                .filter(module -> module.key().equals(ModuleService.ADMIN))
                .findFirst().orElseThrow();

        assertTrue(adminModule.enabled());
        assertTrue(adminModule.locked());
        assertFalse(adminModule.hidden());
    }

    @Test
    void nullUnknownAndLockedUpdatesCannotCorruptRegistry() {
        modules.update(regular, null);
        modules.update(regular, new ModuleSettingsUpdateRequest(Map.of(
                " future-module ", true,
                " CORE ", false,
                " ADMIN ", true
        )));

        Map<String, Boolean> effective = modules.effectiveMap(regular);
        assertTrue(effective.get(ModuleService.CORE));
        assertFalse(effective.get(ModuleService.ADMIN));
        assertFalse(effective.containsKey("future-module"));
        long expectedPersisted = DutyLogModules.ALL.stream()
                .filter(definition -> !definition.locked())
                .filter(definition -> !ModuleService.ADMIN.equals(definition.key()))
                .count();
        assertEquals(expectedPersisted, settings.findByOwner(regular).size(),
                "only switchable non-admin modules are persisted");
    }

    @Test
    void enablingScenarioActivatesItsWholeDependencyChain() {
        modules.update(regular, new ModuleSettingsUpdateRequest(Map.of(
                ModuleService.OVERTIME, false,
                ModuleService.SCENARIOS, false
        )));
        modules.update(regular, new ModuleSettingsUpdateRequest(Map.of(ModuleService.SCENARIOS, true)));

        Map<String, Boolean> effective = modules.effectiveMap(regular);
        assertTrue(effective.get(ModuleService.SCENARIOS));
        assertTrue(effective.get(ModuleService.OVERTIME));
        assertTrue(effective.get(ModuleService.SHIFTS));
        assertTrue(effective.get(ModuleService.CALENDAR));
    }

    @Test
    void staleUnknownSettingsAreIgnoredAndKnownKeysAreNormalized() {
        settings.save(new UserModuleSetting(regular, "future-module", true));
        settings.save(new UserModuleSetting(regular, "  NOTES  ", false));
        settings.flush();

        Map<String, Boolean> effective = modules.effectiveMap(regular);
        assertFalse(effective.containsKey("future-module"));
        assertFalse(effective.get(ModuleService.NOTES));
    }

    @Test
    void requireEnabledNormalizesKeysAndReturnsStructuredForbiddenForDisabledModules() {
        modules.update(regular,
                new ModuleSettingsUpdateRequest(Map.of(ModuleService.NOTES, false)));

        ApiException error = assertThrows(ApiException.class,
                () -> modules.requireEnabled(regular, " NOTES "));
        assertEquals(403, error.getStatus().value());
        assertEquals("MODULE_DISABLED:notes", error.getMessage());

        modules.requireEnabled(regular, " CORE ");
    }
}
