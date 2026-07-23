package ru.daniil.shifts.module;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static ru.daniil.shifts.module.ModuleKeys.*;

/**
 * Single source of truth for DutyLog modules.
 *
 * Add a new module here first, then wire its controller guard, frontend UI slots,
 * offline queue types and docs. Do not scatter new module keys across the codebase.
 */
public final class DutyLogModules {
    private DutyLogModules() {}

    public static final List<ModuleContract> ALL = List.of(
            new ModuleContract(
                    CORE,
                    ModuleCategory.CORE,
                    "Ядро",
                    "Core",
                    "Профиль, язык, тема и базовая оболочка приложения.",
                    "Profile, language, theme and the base app shell.",
                    true,
                    true,
                    List.of(),
                    List.of("app-shell", "profile", "profile:notes-export", "appearance", "language", "offline-shell"),
                    List.of("/api/profile", "/api/v1/profile", "/api/modules", "/api/v1/modules", "/api/auth", "/api/mobile", "/api/v1/mobile", "/api/export/notes", "/api/v1/export/notes"),
                    List.of(),
                    10
            ),
            new ModuleContract(
                    CALENDAR,
                    ModuleCategory.CALENDAR,
                    "Календарь",
                    "Calendar",
                    "Месяц, выбор дня и назначение смен.",
                    "Month view, selected day and shift assignment.",
                    true,
                    true,
                    List.of(CORE),
                    List.of("nav:calendar", "calendar-grid", "selected-day"),
                    List.of("/api/calendar", "/api/v1/calendar", "/api/days", "/api/v1/days"),
                    List.of("day.shift"),
                    20
            ),
            new ModuleContract(
                    SHIFTS,
                    ModuleCategory.CALENDAR,
                    "Смены",
                    "Shifts",
                    "Типы смен, часы, нормы и графики.",
                    "Shift types, hours, norms and schedule templates.",
                    true,
                    true,
                    List.of(CALENDAR),
                    List.of("day:shift", "day:schedule", "settings:shift-types", "settings:time-region"),
                    List.of("/api/shift-types", "/api/v1/shift-types"),
                    List.of("day.shift"),
                    30
            ),
            new ModuleContract(
                    NOTES,
                    ModuleCategory.PRODUCTIVITY,
                    "Заметки",
                    "Notes",
                    "Markdown-заметки внутри выбранного дня.",
                    "Markdown notes inside the selected day.",
                    false,
                    true,
                    List.of(CALENDAR),
                    List.of("day:note", "calendar-marker:note"),
                    List.of("/api/days/{date}:note", "/api/v1/days/{date}:note"),
                    List.of("day.note"),
                    40
            ),
            new ModuleContract(
                    TASKS,
                    ModuleCategory.PRODUCTIVITY,
                    "Задачи",
                    "Tasks",
                    "Задачи по дням, дедлайны и общая доска.",
                    "Daily tasks, due dates and the global board.",
                    false,
                    true,
                    List.of(CALENDAR),
                    List.of("nav:tasks", "day:tasks", "calendar-marker:tasks", "tasks:inbox", "global:quick-capture"),
                    List.of("/api/tasks", "/api/v1/tasks", "/api/inbox", "/api/v1/inbox"),
                    List.of("task.done", "inbox.capture"),
                    50
            ),
            new ModuleContract(
                    OVERTIME,
                    ModuleCategory.TIME_ACCOUNTING,
                    "Переработки",
                    "Overtime",
                    "Начисления, отгулы, FIFO-баланс и журнал.",
                    "Credits, time off, FIFO balance and ledger.",
                    false,
                    true,
                    List.of(CALENDAR, SHIFTS),
                    List.of("nav:overtime", "day:overtime", "calendar-marker:overtime"),
                    List.of("/api/overtime", "/api/v1/overtime"),
                    List.of("day.overtime"),
                    60
            ),
            new ModuleContract(
                    IMPORTANT_DATES,
                    ModuleCategory.PRODUCTIVITY,
                    "Важные даты",
                    "Important dates",
                    "Дни рождения, события и повторяющиеся даты.",
                    "Birthdays, events and repeating dates.",
                    false,
                    true,
                    List.of(CALENDAR),
                    List.of("day:important-dates", "calendar-marker:important-dates", "settings:important-dates"),
                    List.of("/api/important-days", "/api/v1/important-days"),
                    List.of(),
                    70
            ),
            new ModuleContract(
                    NOTIFICATIONS,
                    ModuleCategory.INTEGRATION,
                    "Уведомления",
                    "Notifications",
                    "Браузерные, сменные, задачные и важные напоминания.",
                    "Browser, shift, task and important-date reminders.",
                    false,
                    true,
                    List.of(CALENDAR),
                    List.of("settings:notifications", "calendar-marker:notifications"),
                    List.of("/api/notifications", "/api/v1/notifications"),
                    List.of(),
                    80
            ),
            new ModuleContract(
                    TELEGRAM,
                    ModuleCategory.INTEGRATION,
                    "Telegram",
                    "Telegram",
                    "Привязка Telegram-бота и получение напоминаний.",
                    "Telegram bot linking and reminder delivery.",
                    false,
                    false,
                    List.of(NOTIFICATIONS),
                    List.of("profile:telegram"),
                    List.of("/api/telegram"),
                    List.of(),
                    90
            ),
            new ModuleContract(
                    SCENARIOS,
                    ModuleCategory.TIME_ACCOUNTING,
                    "Сценарии",
                    "Scenarios",
                    "Шаблоны, которые быстро заполняют форму переработки.",
                    "Templates that quickly fill the overtime form.",
                    false,
                    true,
                    List.of(OVERTIME),
                    List.of("day:quick-scenarios", "settings:quick-scenarios"),
                    List.of("/api/quick-scenarios", "/api/v1/quick-scenarios"),
                    List.of(),
                    100
            ),
            new ModuleContract(
                    ADMIN,
                    ModuleCategory.ADMIN,
                    "Админка",
                    "Admin tools",
                    "Пользователи, роли, регистрация и диагностика.",
                    "Users, roles, registration and diagnostics.",
                    true,
                    true,
                    List.of(CORE),
                    List.of("settings:admin", "nav:admin"),
                    List.of("/api/admin"),
                    List.of(),
                    110
            )
    );

    public static Optional<ModuleContract> find(String key) {
        String normalized = normalize(key);
        return ALL.stream().filter(module -> module.key().equals(normalized)).findFirst();
    }

    public static Set<String> knownKeys() {
        return ALL.stream().map(ModuleContract::key).collect(Collectors.toUnmodifiableSet());
    }

    public static String normalize(String key) {
        return key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
    }
}
