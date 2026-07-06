# Android API plan

## Текущее состояние v13

Backend получил mobile-слой и полноценную бухгалтерию переработок:

```text
POST   /api/mobile/auth/login
POST   /api/mobile/auth/refresh
POST   /api/mobile/auth/logout
GET    /api/mobile/auth/me
GET    /api/mobile/auth/sessions
DELETE /api/mobile/auth/sessions/{id}
GET    /api/mobile/bootstrap?from=&to=
POST   /api/mobile/sync
GET    /api/overtime/account
POST   /api/overtime/credits
POST   /api/overtime/usages
```

Android теперь не обязан работать через cookie-сессию. Он логинится, получает `accessToken` и `refreshToken`, а дальше отправляет:

```http
Authorization: Bearer <accessToken>
```

`GET /api/mobile/bootstrap?from=&to=` — главный стартовый запрос: профиль, смены, дни, задачи, важные дни, месячный legacy-баланс и `overtimeAccount` с полным остатком переработки в одном ответе.

`POST /api/mobile/sync` — первый простой слой offline-синхронизации. Пока он синхронизирует изменения дней: смена и заметка. Задачи, важные дни и журнал переработок идут через отдельные endpoint’ы. Для Android это удобнее: начисления и списания не теряются между месяцами.

## Рекомендуемая Android-архитектура

```text
Kotlin + Jetpack Compose
Retrofit/OkHttp
Room
EncryptedSharedPreferences
WorkManager
AlarmManager позже для будильника
```

## Что ещё нужно для настоящего Android-клиента

### v14 — offline-first Android

Android должен хранить календарь локально:

```text
Room DB
├─ shift_types
├─ day_entries
├─ day_tasks
├─ important_days
├─ overtime_credits
├─ overtime_usages
├─ overtime_allocations
└─ sync_queue
```

Алгоритм:

1. Приложение загружает `/api/calendar?from=&to=`.
2. Сохраняет ответ в Room.
3. Пользователь может редактировать без интернета.
4. Изменения попадают в `sync_queue`.
5. WorkManager отправляет очередь на backend, когда сеть появилась.

### v15 — уведомления и будильник

Для смен нужны поля:

```text
startTime
endTime
notifyBeforeMinutes
alarmEnabled
```

Для задач и важных дней:

```text
notifyAt
notifyBeforeMinutes
notificationEnabled
```

Android:

- обычные напоминания через WorkManager/notifications;
- серьёзный будильник через AlarmManager;
- экран подтверждения “я проснулся / смена началась”.

### v16 — Telegram bot

Бот должен быть отдельным клиентом к тому же backend:

```text
Telegram Bot → Spring API → PostgreSQL
```

Команды:

```text
/сегодня
/завтра
/баланс
/ппр 7
/отгул 8
/задача Передать документы
/др Макс 18.06
```
