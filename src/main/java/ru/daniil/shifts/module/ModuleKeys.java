package ru.daniil.shifts.module;

/**
 * Stable module keys. These values are persisted in user_module_settings,
 * are returned by /api/modules and are used by the PWA/offline queue.
 *
 * Never rename a key without a database migration and an offline migration plan.
 */
public final class ModuleKeys {
    private ModuleKeys() {}

    public static final String CORE = "core";
    public static final String CALENDAR = "calendar";
    public static final String SHIFTS = "shifts";
    public static final String NOTES = "notes";
    public static final String TASKS = "tasks";
    public static final String OVERTIME = "overtime";
    public static final String IMPORTANT_DATES = "important_dates";
    public static final String VACATION = "vacation";
    public static final String PAYROLL = "payroll";
    public static final String CALENDAR_SYNC = "calendar_sync";
    public static final String NOTIFICATIONS = "notifications";
    public static final String TELEGRAM = "telegram";
    public static final String SCENARIOS = "scenarios";
    public static final String ADMIN = "admin";
}
