# Roadmap до полноценного продукта

Current release: **v27.19.3 — Task Deadline Validation E2E Contract Hotfix**.

## Текущая продуктовая точка — Tasks & Inbox Next

Статус: **v27.19.3** синхронизирует planned-deadline E2E-контракт поверх v27.19.2, сохраняя Tasks & Inbox Next, asset contract stability и совместимость диапазонов доски.

Сделано к текущей точке:

- all-day, point, exact interval и duration presets;
- overnight mapping на каждый покрытый день;
- канонические instants и source timezone для timed planning;
- дедлайн и напоминание отделены от запланированного интервала;
- read-first details, проектные chips/filters/search;
- hourly calendar использует реальную длительность;
- Inbox search работает с local queue и server rows;
- Flyway V1–V37, Java 17, 97 Java test classes / 507 tests / 28 Playwright scenarios.

Следующий продуктовый этап: **v27.20.0 — Notes & Important Events Next**.

## Этап 1 — production foundation

Статус: начато в v9.

Сделано:

- PostgreSQL для production;
- Flyway-миграции;
- Dockerfile;
- Docker Compose;
- `.env.example`;
- health endpoint;
- nginx-конфиг пример;
- backup script.

Что проверить руками:

1. `docker compose up -d --build`
2. открыть `http://localhost:8080`
3. зарегистрироваться;
4. создать кастомную смену;
5. заполнить график;
6. записать переработку;
7. перезапустить контейнеры;
8. убедиться, что данные остались.

## Этап 2 — нормальная API-архитектура

Статус: основа сделана в v10.

Сделано:

- бизнес-логика вынесена из контроллеров в сервисы;
- добавлен единый формат ошибок;
- добавлен endpoint диапазона дат: `GET /api/calendar?from=...&to=...`;
- добавлен endpoint баланса переработки: `GET /api/overtime/balance?from=...&to=...`;
- добавлен endpoint журнала переработок.

Осталось:

- разнести DTO из одного `Dtos.java` по отдельным файлам;
- добавить OpenAPI/Swagger.

## Этап 3 — важные дни и задачи

Статус: сделано в v11.

Сделано:

- задачи дня с чекбоксами;
- индикатор невыполненных задач на календаре;
- важные дни;
- повторения важных дней: один раз, каждый месяц, каждый год;
- отдельные endpoint'ы под задачи и важные дни;
- `/api/calendar?from=&to=` отдаёт задачи и важные дни вместе с календарём.

## Этап 4 — авторизация для Android

Статус: сделано в v12.

Сделано:

- `POST /api/mobile/auth/login`;
- `POST /api/mobile/auth/refresh`;
- `POST /api/mobile/auth/logout`;
- `GET /api/mobile/auth/me`;
- `GET /api/mobile/auth/sessions`;
- `DELETE /api/mobile/auth/sessions/{id}`;
- access token;
- refresh token с ротацией;
- таблица `mobile_auth_tokens`;
- выход с конкретного устройства;
- хранение хэшей токенов вместо сырых токенов.

Веб оставлен на сессиях, Android/API ходят через Bearer token.

## Этап 5 — бухгалтерия переработок

Статус: сделано в v13.

Сделано:

- начисления переработки вынесены в отдельную сущность;
- списания отгулов вынесены в отдельную сущность;
- списания автоматически распределяются по старым начислениям по FIFO;
- переработка не сгорает при переходе между месяцами;
- добавлена таблица: день, время, часы, причина, использовано, куда списано, остаток;
- добавлены endpoint’ы `/api/overtime/account`, `/api/overtime/credits`, `/api/overtime/usages`.

## Этап 6 — план/факт

Текущая модель уже умеет смену дня + переработку + отгул. Для взрослого табеля лучше разделить:

- плановая смена;
- фактическая работа;
- события/ППР;
- списание отгула;
- комментарии;
- подтверждение месяца.

Пример будущей структуры:

```text
DayEntry
├─ plannedShiftType
├─ actualShiftType
├─ overtimeEvents[]
├─ timeOffEvents[]
└─ note
```

## Этап 7 — быстрые действия

Очень полезные кнопки для реальной жизни:

- «Остался в ночь» → автоматом ставит `+7` сегодня и `+8` завтра;
- «Не вышел после ППР» → списывает часы плановой смены;
- «Списать весь день»;
- «ППР до 00:00»;
- «ППР после 00:00».

## Этап 8 — уведомления

Для PWA/web:

- напоминание перед сменой;
- напоминание вечером заполнить день;
- Telegram-уведомления.

Для Android:

- локальные notifications;
- WorkManager;
- AlarmManager для настоящего будильника.

## Этап 9 — Android

API уже достаточно стабилен для первого Android-клиента. Дальше:

- Kotlin;
- Jetpack Compose;
- Retrofit/Ktor Client;
- Room для offline-кэша;
- sync queue для изменений без интернета;
- локальные уведомления;
- экран календаря;
- экран дня;
- экран баланса переработки.

## Этап 10 — отчёты

- экспорт CSV;
- экспорт Excel;
- отчёт по месяцу;
- отчёт по переработке;
- журнал списаний;
- статистика за год.

## Completed in v27.11.3

- Canonical timezone rebases future shift templates.
- Existing dated shifts remain immutable.
- Shift reminders and Telegram delivery use occurrence instants, including next-day projection.

## Completed in v27.11.4

- Timed task deadlines preserve one absolute instant across canonical timezone changes.
- Task overdue/upcoming classification is instant-based for absolute deadlines.
- Deadline projections may cross calendar dates without moving the task's organisational day.
- Date-only deadlines stay floating.
- Legacy timed task deadlines can be explicitly linked to their real source IANA timezone.
- Browser, mobile and Telegram task reminders consume the same authoritative instant.


## Completed in v27.12.0

- Exact overtime credits are split at midnight in the current canonical timezone.
- Exact FIFO allocation minutes follow the same daily projection without being rebuilt.
- Daily earned, used and remaining totals are additive projections, not persisted copies.
- Ledger filters and exports operate on projected local dates.
- Source-credit edit/delete integrity is preserved across multi-day projections.


## DutyLog Next UI platform sequence after v27.17.3

- `v27.17.3` — Java Contract Build Gate Hotfix.
- `v27.17.4` — UI Core & Workspace Foundation.
- `v27.17.5` — UI Core E2E Accordion Hotfix.
- `v27.17.6` — Classic Sunset.
- `v27.18.0` — Overtime Next.
- `v27.18.1` — Overtime Next E2E Contract Hotfix.
- `v27.18.2` — Overtime Snapshot Sync & Timezone E2E Stabilization Hotfix.
- `v27.18.3` — UI Settings & Button Variants Quality Hotfix.
- `v27.19.0` — Tasks & Inbox Next.
- `v27.19.1` — Task Board Date Range Compatibility Hotfix.
- `v27.19.2` — Frontend Asset Contract Stability Hotfix.
- `v27.19.3` — Task Deadline Validation E2E Contract Hotfix.
- `v27.19.0` — Tasks & Inbox Next, including independent planned task intervals (`start → end`), duration, deadlines and timeline cards.
- `v27.20.0` — Notes & Important Events Next, including all-day/timed/multi-day events, place, description, reminders and read-first event cards.
