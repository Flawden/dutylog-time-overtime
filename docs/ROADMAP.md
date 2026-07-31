# Roadmap до полноценного продукта

Current release: **v27.23.1 — Calendar Sync JSON UTF-8 Contract Hotfix**.

## v27.23.1 — Calendar Sync JSON UTF-8 Contract Hotfix — completed

- [x] MockMvc subscription JSON is decoded explicitly with `StandardCharsets.UTF_8`.
- [x] Token hint stays `prefix…suffix`; the test protects U+2026 without mojibake.
- [x] Runtime, HTTP API, nginx protection and Flyway V41 remain unchanged.
- [x] Baseline stays 107 / 563 / 32.

Next product stage: **v27.24.0 — Workspace, Layout & Theme Studio**.


## v27.23.0 — External Calendar Sync — completed

- [x] RFC 5545 export for a selected range and one important event.
- [x] Shifts, tasks, important events and absences compose into read-only `.ics`.
- [x] Private rolling subscription with SHA-256-only token storage.
- [x] Issue, rotate, revoke and immediate old-token invalidation.
- [x] Responsive Settings UI, Web/v1 API, OpenAPI and browser lifecycle coverage.
- [x] Flyway V41; baseline 107 / 563 / 32.

Next product stage: **v27.24.0 — Workspace, Layout & Theme Studio**.

## v27.22.2 — Workspace-Aware Tasks E2E Navigation Hotfix — completed

- [x] Four stale browser flows use shared workspace-aware `openView()` instead of a hidden Tasks tab.
- [x] Tasks module enablement is asserted with `moduleHidden`, independently from workspace navigation.
- [x] Shift Worker navigation remains intentionally task-tab-free; runtime, API and Flyway remain unchanged.
- [x] Baseline stays 103 / 544 / 31.


## v27.22.1 — Vacation Planner Frontend Contract Hotfix — completed

- [x] Shift Worker static contract includes `vacation` and the accepted Today widget order.
- [x] Vacation Month/Week/Day static contract follows the real all-day absence composition path.
- [x] Persisted switchable-module count is derived from `DutyLogModules.ALL` instead of a hardcoded value.
- [x] Runtime, API and Flyway remain unchanged; baseline stays 103 / 544 / 31.


## v27.22.0 — Vacation Planner — completed

- [x] Separate absence model; vacation never becomes a shift row.
- [x] Annual allowance and carryover.
- [x] Configurable work-year boundary.
- [x] Calendar-day or Monday-Friday counting.
- [x] 14 / 28 / 35-day presets and custom periods.
- [x] Preview with shift and absence conflicts.
- [x] Overlap and allowance protection.
- [x] Built-in and custom absence types.
- [x] Month / Week / Day / selected-day composition.
- [x] Flyway V40, Web/v1 API, Java/static/Playwright contracts.

Next product stage: **v27.24.0 — Workspace, Layout & Theme Studio**.

## Текущая продуктовая точка — Vacation Planner

Статус: **v27.22.2** стабилизирует workspace-aware browser-навигацию задач после принятого Vacation Planner. Базовый домен отпусков и отсутствий остаётся реализацией v27.22.0 и не смешивается со сменами, рабочими часами или FIFO переработок.

Закрыто:

- отдельные owner-scoped настройки отпуска, типы отсутствий и периоды;
- годовая норма, перенос и настраиваемая граница рабочего года;
- подсчёт календарных дней или Пн–Пт без выдуманного законодательства;
- быстрые периоды 14 / 28 / 35 дней и произвольный диапазон;
- per-day preview со сменами, другими отсутствиями и остатком;
- проверка каждого пересечённого рабочего года с показом наиболее ограниченного баланса;
- стабильные `VACATION_LIMIT_EXCEEDED`, `ABSENCE_OVERLAP` и `ABSENCE_TYPE_IN_USE`;
- встроенные и пользовательские типы отсутствий;
- Month / Week / Day / selected-day проекция без записи отпуска в смены;
- Flyway V40, API v1, OpenAPI, Java/static/Playwright contracts;
- baseline 103 Java test classes / 544 tests / 31 Playwright scenario.

Следующий этап: **v27.24.0 — Workspace, Layout & Theme Studio**. Read-only импорт внешних `.ics`-источников остаётся отдельным последующим этапом интеграций.

## Этап 2 — нормальная API-архитектура

Статус: основа сделана в v10.

Сделано:

- бизнес-логика вынесена из контроллеров в сервисы;
- добавлен единый формат ошибок;
- добавлен endpoint диапазона дат: `GET /api/calendar?from=...&to=...`;
- добавлен endpoint баланса переработки: `GET /api/overtime/balance?from=...&to=...`;
- добавлен endpoint журнала переработок.

Осталось:

- разнести DTO из одного `Dtos.java` по отдельным файлам.

OpenAPI v1 уже поддерживается и расширяется вместе с доменными релизами.

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
- `v27.19.4` — Ghost Button Transition E2E Stabilization Hotfix.
- `v27.19.0` — Tasks & Inbox Next, including independent planned task intervals (`start → end`), duration, deadlines and timeline cards.
- `v27.20.0` — Notes & Important Events Next, including all-day/timed/multi-day events, place, description, reminders and read-first event cards.
