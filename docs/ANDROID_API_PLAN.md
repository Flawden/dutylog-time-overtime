# Android API plan

## Текущее состояние v11

Backend уже получил Android-friendly endpoint:

```text
GET /api/calendar?from=2026-06-01&to=2026-07-31
```

Он отдаёт одним ответом:

- диапазон календарных дней;
- типы смен пользователя;
- заметки и отметки по дням;
- переработку и списания отгула;
- задачи дня;
- важные дни и их повторения;
- сводку баланса переработки/отгулов.

Также есть отдельные endpoint'ы:

```text
GET /api/overtime/balance?from=&to=
GET /api/overtime/ledger?from=&to=
GET/POST/PATCH/DELETE /api/tasks
GET/POST/PATCH/DELETE /api/important-days
GET /api/important-days/occurrences?from=&to=
```

Это уже удобно для Android-экранов:

- календарь;
- экран дня;
- задачи дня;
- важные дни/дни рождения;
- баланс переработок;
- журнал переработок;
- список смен.

## Что ещё нужно для настоящего Android-клиента

### v12 — авторизация для Android

Сейчас авторизация сессионная через cookie `JSESSIONID`. Для браузера это удобно, но Android лучше перевести на токены:

```text
POST /api/auth/login
POST /api/auth/refresh
POST /api/auth/logout
GET  /api/auth/me
```

План:

- access token на короткое время;
- refresh token на долгий срок;
- хранение refresh token в БД;
- возможность выйти со всех устройств;
- Android хранит токены в EncryptedSharedPreferences.

### v13 — offline-first Android

Android должен хранить календарь локально:

```text
Room DB
├─ shift_types
├─ day_entries
├─ day_tasks
├─ important_days
└─ sync_queue
```

Алгоритм:

1. Приложение загружает `/api/calendar?from=&to=`.
2. Сохраняет ответ в Room.
3. Пользователь может редактировать без интернета.
4. Изменения попадают в `sync_queue`.
5. WorkManager отправляет очередь на backend, когда сеть появилась.

### v14 — уведомления и будильник

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

### v15 — Telegram bot

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
