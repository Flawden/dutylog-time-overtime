# DutyLog API v27.9.3

Проект: **DutyLog: Time & Overtime**.

Веб-версия работает через `JSESSIONID`, Android/PWA-клиенты могут использовать `Authorization: Bearer <accessToken>`. Старые endpoint'ы сохранены, поверх них добавлен mobile-слой.





## Telegram web API

Эти endpoint’ы используются браузерной вкладкой `⚙ → Telegram` и защищены обычной web-сессией + CSRF. Mobile/Bearer API они не заменяют.

### GET `/api/telegram/status`

Возвращает состояние Telegram-интеграции для текущего пользователя:

```json
{
  "configured": true,
  "pollingEnabled": true,
  "linked": false,
  "enabled": true,
  "notificationsEnabled": false,
  "botUsername": "DutyLogBot",
  "chatId": null,
  "username": null,
  "linkedAt": null,
  "pendingCode": null,
  "pendingCodeExpiresAt": null
}
```

### POST `/api/telegram/link-code`

Создаёт одноразовый код привязки Telegram. Старый неиспользованный код пользователя удаляется.

```json
{
  "code": "DL-123456",
  "expiresAt": "2026-07-06T10:30:00Z",
  "startCommand": "/start DL-123456",
  "deepLink": "https://t.me/DutyLogBot?start=DL-123456"
}
```

### DELETE `/api/telegram/link`

Отключает Telegram от текущего аккаунта и удаляет активные коды привязки.

### PATCH `/api/telegram/settings`

Обновляет настройки Telegram-привязки текущего web-пользователя. Сейчас используется для включения/выключения Telegram-напоминаний. Endpoint защищён web-сессией + CSRF.

```json
{
  "notificationsEnabled": true
}
```

Ответ совпадает с `GET /api/telegram/status`.

## Telegram bot commands

Команды обрабатываются backend’ом через long polling, если включены `DUTYLOG_TELEGRAM_ENABLED=true`, `DUTYLOG_TELEGRAM_BOT_TOKEN=...`, `DUTYLOG_TELEGRAM_POLLING_ENABLED=true`.

- `/start DL-123456` — привязать Telegram к аккаунту.
- `/today`, `/сегодня` — сводка на сегодня.
- `/tomorrow`, `/завтра` — сводка на завтра.
- `/week`, `/неделя` — ближайшие 7 дней.
- `/tasks`, `/задачи` — открытые задачи.
- `/balance`, `/баланс`, `/overtime` — баланс переработок.
- `/help` — список команд.

Изменяющие команды v20.2:

- `/task текст` — создать задачу на сегодня.
- `/task завтра текст` или `/task 2026-07-10 текст` — создать задачу на указанную дату.
- `/done 12` — закрыть задачу по id из `/tasks`.
- `/ppr 17-08 причина` — начислить переработку интервалом; если конец раньше начала, конец переносится на следующий день.
- `/ppr 2 причина` — ручное начисление 2 часов.
- `/timeoff 8 причина` — списать 8 часов отгула по FIFO.

Для команд с датой первым аргументом принимаются `yyyy-MM-dd`, `dd.MM`, `dd.MM.yyyy`, `сегодня`, `завтра`. Для интервальной переработки можно добавить служебные токены `обед60` и `план8/план0`, например `/ppr 10.07 17-08 обед60 план0 ППР после смены`.

Telegram-напоминания v20.1 работают отдельно от команд. Для них должны быть включены Telegram, polling и `DUTYLOG_TELEGRAM_NOTIFICATIONS_ENABLED=true`.

## Time Foundation

Time Foundation exposes one server `Instant` and two explicit IANA projections for every authenticated client.

### GET `/api/time/context`

Legacy web alias. Requires the normal authenticated web session.

### GET `/api/v1/time/context`

Versioned alias for web/mobile clients.

Example response:

```json
{
  "nowInstant": "2026-07-25T12:00:00Z",
  "workTimezone": "Europe/Chisinau",
  "displayTimezone": "Europe/Berlin",
  "workLocalDateTime": "2026-07-25T15:00:00",
  "displayLocalDateTime": "2026-07-25T14:00:00",
  "workDate": "2026-07-25",
  "displayDate": "2026-07-25",
  "workOffset": "+03:00",
  "displayOffset": "+02:00",
  "sameTimezone": false
}
```

`workTimezone` owns calendar calculations, shifts, deadlines and future overtime intervals. `displayTimezone` only changes how absolute moments are shown. Floating dates are never converted between the two zones.

### Dated shift projection (`DayDto.shiftInterval`)

A day with a shift type that has both start/end clock values includes one calculated absolute interval:

```json
{
  "date": "2026-07-25",
  "shiftTypeId": 1,
  "shiftInterval": {
    "startInstant": "2026-07-25T03:30:00Z",
    "endInstant": "2026-07-25T12:00:00Z",
    "workStart": "2026-07-25T08:30",
    "workEnd": "2026-07-25T17:00",
    "displayStart": "2026-07-25T06:30",
    "displayEnd": "2026-07-25T15:00",
    "workTimezone": "Asia/Yekaterinburg",
    "displayTimezone": "Europe/Moscow",
    "breakMinutes": 30,
    "elapsedMinutes": 510,
    "netMinutes": 480,
    "crossesWorkMidnight": false,
    "crossesDisplayMidnight": false,
    "sameTimezone": false
  }
}
```

`shiftInterval` is read-only projection data. Clients still write the existing `shiftTypeId`; changing display timezone does not rewrite the day or shift type.

## Web profile sessions

Эти endpoint’ы используются браузерным UI профиля и защищены обычной web-сессией + CSRF. Они дублируют управление мобильными устройствами, но живут вне `/api/mobile/**`, чтобы mobile API оставался stateless/Bearer.

### GET `/api/profile/sessions`

Возвращает список мобильных устройств пользователя. Формат ответа совпадает с `GET /api/mobile/auth/sessions`.

### DELETE `/api/profile/sessions/{id}`

Отзывает мобильную сессию пользователя. Используется кнопкой `отозвать` во вкладке профиля.

### GET `/api/profile`

Возвращает профиль текущего пользователя, включая персонализацию интерфейса:

```json
{
  "username": "alex",
  "displayName": "Алексей",
  "birthday": "2000-07-07",
  "admin": true,
  "role": "ADMIN",
  "accountTier": "FREE",
  "themePreference": "system",
  "accentColor": "#F5B841",
  "themePreset": "midnight",
  "themeConfig": {
    "appBg": "#0F1220",
    "panelBg": "#181C2B",
    "panelAltBg": "#20263A",
    "textColor": "#EEF2FF",
    "mutedColor": "#A7B0C9",
    "borderColor": "#2D3550",
    "buttonStyle": "soft",
    "cardStyle": "contrast",
    "shadowLevel": "medium",
    "density": "comfortable",
    "cardRadius": 16
  },
  "languagePreference": "ru",
  "workTimezone": "Europe/Chisinau",
  "displayTimezone": "Europe/Berlin",
  "onboardingCompleted": true
}
```

### PUT `/api/profile`

Обновляет профиль и/или внешний вид. `themePreference`: `system`, `light`, `dark`. `accentColor`: `#RRGGBB`. `themeConfig` принимает только whitelist-поля Theme Builder, без пользовательского CSS.

```json
{
  "displayName": "Алексей",
  "birthday": "2000-07-07",
  "themePreference": "dark",
  "accentColor": "#7B8CE0",
  "themePreset": "custom",
  "themeConfig": {
    "appBg": "#0F1220",
    "panelBg": "#181C2B",
    "panelAltBg": "#20263A",
    "textColor": "#EEF2FF",
    "mutedColor": "#A7B0C9",
    "borderColor": "#2D3550",
    "buttonStyle": "soft",
    "cardStyle": "contrast",
    "shadowLevel": "medium",
    "density": "comfortable",
    "cardRadius": 16
  },
  "languagePreference": "en",
  "workTimezone": "Europe/Chisinau",
  "displayTimezone": "Europe/Berlin",
  "onboardingCompleted": true
}
```

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
      "startInstant": null,
      "endInstant": null,
      "sourceTimezone": null,
      "displayStart": null,
      "displayEnd": null,
      "displayTimezone": null,
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

Для новых рассчитанных записей backend сохраняет абсолютные `startInstant` / `endInstant` и `sourceTimezone`. Длительность считается между Instant, поэтому DST-gap/overlap учитывается автоматически. Ответ также содержит `displayStart`, `displayEnd` и `displayTimezone`; исходные `startDateTime`, `endDateTime` и `timeRange` остаются рабочей проекцией.

Исторические строки без сохранённой исходной зоны остаются local-only и возвращают абсолютные поля как `null`. Они не конвертируются через текущую зону пользователя.

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
  "dayEmoji": "🔥",
  "overtimeHours": 7,
  "timeOffHours": 0,
  "overtimeBalanceHours": 7,
  "shiftInterval": null
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
    { "date": "2026-06-18", "shiftTypeId": 1, "note": null, "dayEmoji": "🔥", "overtimeHours": 7, "timeOffHours": 0, "overtimeBalanceHours": 7 }
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
  "dayEmoji": "🔥",
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

### GET `/api/tasks/board`

Общий список задач для отдельного экрана задач в Web/PWA и будущего Android-клиента.

Параметры:

- `status`: `open`, `overdue`, `upcoming`, `done`, `all`; по умолчанию `open`;
- `category`: фильтр по категории, опционально;
- `priority`: `LOW`, `NORMAL`, `HIGH`, `URGENT`, опционально;
- `q`: поиск по тексту, категории, дате и приоритету;
- `from`, `to`: период в формате `yyyy-MM-dd`. Для фильтра используется срок задачи, а если срока нет — дата задачи.

Примеры:

```http
GET /api/tasks/board?status=open
GET /api/tasks/board?status=overdue
GET /api/tasks/board?category=здоровье&priority=HIGH
GET /api/tasks/board?from=2026-07-01&to=2026-07-31&q=врач
```

Ответ: массив `TaskDto`.


## System diagnostics


### GET `/api/auth/registration-status`

Публичный endpoint для страницы входа. Авторизация не нужна. Возвращает, можно ли показывать форму регистрации обычных пользователей. Backend всё равно проверяет эту настройку при `POST /api/auth/register`.

```json
{
  "enabled": false,
  "mode": "closed",
  "source": "database",
  "updatedAt": "2026-07-07T09:45:00Z",
  "updatedBy": "admin"
}
```

### GET `/api/admin/settings/registration`

Возвращает ту же настройку публичной регистрации, но только для администратора. Используется карточкой `Система` → `Публичная регистрация`.

### PATCH `/api/admin/settings/registration`

Меняет публичную регистрацию. Требует web-сессию администратора и CSRF-заголовок.

```json
{ "enabled": false }
```

Если регистрация закрыта, `POST /api/auth/register` возвращает `403` и JSON-ошибку. UI-регистрация администраторов отсутствует: стартовый админ создаётся только через env bootstrap.

### GET `/api/admin/status`

Служебная диагностика для профиля администратора `Система`. Endpoint требует обычную web-сессию и роль `ADMIN`; для обычного пользователя возвращает `403`. CSRF-cookie не нужен, потому что это `GET`. Секреты не отдаются: токен Telegram, пароли и URL базы данных не раскрываются.

Пример ответа:

```json
{
  "app": "DutyLog: Time & Overtime",
  "version": "27.2.5",
  "serverTime": "2026-07-06T11:40:00Z",
  "serverTimezone": "Europe/Moscow",
  "profiles": ["prod"],
  "database": { "ok": true },
  "registration": {
    "enabled": false,
    "mode": "closed",
    "source": "database",
    "updatedAt": "2026-07-07T09:45:00Z",
    "updatedBy": "admin"
  },
  "telegram": {
    "enabled": true,
    "tokenConfigured": true,
    "pollingEnabled": true,
    "notificationsEnabled": true,
    "configured": true,
    "linked": true,
    "accountNotificationsEnabled": true,
    "botUsername": "DutyLogBot"
  }
}
```

### Request diagnostics logs

`RequestDiagnosticsFilter` добавляет в ответ заголовок `X-Request-Id` и пишет в логи:

```text
GET /api/admin/status -> 200 (12 ms, requestId=1a2b3c4d)
```

Уровень логирования можно поменять переменной окружения:

```env
DUTYLOG_REQUEST_LOG_LEVEL=INFO
```


## Backup and restore

Backup and restore are intentionally not exposed as public HTTP API in this version. They are operational actions performed on the server through scripts:

```text
deploy/scripts/backup-postgres.sh
deploy/scripts/restore-postgres.sh
deploy/scripts/list-backups.sh
```

This keeps database dumps, secrets and file-system access outside the user-facing web API.


## Admin users and roles

All endpoints below require an authenticated `ADMIN` session.

### GET /api/admin/users

Returns all users with role metadata. Password hashes are never returned.

```json
[
  {
    "id": 1,
    "username": "admin",
    "displayName": null,
    "role": "ADMIN",
    "accountTier": "FREE",
    "bootstrapAdmin": true,
    "currentUser": true,
    "createdAt": "2026-07-07T10:00:00Z",
    "updatedAt": "2026-07-07T10:00:00Z"
  }
]
```

### PATCH /api/admin/users/{id}/role

```json
{ "role": "ADMIN" }
```

Allowed roles in v22.3: `USER`, `ADMIN`. Public registration still creates only `USER`.

### POST /api/admin/users/{id}/password

```json
{ "newPassword": "long_new_password" }
```

Admin reset requires at least 12 characters and revokes mobile tokens for the target user.


## v23.1.3 pagination notes

Large UI lists are paged server-side before being returned to the browser. Supported query params:

- `GET /api/admin/users?page=0&size=50&q=&role=all` — admin users page.
- `GET /api/tasks/board?page=0&size=50&status=open&category=&priority=&q=&from=&to=` — global task board page.
- `GET /api/overtime/account-page?page=0&size=50&from=&to=&status=all&q=` — overtime ledger page with account summary.

Backend caps `size` to max `100`. CSV/XLS export endpoints are intentionally not paged: they export all rows matching selected filters.


## v24.0/v26.0 profile preferences

`GET /api/profile` includes `languagePreference` with allowed values `ru` or `en` and `onboardingCompleted` for first-run module setup.

`PUT /api/profile` accepts `languagePreference` and validates it against `ru/en`. The setting controls the web/PWA interface language and does not affect roles or account tier.

Since v26.0 `PUT /api/profile` also accepts `onboardingCompleted`. This is only a UX flag for the first-run module onboarding; it does not grant permissions and does not alter user data.


## v25.0 user modules

### GET /api/modules

Returns the current user's module registry with effective enabled flags. Core modules are always enabled. Admin module is visible only to administrators. Since v25.3 each item also includes developer contract metadata: `category`, `order`, `uiSlots`, `apiPrefixes` and `offlineQueueTypes`.

### GET /api/modules/contracts

Returns the same effective module payload as `GET /api/modules`, but the endpoint name makes contract usage explicit for clients, diagnostics and future tests.

### PATCH /api/modules

Request body:

```json
{
  "enabled": {
    "notes": true,
    "tasks": false,
    "overtime": true,
    "telegram": false
  }
}
```

Unknown keys and locked modules are ignored. Dependencies are enabled automatically. Disabled feature APIs return HTTP 403 with `code: MODULE_DISABLED`, structured `moduleKey: <key>`, and the legacy `MODULE_DISABLED:<key>` message.

## Notes export

### `GET /api/export/notes`

Downloads every non-empty day note owned by the authenticated user as an Obsidian-friendly ZIP archive.

Response:

```http
HTTP/1.1 200 OK
Content-Type: application/zip
Content-Disposition: attachment; filename="dutylog-notes-20260710.zip"
Cache-Control: no-store, must-revalidate
Pragma: no-cache
```

Archive layout:

```text
2025/2025-12-31.md
2026/2026-07-03.md
README.md
```

Security/limits:

- authentication required;
- owner filter is applied in the repository query;
- blank notes are skipped;
- YAML metadata is escaped;
- ZIP paths are date-derived;
- export count and uncompressed bytes are capped;
- `413 Payload Too Large` is returned when a configured cap is exceeded.

---

## Android API v1 (v27.2.5)

New Android code must use `/api/v1/**`. The complete contract and compatibility rules are in [`ANDROID_API_V1.md`](ANDROID_API_V1.md); the canonical OpenAPI file is served from `/openapi/dutylog-v1.yaml`.

Key endpoints:

```text
GET    /api/v1/mobile/auth/registration-status
POST   /api/v1/mobile/auth/register
POST   /api/v1/mobile/auth/login
POST   /api/v1/mobile/auth/refresh
POST   /api/v1/mobile/auth/logout
GET    /api/v1/mobile/auth/me
GET    /api/v1/mobile/auth/sessions
DELETE /api/v1/mobile/auth/sessions/{id}
GET    /api/v1/mobile/bootstrap?from=YYYY-MM-DD&to=YYYY-MM-DD
POST   /api/v1/mobile/sync
```

Shared user APIs also have `/api/v1` aliases for calendar, days, tasks, important dates, overtime, shift types, profile, modules, notifications, quick scenarios and note export.


## Overtime interval provenance (v27.9.0)

`GET /api/overtime/account` and `/api/v1/overtime/account` expose integer minutes and exact allocation intervals in addition to backward-compatible decimal hours.

Legacy migration:

```text
POST /api/overtime/legacy-credits/preview
POST /api/overtime/legacy-credits/migrate
```

The same routes exist below `/api/v1`. Requests contain selected `creditIds` and an explicit IANA `sourceTimezone`. Preview is read-only; migrate persists exact instants and rebuilds FIFO. Ownership and overtime-module guards are unchanged.

Profile responses still include `workTimezone` and `displayTimezone`, but v27.9.0 treats them as compatibility aliases of one canonical timezone.
