package ru.daniil.shifts.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.dto.Dtos.ModuleSettingsUpdateRequest;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.repo.UserRepository;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Dependency graph regressions for optional modules. */
@SpringBootTest
@Transactional
class ModuleDependencyTest {

    @Autowired ModuleService modules;
    @Autowired UserRepository users;

    AppUser user;

    @BeforeEach
    void setUp() {
        user = users.save(new AppUser("module-dependency-user", "{noop}unused"));
    }

    @Test
    void enablingTelegramAlsoEnablesNotifications() {
        modules.update(user, new ModuleSettingsUpdateRequest(Map.of("telegram", true)));
        Map<String, Boolean> effective = modules.effectiveMap(user);

        assertTrue(effective.get("telegram"));
        assertTrue(effective.get("notifications"));
    }

    @Test
    void disablingNotificationsCascadesToTelegram() {
        modules.update(user, new ModuleSettingsUpdateRequest(Map.of("telegram", true)));
        modules.update(user, new ModuleSettingsUpdateRequest(Map.of("notifications", false)));
        Map<String, Boolean> effective = modules.effectiveMap(user);

        assertFalse(effective.get("notifications"));
        assertFalse(effective.get("telegram"));
    }

    @Test
    void explicitDependencyDisableWinsInsideSamePatch() {
        modules.update(user, new ModuleSettingsUpdateRequest(Map.of(
                "telegram", true,
                "notifications", false
        )));
        Map<String, Boolean> effective = modules.effectiveMap(user);

        assertFalse(effective.get("notifications"));
        assertFalse(effective.get("telegram"));
    }
}
