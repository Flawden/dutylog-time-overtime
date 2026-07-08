package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.dto.Dtos.ModuleDto;
import ru.daniil.shifts.dto.Dtos.ModuleSettingsUpdateRequest;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.UserModuleSetting;
import ru.daniil.shifts.repo.UserModuleSettingRepository;
import ru.daniil.shifts.service.exception.ApiException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Registry and per-user switches for DutyLog modules.
 * v25.0 keeps this as a modular-monolith layer: one app, explicit module boundaries.
 */
@Service
public class ModuleService {
    public static final String CORE = "core";
    public static final String CALENDAR = "calendar";
    public static final String SHIFTS = "shifts";
    public static final String NOTES = "notes";
    public static final String TASKS = "tasks";
    public static final String OVERTIME = "overtime";
    public static final String IMPORTANT_DATES = "important_dates";
    public static final String NOTIFICATIONS = "notifications";
    public static final String TELEGRAM = "telegram";
    public static final String SCENARIOS = "scenarios";
    public static final String ADMIN = "admin";

    private static final List<ModuleDefinition> DEFINITIONS = List.of(
            new ModuleDefinition(CORE, "Ядро", "Core", "Профиль, язык, тема и базовая оболочка приложения.", "Profile, language, theme and the base app shell.", true, true, List.of()),
            new ModuleDefinition(CALENDAR, "Календарь", "Calendar", "Месяц, выбор дня и назначение смен.", "Month view, selected day and shift assignment.", true, true, List.of(CORE)),
            new ModuleDefinition(SHIFTS, "Смены", "Shifts", "Типы смен, часы, нормы и графики.", "Shift types, hours, norms and schedule templates.", true, true, List.of(CALENDAR)),
            new ModuleDefinition(NOTES, "Заметки", "Notes", "Markdown-заметки внутри выбранного дня.", "Markdown notes inside the selected day.", false, true, List.of(CALENDAR)),
            new ModuleDefinition(TASKS, "Задачи", "Tasks", "Задачи по дням, дедлайны и общая доска.", "Daily tasks, due dates and the global board.", false, true, List.of(CALENDAR)),
            new ModuleDefinition(OVERTIME, "Переработки", "Overtime", "Начисления, отгулы, FIFO-баланс и журнал.", "Credits, time off, FIFO balance and ledger.", false, true, List.of(CALENDAR, SHIFTS)),
            new ModuleDefinition(IMPORTANT_DATES, "Важные даты", "Important dates", "Дни рождения, события и повторяющиеся даты.", "Birthdays, events and repeating dates.", false, true, List.of(CALENDAR)),
            new ModuleDefinition(NOTIFICATIONS, "Уведомления", "Notifications", "Браузерные, сменные, задачные и важные напоминания.", "Browser, shift, task and important-date reminders.", false, true, List.of(CALENDAR)),
            new ModuleDefinition(TELEGRAM, "Telegram", "Telegram", "Привязка Telegram-бота и получение напоминаний.", "Telegram bot linking and reminder delivery.", false, false, List.of(NOTIFICATIONS)),
            new ModuleDefinition(SCENARIOS, "Сценарии", "Scenarios", "Шаблоны, которые быстро заполняют форму переработки.", "Templates that quickly fill the overtime form.", false, true, List.of(OVERTIME)),
            new ModuleDefinition(ADMIN, "Админка", "Admin tools", "Пользователи, роли, регистрация и диагностика.", true, true, List.of(CORE))
    );

    private final UserModuleSettingRepository settings;

    public ModuleService(UserModuleSettingRepository settings) {
        this.settings = settings;
    }

    public record ModuleDefinition(String key, String titleRu, String titleEn, String descriptionRu, String descriptionEn, boolean locked, boolean defaultEnabled, List<String> dependencies) {}

    @Transactional(readOnly = true)
    public List<ModuleDto> list(AppUser user) {
        Map<String, Boolean> values = effectiveMap(user);
        return DEFINITIONS.stream()
                .filter(def -> !ADMIN.equals(def.key()) || user.isAdmin())
                .map(def -> toDto(def, values.getOrDefault(def.key(), def.defaultEnabled()), user))
                .toList();
    }

    @Transactional
    public List<ModuleDto> update(AppUser user, ModuleSettingsUpdateRequest req) {
        Map<String, Boolean> values = effectiveMap(user);
        Map<String, Boolean> requested = req == null || req.enabled() == null ? Map.of() : req.enabled();
        Set<String> known = DEFINITIONS.stream().map(ModuleDefinition::key).collect(Collectors.toSet());

        for (Map.Entry<String, Boolean> entry : requested.entrySet()) {
            String key = normalizeKey(entry.getKey());
            if (!known.contains(key)) continue;
            ModuleDefinition def = definition(key);
            if (def == null || def.locked()) continue;
            if (ADMIN.equals(key) && !user.isAdmin()) continue;
            values.put(key, Boolean.TRUE.equals(entry.getValue()));
        }

        // Locked/core modules are always enabled; admin is visible only for admins.
        for (ModuleDefinition def : DEFINITIONS) {
            if (def.locked()) values.put(def.key(), !ADMIN.equals(def.key()) || user.isAdmin());
        }

        // Enabling a module enables its dependencies. This keeps the UI coherent.
        boolean changed;
        do {
            changed = false;
            for (ModuleDefinition def : DEFINITIONS) {
                if (!values.getOrDefault(def.key(), def.defaultEnabled())) continue;
                for (String dep : def.dependencies()) {
                    if (!values.getOrDefault(dep, false)) {
                        values.put(dep, true);
                        changed = true;
                    }
                }
            }
        } while (changed);

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
        for (ModuleDefinition def : DEFINITIONS) {
            values.put(def.key(), def.locked() ? (!ADMIN.equals(def.key()) || user.isAdmin()) : def.defaultEnabled());
        }
        for (UserModuleSetting setting : settings.findByOwner(user)) {
            String key = normalizeKey(setting.getModuleKey());
            ModuleDefinition def = definition(key);
            if (def == null || def.locked()) continue;
            values.put(key, setting.isEnabled());
        }
        if (!user.isAdmin()) values.put(ADMIN, false);
        for (ModuleDefinition def : DEFINITIONS) {
            if (def.locked()) values.put(def.key(), !ADMIN.equals(def.key()) || user.isAdmin());
        }
        return values;
    }

    private void persist(AppUser user, Map<String, Boolean> values) {
        for (ModuleDefinition def : DEFINITIONS) {
            if (def.locked()) continue;
            boolean enabled = values.getOrDefault(def.key(), def.defaultEnabled());
            UserModuleSetting setting = settings.findByOwnerAndModuleKey(user, def.key())
                    .orElseGet(() -> new UserModuleSetting(user, def.key(), enabled));
            setting.setEnabled(enabled);
            settings.save(setting);
        }
    }

    private ModuleDto toDto(ModuleDefinition def, boolean enabled, AppUser user) {
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
                ADMIN.equals(def.key()) && !user.isAdmin()
        );
    }

    private static ModuleDefinition definition(String key) {
        String normalized = normalizeKey(key);
        return DEFINITIONS.stream().filter(d -> d.key().equals(normalized)).findFirst().orElse(null);
    }

    private static String normalizeKey(String key) {
        return key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
    }
}
