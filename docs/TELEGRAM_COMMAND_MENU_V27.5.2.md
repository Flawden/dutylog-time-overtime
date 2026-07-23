# Telegram command menu and quick actions — v27.5.2

## Product goal

The user must not remember or invent Telegram commands. DutyLog exposes the supported command set in Telegram itself and keeps the most common read-only actions one tap away.

## Registered command menu

DutyLog calls `setMyCommands` after startup and refreshes the menu periodically. The default menu contains:

- `/today` — summary for today;
- `/tomorrow` — summary for tomorrow;
- `/week` — next seven days;
- `/tasks` — open tasks;
- `/balance` — overtime balance;
- `/task` — add a task;
- `/done` — complete a task by id;
- `/ppr` — add overtime;
- `/timeoff` — use overtime balance;
- `/help` — full syntax and examples.

A failed Telegram request is logged with the token redacted. The scheduled refresh retries later without blocking polling, notifications or application startup.

## Persistent quick-action keyboard

Bot replies include a compact `ReplyKeyboardMarkup`:

```text
[ Сегодня ] [ Завтра ]
[ Задачи  ] [ Баланс ]
[ Неделя  ] [ Помощь ]
```

The labels are handled as aliases for the corresponding slash commands. They reuse the same account timezone, authorization and data-isolation rules.

## Configuration

```text
DUTYLOG_TELEGRAM_COMMAND_MENU_ENABLED=true
DUTYLOG_TELEGRAM_COMMAND_MENU_INITIAL_DELAY_MS=7000
DUTYLOG_TELEGRAM_COMMAND_MENU_REFRESH_MS=21600000
```

The defaults work without adding these keys to an existing server `.env`.

## Out of scope

- per-user command localization;
- destructive one-tap actions;
- free-form natural-language parsing;
- Telegram as a replacement for the full web editor.

These can be layered on later without changing the command registry contract.
