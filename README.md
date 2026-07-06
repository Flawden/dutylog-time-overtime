# DutyLog: Time & Overtime

DutyLog — приложение для учёта смен, переработок, отгулов, задач, важных дат и напоминаний. Оно объединяет календарь смен, журнал переработок, задачи дня, Markdown-заметки, Telegram-бота и PWA-интерфейс в одном Spring Boot backend.

## Возможности

- Календарь смен с типами `Дневная`, `Ночная`, `Выходной` и пользовательскими сменами.
- Автозаполнение графиков: 2/2, день/ночь/48, 5/2, день/72, ночь/72.
- Markdown-заметки для каждого дня с полноэкранным редактором и живым превью.
- Задачи дня с категориями, приоритетами, сроками и напоминаниями.
- Важные даты: разовые, ежемесячные и ежегодные события.
- Журнал переработок и отгулов с FIFO-списанием старых остатков.
- Расчёт переработки по интервалу: начало, конец, обед и вычитаемый план.
- Быстрые сценарии для типовых переработок.
- Уведомления в браузере и Telegram.
- Telegram-бот для просмотра данных и быстрых действий.
- Профиль пользователя, смена пароля и управление мобильными сессиями.
- Служебная диагностика состояния приложения, сервера, базы данных и Telegram-интеграции в отдельном профиле администратора.

## Стек

- Java 17
- Spring Boot 3.3.5
- Spring Web, Data JPA, Security, Validation
- PostgreSQL + Flyway для production
- H2 для локальной разработки
- HTML/CSS/JavaScript без frontend-фреймворка
- PWA: manifest, service worker, installable shell
- Docker Compose
- Telegram Bot API через long polling

## Архитектура

Основной backend — монолит Spring Boot с чётким разделением по слоям:

```text
web/       HTTP-контроллеры и API
service/   бизнес-логика
model/     JPA-сущности
repo/      Spring Data repositories
telegram/  Telegram-бот, команды, привязка и доставка уведомлений
config/    безопасность, диагностика запросов, Bearer-auth
static/    PWA-интерфейс
```

Подробная схема модулей и границ ответственности описана в [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

## Быстрый локальный запуск

Нужны JDK 17+ и Maven.

```bash
mvn spring-boot:run
```

После запуска приложение доступно по адресу:

```text
http://localhost:8080
```

В dev-режиме используется H2-база в папке `./data`.

## Запуск через Docker Compose

Скопируйте пример переменных окружения:

```bash
cp .env.example .env
```

Заполните значения в `.env`, затем запустите:

```bash
docker compose up -d --build
```

Безопасная остановка:

```bash
docker compose down
```

Команда ниже удаляет Docker volumes и может стереть базу данных:

```bash
docker compose down -v
```

## Production-профиль

В production используется PostgreSQL и Flyway-миграции. Hibernate работает в режиме валидации схемы, поэтому изменения БД должны оформляться новыми файлами миграций в `src/main/resources/db/migration`.

Минимальные переменные окружения:

```env
POSTGRES_DB=dutylog
POSTGRES_USER=dutylog
POSTGRES_PASSWORD=change-me
DUTYLOG_DB_URL=jdbc:postgresql://postgres:5432/dutylog
DUTYLOG_DB_USER=dutylog
DUTYLOG_DB_PASSWORD=change-me
DUTYLOG_ADMIN_USERNAME=admin
DUTYLOG_ADMIN_PASSWORD=change-me
```

Пароли в `docker-compose.yml` настроены fail-hard: пустые production-пароли не должны приводить к тихому запуску небезопасной конфигурации.

## Telegram

Telegram-бот работает внутри основного backend. Для включения задайте:

```env
DUTYLOG_TELEGRAM_ENABLED=true
DUTYLOG_TELEGRAM_BOT_TOKEN=123456:telegram-token
DUTYLOG_TELEGRAM_BOT_USERNAME=your_bot_username
DUTYLOG_TELEGRAM_POLLING_ENABLED=true
DUTYLOG_TELEGRAM_NOTIFICATIONS_ENABLED=true
```

Подключение пользователя выполняется через одноразовый код в профиле DutyLog:

```text
/start DL-123456
```

Команды бота:

```text
/today       что сегодня
/tomorrow    что завтра
/week        ближайшие 7 дней
/tasks       открытые задачи
/task        добавить задачу
/done        закрыть задачу
/ppr         начислить переработку
/timeoff     списать отгул
/balance     остаток переработок
/help        помощь
```

## Безопасность

- Web-интерфейс работает через `JSESSIONID` и CSRF-защиту.
- Изменяющие web-запросы отправляют `X-XSRF-TOKEN`.
- Mobile API использует `Authorization: Bearer <accessToken>`.
- Refresh tokens хранятся только в виде SHA-256-хэшей.
- Пароли пользователей хранятся через BCrypt.
- Диагностический endpoint не раскрывает секреты: Telegram token, пароли и URL базы данных не отдаются.

## Документация

- [`CHANGES.md`](CHANGES.md) — история версий.
- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — архитектура приложения.
- [`docs/API.md`](docs/API.md) — HTTP API.
- [`docs/GIT_WORKFLOW.md`](docs/GIT_WORKFLOW.md) — Git-история, теги и откаты.
- [`docs/ANDROID_API_PLAN.md`](docs/ANDROID_API_PLAN.md) — мобильный API.
- [`docs/ROADMAP.md`](docs/ROADMAP.md) — идеи развития.
- [`docs/PRODUCT_COPY.md`](docs/PRODUCT_COPY.md) — стиль пользовательских текстов.

## Текущая версия

`v20.6 — admin diagnostics profile`

В этой версии интерфейсные тексты приведены к пользовательскому стилю, технические подсказки вынесены из пользовательских сценариев, README стал product-ready, а архитектура описана отдельным документом.


## Служебный профиль администратора

Диагностика не показывается в обычных пользовательских настройках. Администратор видит в шапке кнопку `Система`, где доступны версия интерфейса и сервера, состояние БД, Service Worker, Telegram-интеграция и безопасный отчёт без секретов.

Первый пользователь существующей установки получает роль `ADMIN` миграцией `V14__admin_role_and_diagnostics.sql`. На новой установке первым администратором становится первый зарегистрированный пользователь.
