# Shift Calendar API v11

Пока авторизация остаётся сессионной (`JSESSIONID`), чтобы не ломать веб-версию. Android на следующем этапе лучше перевести на JWT/refresh-token, но календарные endpoint'ы уже подготовлены под мобильный клиент.

## Базовые сущности

### ShiftTypeDto

```json
{
  "id": 1,
  "name": "Дневная",
  "hours": 8,
  "color": "#F5B841",
  "builtin": true
}
```

### DayDto

```json
{
  "date": "2026-06-18",
  "shiftTypeId": 1,
  "note": "ППР после смены",
  "overtimeHours": 7,
  "timeOffHours": 0,
  "overtimeBalanceHours": 7
}
```

### TaskDto

```json
{
  "id": 10,
  "date": "2026-06-18",
  "text": "Передать документы",
  "done": false
}
```

### ImportantDayOccurrenceDto

```json
{
  "id": 5,
  "date": "2026-06-18",
  "title": "День рождения Макса",
  "repeatMode": "YEARLY",
  "color": "#F5B841"
}
```

## Android-friendly календарь

### GET `/api/calendar?from=2026-06-01&to=2026-07-31`

Один главный запрос для Android/PWA: отдаёт диапазон дней, типы смен, задачи, важные дни и сводку переработок.

```json
{
  "from": "2026-06-01",
  "to": "2026-07-31",
  "shiftTypes": [
    { "id": 1, "name": "Дневная", "hours": 8, "color": "#F5B841", "builtin": true },
    { "id": 2, "name": "Ночная", "hours": 8, "color": "#7B8CE0", "builtin": true },
    { "id": 3, "name": "Выходной", "hours": 0, "color": "#6FBF73", "builtin": true }
  ],
  "days": [
    { "date": "2026-06-18", "shiftTypeId": 1, "note": null, "overtimeHours": 7, "timeOffHours": 0, "overtimeBalanceHours": 7 }
  ],
  "tasks": [
    { "id": 10, "date": "2026-06-18", "text": "Передать документы", "done": false }
  ],
  "importantDays": [
    { "id": 5, "date": "2026-06-18", "title": "День рождения Макса", "repeatMode": "YEARLY", "color": "#F5B841" }
  ],
  "overtime": {
    "from": "2026-06-01",
    "to": "2026-07-31",
    "overtimeHours": 15,
    "timeOffHours": 8,
    "balanceHours": 7
  }
}
```

Ограничение диапазона: максимум 366 дней за один запрос.

## Совместимые endpoint'ы веб-версии

### GET `/api/days?year=2026&month=7`

Старый endpoint. Возвращает список `DayDto` только за месяц. В новой веб-версии для календаря используется `/api/calendar`, потому что он также отдаёт задачи и важные дни.

### PUT `/api/days/{date}`

Создать/обновить день.

```json
{
  "shiftTypeId": 1,
  "note": "# Markdown заметка",
  "overtimeHours": 7,
  "timeOffHours": 0
}
```

Если день полностью пустой — backend удалит запись и вернёт `204 No Content`.

### POST `/api/days/fill`

Массово заполнить график.

```json
{
  "startDate": "2026-06-29",
  "days": 31,
  "shiftTypeIds": [1, 1, 3, 3],
  "overwriteExistingShift": true
}
```

Меняет только `shiftTypeId`. Заметки, переработки, отгулы, задачи и важные дни не стирает.

## Задачи дня

### GET `/api/tasks?date=2026-06-18`

Вернуть задачи одного дня.

### GET `/api/tasks?from=2026-06-01&to=2026-06-30`

Вернуть задачи диапазона.

### POST `/api/tasks`

```json
{
  "date": "2026-06-18",
  "text": "Передать документы"
}
```

### PATCH `/api/tasks/{id}`

Отметить задачу выполненной:

```json
{
  "done": true
}
```

Переименовать задачу:

```json
{
  "text": "Передать документы начальнику смены"
}
```

### DELETE `/api/tasks/{id}`

Удалить задачу.

## Важные дни

### GET `/api/important-days`

Вернуть список важных дней как настроек.

### GET `/api/important-days/occurrences?from=2026-06-01&to=2026-06-30`

Вернуть развёрнутые повторения важных дней внутри диапазона.

### POST `/api/important-days`

```json
{
  "title": "День рождения Макса",
  "date": "2002-06-18",
  "repeatMode": "YEARLY",
  "color": "#F5B841"
}
```

`repeatMode`:

```text
NONE    — один раз
MONTHLY — каждый месяц
YEARLY  — каждый год
```

### PATCH `/api/important-days/{id}`

```json
{
  "title": "День рождения Максима",
  "repeatMode": "YEARLY",
  "color": "#E0653A"
}
```

### DELETE `/api/important-days/{id}`

Удалить важный день полностью, включая будущие повторения.

## Переработки и отгулы

### GET `/api/overtime/balance?from=2026-06-01&to=2026-06-30`

```json
{
  "from": "2026-06-01",
  "to": "2026-06-30",
  "overtimeHours": 15,
  "timeOffHours": 8,
  "balanceHours": 7
}
```

### GET `/api/overtime/ledger?from=2026-06-01&to=2026-06-30`

Журнал только тех дней, где есть переработка или списание отгула.

## Типы смен

### GET `/api/shift-types`

Возвращает список типов смен текущего пользователя. Заодно гарантирует наличие встроенных `Дневная`, `Ночная`, `Выходной`.

### POST `/api/shift-types`

```json
{
  "name": "12 часов",
  "hours": 12,
  "color": "#E0653A"
}
```

### DELETE `/api/shift-types/{id}`

Удаляет только пользовательскую смену. Встроенные смены возвращают `409 Conflict`.

## Формат ошибок

```json
{
  "error": "Дата from должна быть в формате yyyy-MM-dd"
}
```

Для ошибок валидации может быть дополнительное поле `fields`.
