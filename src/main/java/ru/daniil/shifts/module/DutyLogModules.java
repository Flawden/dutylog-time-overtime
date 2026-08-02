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
                    "Независимые Markdown-заметки, поиск и оффлайн-редактирование внутри календаря.",
                    "Independent Markdown notes, search and offline editing inside the calendar.",
                    false,
                    true,
                    List.of(CALENDAR),
                    List.of("day:notes", "notes:search", "calendar-marker:notes", "profile:notes-export"),
                    List.of("/api/notes", "/api/v1/notes", "/api/days/{date}:note", "/api/v1/days/{date}:note"),
                    List.of("day.note", "note.update"),
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
                    "Важные события",
                    "Important events",
                    "Floating-даты, события со временем, периоды и повторения.",
                    "Floating dates, timed events, periods and recurrences.",
                    false,
                    true,
                    List.of(CALENDAR),
                    List.of("nav:important-events", "day:important-dates", "calendar-marker:important-dates", "important-events:details", "important-events:editor"),
                    List.of("/api/important-days", "/api/v1/important-days"),
                    List.of(),
                    70
            ),
            new ModuleContract(
                    VACATION,
                    ModuleCategory.TIME_ACCOUNTING,
                    "Отпуск и отсутствия",
                    "Vacation & absences",
                    "Годовой лимит, перенос, периоды отпуска и другие виды отсутствия.",
                    "Annual allowance, carryover, vacation periods and other absence types.",
                    false,
                    true,
                    List.of(CALENDAR),
                    List.of("nav:vacation", "day:vacation", "calendar-marker:vacation", "vacation:planner"),
                    List.of("/api/vacation-planner", "/api/v1/vacation-planner"),
                    List.of(),
                    75
            ),
            new ModuleContract(
                    PAYROLL,
                    ModuleCategory.TIME_ACCOUNTING,
                    "Зарплата",
                    "Payroll",
                    "Расчёт закрытого периода по ставке, оплачиваемому времени и прозрачным корректировкам.",
                    "Closed-period calculation from rate, payable time and transparent adjustments.",
                    false,
                    true,
                    List.of(OVERTIME, VACATION),
                    List.of("nav:payroll", "payroll:workspace", "overtime:payroll-bridge"),
                    List.of("/api/payroll", "/api/v1/payroll"),
                    List.of(),
                    77
            ),
            new ModuleContract(
                    CALENDAR_SYNC,
                    ModuleCategory.INTEGRATION,
                    "Внешний календарь",
                    "External calendar",
                    "Безопасный .ics-экспорт и приватная read-only подписка.",
                    "Safe .ics export and a private read-only subscription.",
                    false,
                    true,
                    List.of(CALENDAR),
                    List.of("settings:calendar-sync", "important-events:ics-export"),
                    List.of("/api/calendar-sync", "/api/v1/calendar-sync", "/calendar-feed.ics"),
                    List.of(),
                    78
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
