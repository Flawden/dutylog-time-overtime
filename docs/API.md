# Shift Calendar API v14.6

Веб-версия работает через `JSESSIONID`, Android/PWA-клиенты могут использовать `Authorization: Bearer <accessToken>`. Старые endpoint'ы сохранены, поверх них добавлен mobile-слой.



## Mobile auth

### POST `/api/mobile/auth/login`

```json
{
  "username": "alex",
  "password": "secret123",
  "deviceName": "Galaxy S23 Plus"
}
```

Ответ:

```json
{
  "tokenType": "Bearer",
  "accessToken": "...",
  "accessExpiresAt": "2026-07-02T18:00:00Z",
  "refreshToken": "...",
  "refreshExpiresAt": "2026-08-16T17:30:00Z",
  "user": {
    "username": "alex"
  }
}
```

Android хранит `accessToken` для обычных запросов и `refreshToken` для получения новой пары токенов. Для запросов:

```http
Authorization: Bearer <accessToken>
```

### POST `/api/mobile/auth/refresh`

```json
{
  "refreshToken": "..."
}
```

Refresh token ротируется: ответ содержит новую пару `accessToken` + `refreshToken`, старую пару надо забыть.

### POST `/api/mobile/auth/logout`

```json
{
  "refreshToken": "..."
}
```

Можно также вызвать с `Authorization: Bearer ...` без тела — будет отозвана текущая access-сессия.

### GET `/api/mobile/auth/me`

Возвращает пользователя текущего Bearer-токена:

```json
{
  "username": "alex"
}
```

### GET `/api/mobile/auth/sessions`

Список мобильных устройств/сессий:

```json
[
  {
    "id": 1,
    "deviceName": "Galaxy S23 Plus",
    "createdAt": "2026-07-02T17:00:00Z",
    "lastUsedAt": "2026-07-02T17:05:00Z",
    "refreshExpiresAt": "2026-08-16T17:00:00Z",
    "revoked": false,
    "active": true
  }
]
```

### DELETE `/api/mobile/auth/sessions/{id}`

Отозвать конкретную мобильную сессию.

## Mobile bootstrap

### GET `/api/mobile/bootstrap?from=2026-07-01&to=2026-08-31`

Первый запрос после старта Android-приложения. Возвращает server time, профиль и полный диапазон календаря:

```json
{
  "serverTime": "2026-07-02T17:10:00Z",
  "user": { "username": "alex" },
  "calendar": {
    "from": "2026-07-01",
    "to": "2026-08-31",
    "shiftTypes": [],
    "days": [],
    "tasks": [],
    "importantDays": [],
    "overtime": {
      "from": "2026-07-01",
      "to": "2026-08-31",
      "overtimeHours": 0,
      "timeOffHours": 0,
      "balanceHours": 0
    },
    "overtimeAccount": {
      "totalEarnedHours": 0,
      "totalUsedHours": 0,
      "balanceHours": 0,
      "credits": [],
      "usages": []
    }
  }
}
```

## Mobile sync

### POST `/api/mobile/sync`

Пакетная синхронизация изменений дней из offline-очереди Android. Сейчас sync покрывает `DayEntry`: смена, заметка, переработка, отгул. Задачи и важные дни пока отправляются обычными endpoint’ами `/api/tasks` и `/api/important-days`.

```json
{
  "days": [
    {
      "date": "2026-07-02",
      "shiftTypeId": 1,
      "note": "ППР после смены",
      "overtimeHours": 7,
      "timeOffHours": 0
    },
    {
      "date": "2026-07-03",
      "clearShiftType": true,
      "clearNote": true,
      "overtimeHours": 0,
      "timeOffHours": 8
    }
  ]
}
```

Ответ:

```json
{
  "serverTime": "2026-07-02T17:12:00Z",
  "days": [
    {
      "date": "2026-07-02",
      "shiftTypeId": 1,
      "note": "ППР после смены",
      "overtimeHours": 7,
      "timeOffHours": 0,
      "overtimeBalanceHours": 7
    }
  ],
  "warnings": {}
}
```

Patch-правила:

```text
shiftTypeId       — поставить смену
clearShiftType    — очистить смену
note              — заменить заметку
clearNote         — очистить заметку
overtimeHours     — заменить часы переработки
timeOffHours      — заменить списанные часы отгула
```


## Переработка: полноценная бухгалтерия часов

Старые поля `DayDto.overtimeHours` и `DayDto.timeOffHours` оставлены для совместимости, но продуктовый учёт переработок теперь идёт через отдельный журнал начислений и списаний.

### GET `/api/overtime/account`

Возвращает всю таблицу переработок пользователя. Это не месячный отчёт: начисления живут до полного списания.

```json
{
  "totalEarnedHours": 5,
  "totalUsedHours": 4,
  "balanceHours": 1,
  "credits": [
    {
      "id": 10,
      "workedDate": "2026-06-20",
      "timeRange": "15:00–17:00",
      "startDateTime": null,
      "endDateTime": null,
      "breakMinutes": 0,
      "plannedHours": 0,
      "calculated": false,
      "hours": 2,
      "reason": "ППР после смены",
      "usedHours": 2,
      "remainingHours": 0,
      "usages": [
        {
          "usageId": 30,
          "usageDate": "2026-06-23",
          "hours": 2,
          "reason": "Отгул"
        }
      ]
    },
    {
      "id": 11,
      "workedDate": "2026-06-21",
      "timeRange": "15:00–18:00",
      "hours": 3,
      "reason": "Замена смены",
      "usedHours": 2,
      "remainingHours": 1,
      "usages": [
        {
          "usageId": 30,
          "usageDate": "2026-06-23",
          "hours": 2,
          "reason": "Отгул"
        }
      ]
    }
  ],
  "usages": [
    {
      "id": 30,
      "usageDate": "2026-06-23",
      "hours": 4,
      "reason": "Отгул",
      "allocations": [
        {
          "creditId": 10,
          "workedDate": "2026-06-20",
          "timeRange": "15:00–17:00",
          "hours": 2,
          "reason": "ППР после смены"
        },
        {
          "creditId": 11,
          "workedDate": "2026-06-21",
          "timeRange": "15:00–18:00",
          "hours": 2,
          "reason": "Замена смены"
        }
      ]
    }
  ]
}
```

### POST `/api/overtime/credits`

Начислить переработку. Можно передать готовые часы вручную:

```json
{
  "date": "2026-06-20",
  "timeRange": "15:00–17:00",
  "hours": 2,
  "reason": "ППР после смены"
}
```

Или передать интервал, чтобы backend посчитал часы сам:

```json
{
  "date": "2026-05-04",
  "startDateTime": "2026-05-04T20:00",
  "endDateTime": "2026-05-05T08:00",
  "breakMinutes": 60,
  "plannedHours": 0,
  "reason": "Ночная переработка"
}
```

Формула расчёта:

```text
переработка = конец - начало - обед - плановые часы
```

Если интервал проходит через несколько дат, backend создаёт несколько строк начисления. Обычные ночные интервалы режутся по датам, а ровные сутки вида `08:00 → 08:00` следующего дня режутся пополам. Обед и плановые часы вычитаются с начала интервала.

Backend также запрещает пересекающиеся рассчитанные интервалы. Если уже есть `2026-07-03T20:00 → 2026-07-04T08:00`, повторная или частично пересекающаяся запись вернёт `400`.

Примеры:

```text
20:00–08:00, обед 60 мин, план 0 ч => 11 ч переработки
06:30–08:00 следующего дня, обед 30 мин, план 8 ч => 17 ч переработки
17:00–08:00 следующего дня, обед 0 мин, план 0 ч => 15 ч переработки
08:00–08:00 следующего дня, обед 0 мин, план 0 ч => 12 ч на первую дату и 12 ч на вторую дату
```

Ответ — обновлённый `OvertimeAccountDto`.

### PATCH `/api/overtime/credits/{id}`

Отредактировать начисление переработки. Все поля опциональны, но веб обычно отправляет полный набор полей из формы.

Ручная запись:

```json
{
  "date": "2026-06-20",
  "timeRange": "16:00–18:00",
  "hours": 2,
  "reason": "ППР, уточнил время"
}
```

Рассчитанная запись:

```json
{
  "date": "2026-07-03",
  "startDateTime": "2026-07-03T17:00",
  "endDateTime": "2026-07-04T08:00",
  "breakMinutes": 0,
  "plannedHours": 0,
  "reason": "Остался в ночь"
}
```

Защиты:

- нельзя уменьшить начисление ниже уже использованных часов;
- нельзя создать пересечение с другим рассчитанным интервалом;
- если один интервал после редактирования должен разбиться на несколько дат, а старая запись уже использована списаниями, API вернёт ошибку и попросит сначала удалить соответствующие списания.

Ответ — обновлённый `OvertimeAccountDto`.

### POST `/api/overtime/usages`

Списать отгул. Backend сам распределяет часы по FIFO: сначала самые старые остатки.

```json
{
  "date": "2026-06-23",
  "hours": 4,
  "reason": "Отгул"
}
```

Если доступно меньше часов, API вернёт ошибку `400` с понятным текстом.

### PATCH `/api/overtime/usages/{id}`

Отредактировать списание отгула. Если поменять часы, FIFO-распределение будет собрано заново.

```json
{
  "date": "2026-06-24",
  "hours": 6,
  "reason": "Отгул за ППР"
}
```

Если после удаления старого распределения доступных часов не хватает для нового списания, транзакция откатывается: старое списание и его связи сохраняются.

Ответ — обновлённый `OvertimeAccountDto`.

### DELETE `/api/overtime/credits/{id}`

Удалить начисление. Разрешено только если из него ещё ничего не списывали.

### DELETE `/api/overtime/usages/{id}`

Удалить списание. Часы автоматически вернутся в остатки соответствующих начислений.


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
  "color": "#E0653A",
  "startTime": "08:00",
  "endTime": "20:00",
  "breakMinutes": 60,
  "plannedHours": 11
}
```

Поля `startTime`, `endTime`, `breakMinutes`, `plannedHours` необязательные.

### PATCH `/api/shift-types/{id}`

```json
{
  "startTime": "06:30",
  "endTime": "17:00",
  "breakMinutes": 30,
  "plannedHours": 8
}
```

У встроенных смен можно менять время/обед/план/часы, но нельзя менять название и цвет.

### DELETE `/api/shift-types/{id}`

Удаляет только пользовательскую смену. Встроенные смены возвращают `409 Conflict`.

## Формат ошибок

```json
{
  "error": "Дата from должна быть в формате yyyy-MM-dd"
}
```

Для ошибок валидации может быть дополнительное поле `fields`.


## Экспорт переработок

```text
GET /api/overtime/export.csv
GET /api/overtime/export.xls
```

Параметры совпадают с фильтрами таблицы переработок:

```text
from=2026-07-01      // необязательно
to=2026-07-31        // необязательно
status=all|open|partial|closed
q=ППР                // необязательно, поиск по дате/причине/списаниям
```

Примеры:

```text
/api/overtime/export.csv?from=2026-07-01&to=2026-07-31&status=open
/api/overtime/export.xls?q=ППР
```

CSV открывается в Excel с кириллицей за счёт UTF-8 BOM. XLS — Excel-совместимая HTML-таблица, поэтому не требует отдельной зависимости вроде Apache POI.

## Notifications / reminders

### GET `/api/notifications/settings`

Возвращает настройки напоминаний текущего пользователя.

### PATCH `/api/notifications/settings`

Все поля опциональны.

```json
{
  "browserNotificationsEnabled": true,
  "shiftRemindersEnabled": true,
  "shiftReminderMinutesBefore": 60,
  "tomorrowDigestEnabled": false,
  "tomorrowDigestTime": "19:00",
  "taskRemindersEnabled": true,
  "taskReminderTime": "09:00",
  "importantDayRemindersEnabled": true,
  "importantDayDaysBefore": 1,
  "importantDayReminderTime": "09:00"
}
```

### GET `/api/notifications/upcoming?from=2026-07-01&to=2026-07-31`

Возвращает рассчитанные напоминания за диапазон. Эти данные может использовать веб, Android или Telegram-бот.

Типы напоминаний:

- `SHIFT` — перед сменой;
- `TASK` — невыполненная задача дня;
- `IMPORTANT_DAY` — важный день;
- `TOMORROW_DIGEST` — вечерний дайджест на завтра.

Пример ответа:

```json
[
  {
    "id": "shift:2026-07-04",
    "type": "SHIFT",
    "sourceDate": "2026-07-04",
    "remindAt": "2026-07-04T05:30:00",
    "title": "Смена: Дневная",
    "details": "Начало 06:30, напоминание за 60 мин.",
    "priority": 10
  }
]
```

## Уведомления v17.1

### Настройки смены для уведомлений

`ShiftTypeDto` теперь содержит:

```json
{
  "notificationsEnabled": true,
  "notificationMinutesBefore": 90
}
```

`notificationMinutesBefore = null` означает: использовать глобальную настройку из `/api/notifications/settings`.

Для сброса индивидуального значения через `PATCH /api/shift-types/{id}` можно передать:

```json
{
  "notificationMinutesBefore": -1
}
```

### Проверить напоминания на завтра

```http
GET /api/notifications/tomorrow
```

Возвращает список рассчитанных напоминаний для событий завтрашнего дня.

### Напоминания за период

```http
GET /api/notifications/upcoming?from=2026-07-01&to=2026-07-31&includePast=true
```

`includePast=false` скрывает напоминания, время которых уже прошло.

## Quick scenarios

Пользовательские быстрые сценарии заполняют форму переработки по выбранной смене и дате.
Сценарий сам не начисляет часы — он только подставляет начало, конец, обед, план и причину.

### GET /api/quick-scenarios

Возвращает список сценариев пользователя. При первом запросе создаёт стандартные сценарии.

### POST /api/quick-scenarios

Создаёт сценарий.

Поля:

- `name` — название;
- `groupLabel` — короткая метка над названием;
- `description` — описание;
- `startMode` — `SHIFT_START` или `SHIFT_END`;
- `endMode` — `SHIFT_END`, `ADD_MINUTES` или `FIXED_TIME`;
- `endOffsetMinutes` — сколько минут прибавить к старту для `ADD_MINUTES`;
- `endFixedTime` — время `HH:mm` для `FIXED_TIME`;
- `endNextDay` — брать фиксированное время на следующий день;
- `breakMode` — `ZERO`, `SHIFT` или `CUSTOM`;
- `customBreakMinutes` — свой обед в минутах;
- `plannedMode` — `ZERO`, `SHIFT` или `CUSTOM`;
- `customPlannedHours` — свои плановые часы;
- `reasonTemplate` — причина по умолчанию;
- `sortOrder` — порядок вывода.

### PATCH /api/quick-scenarios/{id}

Обновляет сценарий.

### DELETE /api/quick-scenarios/{id}

Удаляет сценарий.

## Tasks 2.0

`TaskDto` теперь содержит дополнительные поля:

```json
{
  "id": 1,
  "date": "2026-08-13",
  "text": "Позвонить врачу",
  "done": false,
  "category": "здоровье",
  "priority": "HIGH",
  "dueDate": "2026-08-13",
  "dueTime": "18:00",
  "reminderEnabled": true,
  "reminderMinutesBefore": 60,
  "overdue": false
}
```

`POST /api/tasks` и `PATCH /api/tasks/{id}` принимают эти поля. Старые клиенты могут продолжать отправлять только `date` и `text`.
