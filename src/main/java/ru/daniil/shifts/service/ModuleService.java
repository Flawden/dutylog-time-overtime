package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.dto.Dtos.ModuleDto;
import ru.daniil.shifts.dto.Dtos.ModuleSettingsUpdateRequest;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.UserModuleSetting;
import ru.daniil.shifts.module.DutyLogModules;
import ru.daniil.shifts.module.ModuleContract;
import ru.daniil.shifts.module.ModuleKeys;
import ru.daniil.shifts.repo.UserModuleSettingRepository;
import ru.daniil.shifts.service.exception.ApiException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

/**
 * Registry and per-user switches for DutyLog modules.
 *
 * v25.3 keeps DutyLog as a modular monolith: one deployable application,
 * explicit module contracts, guarded API boundaries and module-aware PWA/offline UI.
 */
@Service
public class ModuleService {
    public static final String CORE = ModuleKeys.CORE;
    public static final String CALENDAR = ModuleKeys.CALENDAR;
    public static final String SHIFTS = ModuleKeys.SHIFTS;
    public static final String NOTES = ModuleKeys.NOTES;
    public static final String TASKS = ModuleKeys.TASKS;
    public static final String OVERTIME = ModuleKeys.OVERTIME;
    public static final String IMPORTANT_DATES = ModuleKeys.IMPORTANT_DATES;
    public static final String VACATION = ModuleKeys.VACATION;
    public static final String CALENDAR_SYNC = ModuleKeys.CALENDAR_SYNC;
    public static final String NOTIFICATIONS = ModuleKeys.NOTIFICATIONS;
    public static final String TELEGRAM = ModuleKeys.TELEGRAM;
    public static final String SCENARIOS = ModuleKeys.SCENARIOS;
    public static final String ADMIN = ModuleKeys.ADMIN;

    private static final List<ModuleContract> DEFINITIONS = DutyLogModules.ALL;

    private final UserModuleSettingRepository settings;

    public ModuleService(UserModuleSettingRepository settings) {
        this.settings = settings;
    }

    @Transactional(readOnly = true)
    public List<ModuleDto> list(AppUser user) {
        Map<String, Boolean> values = effectiveMap(user);
        return DEFINITIONS.stream()
                .filter(def -> !ADMIN.equals(def.key()) || user.isAdmin())
                .map(def -> toDto(def, values.getOrDefault(def.key(), def.defaultEnabled()), user))
                .toList();
    }

    /** Same payload as list(), named for clients and developers that want the contract explicitly. */
    @Transactional(readOnly = true)
    public List<ModuleDto> contracts(AppUser user) {
        return list(user);
    }

    @Transactional
    public List<ModuleDto> update(AppUser user, ModuleSettingsUpdateRequest req) {
        Map<String, Boolean> values = effectiveMap(user);
        Map<String, Boolean> requested = req == null || req.enabled() == null ? Map.of() : req.enabled();
        Set<String> known = DutyLogModules.knownKeys();

        for (Map.Entry<String, Boolean> entry : requested.entrySet()) {
            String key = normalizeKey(entry.getKey());
            if (!known.contains(key)) continue;
            ModuleContract def = definition(key);
            if (def == null || def.locked()) continue;
            if (ADMIN.equals(key) && !user.isAdmin()) continue;
            values.put(key, Boolean.TRUE.equals(entry.getValue()));
        }

        // Locked/core modules are always enabled; admin is visible only for admins.
        enforceLockedModules(user, values);

        // Directly enabling a module also enables its dependency chain.
        // Directly disabling a dependency wins: dependent modules are disabled below.
        Set<String> explicitlyDisabled = new HashSet<>();
        for (Map.Entry<String, Boolean> entry : requested.entrySet()) {
            String key = normalizeKey(entry.getKey());
            ModuleContract def = definition(key);
            if (def == null || def.locked()) continue;
            if (Boolean.FALSE.equals(entry.getValue())) {
                explicitlyDisabled.add(key);
            }
        }
        for (Map.Entry<String, Boolean> entry : requested.entrySet()) {
            String key = normalizeKey(entry.getKey());
            ModuleContract def = definition(key);
            if (def == null || def.locked()) continue;
            if (Boolean.TRUE.equals(entry.getValue())) {
                enableDependencies(key, values, explicitlyDisabled);
            }
        }
        enforceLockedModules(user, values);

        // Disabling a module must also disable modules that depend on it.
        // Example: Overtime off => Scenarios off, so mobile guards and UI stay honest.
        cascadeDisableBrokenDependencies(values);
        enforceLockedModules(user, values);

        persist(user, values);
        return list(user);
    }

    @Transactional(readOnly = true)
    public boolean isEnabled(AppUser user, String moduleKey) {
        return effectiveMap(user).getOrDefault(normalizeKey(moduleKey), false);
    }

    @Transactional(readOnly = true)
    public void requireEnabled(AppUser user, String moduleKey) {
        String key = normalizeKey(moduleKey);
        if (!isEnabled(user, key)) {
            throw ApiException.forbidden("MODULE_DISABLED:" + key);
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Boolean> effectiveMap(AppUser user) {
        Map<String, Boolean> values = new LinkedHashMap<>();
        for (ModuleContract def : DEFINITIONS) {
            values.put(def.key(), def.locked() ? (!ADMIN.equals(def.key()) || user.isAdmin()) : def.defaultEnabled());
        }
        for (UserModuleSetting setting : settings.findByOwner(user)) {
            String key = normalizeKey(setting.getModuleKey());
            ModuleContract def = definition(key);
            if (def == null || def.locked()) continue;
            values.put(key, setting.isEnabled());
        }
        enforceLockedModules(user, values);
        cascadeDisableBrokenDependencies(values);
        enforceLockedModules(user, values);
        return values;
    }

    private void enforceLockedModules(AppUser user, Map<String, Boolean> values) {
        for (ModuleContract def : DEFINITIONS) {
            if (def.locked()) {
                values.put(def.key(), !ADMIN.equals(def.key()) || user.isAdmin());
            }
        }
        if (!user.isAdmin()) {
            values.put(ADMIN, false);
        }
    }

    private void enableDependencies(String key, Map<String, Boolean> values, Set<String> explicitlyDisabled) {
        ModuleContract def = definition(key);
        if (def == null) {
            return;
        }
        for (String dep : def.dependencies()) {
            if (explicitlyDisabled.contains(dep)) {
                continue;
            }
            if (!values.getOrDefault(dep, false)) {
                values.put(dep, true);
            }
            enableDependencies(dep, values, explicitlyDisabled);
        }
    }

    private void cascadeDisableBrokenDependencies(Map<String, Boolean> values) {
        boolean changed;
        do {
            changed = false;
            for (ModuleContract def : DEFINITIONS) {
                if (def.locked() || !values.getOrDefault(def.key(), false)) {
                    continue;
                }
                for (String dep : def.dependencies()) {
                    if (!values.getOrDefault(dep, false)) {
                        values.put(def.key(), false);
                        changed = true;
                        break;
                    }
                }
            }
        } while (changed);
    }

    private void persist(AppUser user, Map<String, Boolean> values) {
        for (ModuleContract def : DEFINITIONS) {
            if (def.locked()) continue;
            boolean enabled = values.getOrDefault(def.key(), def.defaultEnabled());
            UserModuleSetting setting = settings.findByOwnerAndModuleKey(user, def.key())
                    .orElseGet(() -> new UserModuleSetting(user, def.key(), enabled));
            setting.setEnabled(enabled);
            settings.save(setting);
        }
    }

    private ModuleDto toDto(ModuleContract def, boolean enabled, AppUser user) {
        return new ModuleDto(
                def.key(),
                def.titleRu(),
                def.titleEn(),
                def.descriptionRu(),
                def.descriptionEn(),
                enabled,
                def.locked(),
                def.defaultEnabled(),
                def.dependencies(),
                ADMIN.equals(def.key()) && !user.isAdmin(),
                def.category().name().toLowerCase(),
                def.order(),
                def.uiSlots(),
                def.apiPrefixes(),
                def.offlineQueueTypes()
        );
    }

    private static ModuleContract definition(String key) {
        return DutyLogModules.find(key).orElse(null);
    }

    private static String normalizeKey(String key) {
        return DutyLogModules.normalize(key);
    }
}
