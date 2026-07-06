# План API под Android

## Общая схема

```text
Android app ─┐
             ├── Spring Boot API ── PostgreSQL
Web app ─────┘
Telegram bot ┘
```

Backend — единственный источник правды. Web, Android и Telegram только отображают и меняют данные через API.

## Что Android должен уметь

- войти в аккаунт;
- загрузить календарь за диапазон дат;
- изменить смену дня;
- изменить заметку;
- записать переработку;
- списать отгул;
- заполнить график;
- посмотреть баланс переработки;
- работать без интернета и синхронизироваться позже.

## Рекомендуемые endpoint'ы

### Auth

```text
POST /api/auth/login
POST /api/auth/refresh
POST /api/auth/logout
GET  /api/auth/me
```

### Calendar

```text
GET /api/calendar?from=2026-06-01&to=2026-07-31
PUT /api/days/{date}
POST /api/days/fill
```

### Shift types

```text
GET    /api/shift-types
POST   /api/shift-types
PUT    /api/shift-types/{id}
DELETE /api/shift-types/{id}
```

### Overtime

```text
GET  /api/overtime/balance?from=2026-01-01&to=2026-12-31
GET  /api/overtime/journal?from=2026-06-01&to=2026-06-30
POST /api/overtime/night-work
POST /api/overtime/write-off
```

## Пример ответа календаря

```json
{
  "from": "2026-06-01",
  "to": "2026-07-31",
  "shiftTypes": [
    {"id": 1, "name": "Дневная", "hours": 8, "color": "#F5B841", "builtin": true},
    {"id": 2, "name": "Ночная", "hours": 8, "color": "#7B8CE0", "builtin": true},
    {"id": 3, "name": "Выходной", "hours": 0, "color": "#6FBF73", "builtin": true}
  ],
  "days": [
    {
      "date": "2026-06-18",
      "shiftTypeId": 1,
      "note": "ППР после смены",
      "overtimeHours": 7,
      "timeOffHours": 0,
      "overtimeBalanceHours": 7
    }
  ],
  "summary": {
    "plannedHours": 168,
    "overtimeHours": 15,
    "timeOffHours": 8,
    "balanceHours": 7
  }
}
```

## Offline-режим Android

Рекомендуемая схема:

```text
Room local DB
├─ days
├─ shift_types
└─ pending_sync_operations
```

Когда интернета нет, Android пишет изменения локально и добавляет операцию в очередь. Когда интернет появился, приложение отправляет очередь на backend.

## Конфликты синхронизации

В будущем стоит добавить поля:

```text
createdAt
updatedAt
version
```

Тогда Android сможет понять, что запись менялась на другом устройстве.

## Push/будильник

Для обычных напоминаний:

- WorkManager;
- локальные notifications.

Для настоящего будильника перед сменой:

- AlarmManager;
- разрешение на точные будильники;
- отдельное поле `alarmEnabled` у смены или события.
