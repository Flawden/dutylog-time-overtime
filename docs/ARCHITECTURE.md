# DutyLog architecture

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
static/    PWA-интерфейс без отдельного frontend build step
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

В production схема управляется Flyway; текущая последовательность заканчивается на V36 Multiple Daily Notes. Новые изменения БД добавляются только новыми миграциями:

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
