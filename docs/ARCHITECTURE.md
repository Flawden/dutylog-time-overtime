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

В production схема управляется Flyway. Новые изменения БД добавляются только новыми миграциями:

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
