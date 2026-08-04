# DutyLog architecture

## Active Vue frontend transition — v27.34.4 contract stabilization

DutyLog remains one modular monolith, one repository, one release version and one production application image/container. PostgreSQL remains a separate container. The frontend migration from ordered vanilla JavaScript to **Vue 3 + TypeScript + Vite** is active. v27.33.0 introduced the production foundation; v27.34.0 moved the visible application shell and shared design-system infrastructure to Vue; v27.34.1–v27.34.3 stabilized strict typing, browser-safe output and E2E navigation ownership. v27.34.4 keeps the same boundary while separating non-persistent overtime draft calculation from strict writes and completing active-route presentation for secondary Vue navigation. New product features remain paused until complete Vue parity.

The migration is incremental, not a big-bang rewrite: the released foundation and CI boundary → design system and app shell → domain-by-domain migration → deletion of the legacy numbered scripts and bridge. Production Vite is not a server: its build output is packaged into the Spring Boot JAR/image and served same-origin with the existing session and CSRF model.

```text
frontend/ (Vue 3 + TypeScript + Vite)
        ↓ build
Spring Boot static resources + API
        ↓
one DutyLog JAR / one dutylog-app image
        ↓
PostgreSQL container
```


## Unified time and compensation ledger (v27.26.0)

DutyLog separates three layers: planned schedule, factual state and compensation source. Existing overtime credits/usages/allocations are the canonical minute ledger. `OVERTIME_BANK` absences own one source-linked usage and cannot be independently mutated from the manual ledger. `TimeCompensationService` provides an owner-scoped monthly read model for Payroll Foundation; it contains time semantics only and intentionally no money formulas.

Flyway V43 migrates the V42 standalone balance into an opening credit, links historical time-off absences and leaves planned `day_entries` unchanged. The deprecated settings field remains only for wire compatibility and is not writable authority.


DutyLog построен как монолитное Spring Boot приложение. На текущем этапе это осознанный выбор: календарь, переработки, задачи, уведомления и Telegram используют общую модель пользователя и общую базу данных, поэтому отдельные сервисы добавили бы лишнюю сложность без практической пользы.

## Общая схема

```text
Browser / PWA
    |
    | JSESSIONID + CSRF
    v
Spring Boot application
    |
    | JPA / Flyway
    v
PostgreSQL

Telegram Bot API
    ^
    | long polling / sendMessage
    |
telegram/ module inside Spring Boot
```

## Слои приложения

```text
web/       HTTP API, проверка входных DTO, перевод ошибок в ответы
service/   бизнес-правила и транзакции
repo/      доступ к данным через Spring Data JPA
model/     JPA-сущности и доменные enum
telegram/  Telegram-команды, привязка аккаунта, доставка уведомлений
config/    Security, Bearer auth, request diagnostics
frontend/ Vue 3 + TypeScript + Vite sources during the approved migration
static/    built frontend assets packaged into the Spring Boot application
```

Контроллеры не должны содержать бизнес-логику. Их задача — принять запрос, определить текущего пользователя, вызвать сервис и вернуть DTO.

Сервисы содержат правила предметной области: расчёт календаря, FIFO-списание переработок, проверку пересечений интервалов, расчёт напоминаний, смену пароля и отзыв сессий.

Repositories не должны использоваться напрямую из контроллеров, если операция содержит бизнес-правила.

## Основные модули

### Calendar

Файлы:

```text
CalendarController
CalendarService
DayController
DayEntryService
DayEntry
ShiftType
```

Отвечает за календарную сетку, выбранный день, смену, заметку и сводные данные по периоду.

### Overtime

Файлы:

```text
OvertimeController
OvertimeService
OvertimeCredit
OvertimeUsage
OvertimeAllocation
```

Отвечает за начисления переработки, списания отгулов и FIFO-распределение. Это критичный модуль: изменения в `OvertimeService` должны сопровождаться тестами.

### Tasks

Файлы:

```text
TaskController
TaskService
DayTask
TaskPriority
```

Отвечает за задачи дня, общий список задач, статусы, сроки, приоритеты и напоминания.

### Important days

Файлы:

```text
ImportantDayController
ImportantDayService
ImportantDay
RepeatMode
```

Отвечает за разовые, ежемесячные и ежегодные события. Повторяющиеся события рассчитываются при построении календарного диапазона.

### Time Foundation

Файлы:

```text
UserTimeService
WorkIntervalService
TimeContextController
AppUser.workTimezone
AppUser.displayTimezone
```

`UserTimeService` — единственная backend-граница для текущего `Instant`, work/display-проекций и преобразования рабочего wall-clock времени в абсолютный момент. Рабочий часовой пояс определяет календарный смысл, display timezone не участвует в расчётах и не переписывает данные.

Плавающие даты (`LocalDate`) хранятся без преобразования. Абсолютные моменты (`Instant`/`TIMESTAMPTZ`) сравниваются на глобальной шкале времени. `WorkIntervalService` измеряет длительности между абсолютными границами, поэтому переход через полночь и DST не сводится к простому вычитанию локальных часов.

Правило DST детерминировано: разрыв сдвигается вперёд на длительность перехода, пересечение использует более ранний offset.

С v27.8.0 `DayDto.shiftInterval` проецирует датированную смену одновременно в work/display zones, сохраняя одни и те же абсолютные границы. В v27.8.1 профиль загружается до первого календарного bundle, а изменение зон принудительно инвалидирует локальный snapshot и перечитывает проекции с сервера. Новые рассчитанные `OvertimeCredit` хранят `startAtInstant`, `endAtInstant` и `sourceTimezone`; старые local-only строки не backfill'ятся без достоверной исходной зоны.

### Notifications

Файлы:

```text
NotificationController
NotificationService
NotificationSettings
```

Рассчитывает напоминания для смен, задач, важных дней и вечернего дайджеста. Этот слой используется web-интерфейсом и Telegram-доставкой.

### Telegram

Файлы:

```text
TelegramController
TelegramBotService
TelegramCommandService
TelegramLinkService
TelegramNotificationService
TelegramLink
TelegramLinkCode
TelegramNotificationDelivery
```

Telegram живёт внутри основного backend. Пользователь привязывает аккаунт через одноразовый код. Команды используют существующие сервисы приложения, а доставка уведомлений не дублирует правила — она берёт рассчитанные напоминания из `NotificationService`.

### Profile and auth

Файлы:

```text
AuthController
ProfileController
MobileAuthController
MobileController
CurrentUserService
MobileAuthService
AppUser
MobileAuthToken
```

Web использует cookie-сессию и CSRF. Mobile API использует Bearer tokens. Профиль управляет именем, днём рождения, сменой пароля и мобильными сессиями.

### Diagnostics

Файлы:

```text
SystemController
RequestDiagnosticsFilter
```

`GET /api/admin/status` отдаёт безопасный статус системы без секретов и доступен только пользователю с ролью `ADMIN`. `RequestDiagnosticsFilter` добавляет `X-Request-Id` и пишет краткие request-логи.

## База данных

В production схема управляется Flyway; текущая последовательность заканчивается на V40 Vacation Planner; предыдущая доменная миграция — V39 Schedule Templates & Calendar Layers. Новые изменения БД добавляются только новыми миграциями:

```text
src/main/resources/db/migration/V14__example.sql
```

В production не используется `ddl-auto=update`; схема валидируется при запуске.

## Границы web и mobile API

- Web API использует cookie session + CSRF.
- Mobile API использует Bearer auth и исключён из CSRF.
- Web-интерфейс не должен вызывать `/api/mobile/**` для действий обычной страницы. Для web нужны отдельные безопасные endpoints, например `/api/profile/sessions`.

## Решение по Telegram

Telegram пока не вынесен в отдельный сервис. Причины:

- бот использует те же доменные сервисы;
- нет отдельной авторизации между сервисами;
- проще локальный запуск и VPS-деплой;
- меньше инфраструктурных точек отказа.

Вынесение Telegram в отдельный сервис имеет смысл, когда появится необходимость независимого масштабирования или отдельного деплоя.

## Правила изменений

1. Не добавлять бизнес-логику в контроллеры.
2. Не обращаться к repository из web-слоя для операций с правилами.
3. Все изменения БД оформлять Flyway-миграциями.
4. Изменения в переработках покрывать тестами `OvertimeServiceTest`.
5. Не хранить секреты в Git: `.env`, токены, реальные backup-файлы и дампы БД не коммитятся.
6. Пользовательский интерфейс не должен показывать внутренние формулировки вроде `backend`, `frontend`, `VPS`, `CSRF`, если это не экран диагностики.


## Backup and restore

DutyLog does not store backups inside the application database. Backup and restore are operational scripts in `deploy/scripts`:

```text
deploy/scripts/backup-postgres.sh
deploy/scripts/restore-postgres.sh
deploy/scripts/list-backups.sh
```

The scripts work with the Docker Compose PostgreSQL service and create PostgreSQL custom-format dumps in `backups/`. Backup files and `.env` are intentionally excluded from Git.

Daily backup examples for systemd live in `deploy/systemd`.

## Production deployment

The active VPS deployment uses the host-wide system nginx and separate Compose projects:

```text
Internet
   |
   v
system nginx :80/:443
   |-- 127.0.0.1:18082 -> DutyLog staging app
   `-- 127.0.0.1:18083 -> DutyLog production app
                                  |
                                  v
                         private PostgreSQL :5432
```

Files:

```text
deploy/compose/docker-compose.deploy.yml
deploy/env/.env.staging.example
deploy/env/.env.production.cicd.example
deploy/nginx/dutylog-staging.conf.example
deploy/nginx/dutylog-production.conf.example
deploy/scripts/local-smoke-test.sh
deploy/scripts/smoke-test.sh
```

The app is published only on `127.0.0.1`; deployment preflight rejects public bind addresses. PostgreSQL has no host port and sits on an internal Docker network. A separate outbound network gives only the app optional Internet access for Telegram. Caddy files remain legacy/alternative examples and are not started by active CI/CD.

The deployment proves container health, a full loopback smoke test and then the public HTTPS path through nginx. Admin diagnostics remain behind `/api/admin/status` and require an administrator account.


## Modular monolith layer

Since v25.0 DutyLog has a user-module layer. Modules are registered in backend code and stored per user in `user_module_settings`. This is not a microservice split: the app remains one Spring Boot monolith, but large features such as notes, tasks, overtime, important dates, notifications, Telegram and scenarios have explicit enable/disable boundaries. Disabled modules are hidden in the UI and guarded by backend APIs with `MODULE_DISABLED:<key>`. Data is never deleted by disabling a module. See `docs/MODULES.md`.


## Overtime Interval Engine (v27.9.0)

`OvertimeService` treats integer minutes as the ledger authority and rebuilds all owner allocations deterministically after usage mutations. `OvertimeCredit` stores the absolute credited interval separately from the raw entered interval; `OvertimeAllocation` stores the exact consumed slice. Decimal hours remain compatibility/reporting projections.

The user-facing time model is one canonical IANA timezone. Historical work/display fields remain aliases so older clients keep working. Absolute overtime is reprojected without changing UTC identity; floating dates are not shifted.

Legacy local-only credits cross an explicit migration boundary. The user chooses the source timezone, previews the conversion and confirms selected rows. Rows without precise local start/end stay quantity-only. Reconstructed allocations carry a marker instead of claiming original certainty.

## Schedule templates and calendar layers

`ScheduleTemplate` stores reusable rules; applying a template writes ordinary `DayEntry` shifts. `CalendarLayer` stores only read-only projection metadata and derives occurrences at query time. Timed occurrences resolve in the layer IANA timezone and project into the user's display timezone. No companion occurrence is persisted as an owner day.


## V41 External Calendar Sync

`calendar_feed_subscriptions` is an owner-scoped credential table for a read-only RFC 5545 feed. It stores one SHA-256 token digest and a short hint per user; raw bearer secrets and complete subscription URLs are never persisted. Calendar content remains projected from authoritative shifts, tasks, important events and absence domains.

## V42 Absence & Time-Off Overhaul

V42 extends the existing vacation bounded context into a factual absence layer without mutating scheduled shifts.

- `vacation_settings` owns the independent time-off minute bank and default full-day duration.
- `absence_types.balance_policy` selects `VACATION_DAYS`, `TIME_OFF_HOURS` or `NONE`.
- `absence_types.full_day_replaces_shift` controls presentation only; the shift row remains the plan source.
- `absence_periods.coverage` distinguishes `FULL_DAY` from one-day `PARTIAL` intervals.
- `charged_minutes` is persisted so historical balance usage does not depend on later shift-template changes.
- Calendar projections compose planned shifts and factual absences in Month, Week, Day and `.ics` output.

The migration is additive. It does not alter `day_entries`, rewrite shift occurrences or remove existing vacation data.
