# DutyLog: Time & Overtime

DutyLog — приложение для учёта смен, переработок, отгулов, задач, важных дат и напоминаний. Оно объединяет календарь смен, журнал переработок, задачи дня, Markdown-заметки, Telegram-бота и PWA-интерфейс в одном Spring Boot backend.

## Возможности

- Календарь смен с типами `Дневная`, `Ночная`, `Выходной` и пользовательскими сменами.
- Модульный режим: пользователь может включать и выключать Notes, Tasks, Overtime, Important dates, Notifications, Telegram и Scenarios без удаления данных.
- Первый запуск: новый пользователь выбирает нужные модули через спокойный onboarding, а не сразу попадает в перегруженный интерфейс.
- Автозаполнение графиков: 2/2, день/ночь/48, 5/2, день/72, ночь/72.
- Markdown-заметки для каждого дня с полноэкранным редактором, живым превью и ZIP-экспортом всей базы для Obsidian/резервной копии.
- Персонализация: светлая/тёмная/системная тема, акцентный цвет и emoji-маркеры дней без хранения картинок.
- Задачи дня с категориями, приоритетами, сроками и напоминаниями.
- Важные даты: разовые, ежемесячные и ежегодные события.
- Журнал переработок и отгулов с FIFO-списанием старых остатков.
- Расчёт переработки по интервалу: начало, конец, обед и вычитаемый план.
- Быстрые сценарии для типовых переработок.
- Уведомления в браузере и Telegram.
- Telegram-бот для просмотра данных и быстрых действий.
- Профиль пользователя, смена пароля и управление мобильными сессиями.
- Служебная диагностика состояния приложения, сервера, базы данных и Telegram-интеграции в отдельном профиле администратора.
- Скрипты резервного копирования и восстановления PostgreSQL.
- Production-ready compose, Caddy reverse proxy example, healthchecks and launch runbook.

## Стек

- Java 17
- Spring Boot 3.3.5
- Spring Web, Data JPA, Security, Validation
- PostgreSQL + Flyway для production
- H2 для локальной разработки
- HTML/CSS/JavaScript без frontend-фреймворка
- PWA: manifest, service worker, installable web shell
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

Для локального Docker-запуска:

```bash
cp .env.example .env
docker compose up -d --build
```

Для VPS/production-запуска используйте отдельный compose-файл с Caddy и без публичного порта приложения:

```bash
cp .env.production.example .env
cp deploy/caddy/Caddyfile.example deploy/caddy/Caddyfile
docker compose -f docker-compose.prod.yml up -d --build
```

Безопасная остановка:

```bash
docker compose down
```

Команда ниже удаляет Docker volumes и может стереть базу данных:

```bash
docker compose down -v
```

## Резервные копии

Создать backup PostgreSQL:

```bash
./deploy/scripts/backup-postgres.sh
```

Посмотреть список backup-файлов:

```bash
./deploy/scripts/list-backups.sh
```

Восстановить базу из backup:

```bash
./deploy/scripts/restore-postgres.sh backups/dutylog-YYYY-MM-DD_HH-MM-SS.dump
```

Подробно: [`docs/BACKUP.md`](docs/BACKUP.md).

## Production-профиль

В production используется PostgreSQL и Flyway-миграции. Hibernate работает в режиме валидации схемы, поэтому изменения БД должны оформляться новыми файлами миграций в `src/main/resources/db/migration`.

Для боевого запуска подготовлены:

- `docker-compose.prod.yml` — PostgreSQL, приложение и Caddy;
- `.env.production.example` — шаблон production-переменных;
- `deploy/caddy/Caddyfile.example` — HTTPS reverse proxy;
- `docs/PRODUCTION_RUNBOOK.md` — первый запуск, обновление, откат и emergency backup;
- `docs/SECURITY_CHECKLIST.md` — чеклист безопасности.

Пароли в compose настроены fail-hard: пустые production-пароли не должны приводить к тихому запуску небезопасной конфигурации.

Перед первым запуском на VPS можно прогнать production preflight:

```bash
./deploy/scripts/check-production-env.sh
```

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
- `/api/mobile/**` работает отдельной stateless security chain и принимает только `Authorization: Bearer <accessToken>`; browser `JSESSIONID` для неё не подходит.
- Production-регистрация по умолчанию закрыта, а login/registration/mobile-login ограничены app-level rate limiter.
- Структурированные `SECURITY_AUDIT` события не содержат пароли, токены, Telegram-коды или заметки.
- Notes export owner-scoped, bounded, streamed and marked `Cache-Control: no-store`.
- Refresh tokens хранятся только в виде SHA-256-хэшей.
- Пароли пользователей хранятся через BCrypt.
- Диагностический endpoint не раскрывает секреты: Telegram token, пароли и URL базы данных не отдаются.

## Документация

- [`CHANGES.md`](CHANGES.md) — история версий.
- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — архитектура приложения.
- [`docs/API.md`](docs/API.md) — HTTP API.
- [`docs/GIT_WORKFLOW.md`](docs/GIT_WORKFLOW.md) — Git-история, теги и откаты.
- [`docs/BACKUP.md`](docs/BACKUP.md) — резервные копии и восстановление PostgreSQL.
- [`docs/DEPLOY.md`](docs/DEPLOY.md) — запуск на VPS через Docker Compose.
- [`docs/PRODUCTION_RUNBOOK.md`](docs/PRODUCTION_RUNBOOK.md) — эксплуатация, обновление и откат на VPS.
- [`docs/PRODUCTION_LAUNCH.md`](docs/PRODUCTION_LAUNCH.md) — короткий сценарий первого запуска на VPS.
- [`docs/SECURITY_CHECKLIST.md`](docs/SECURITY_CHECKLIST.md) — чеклист безопасности перед публикацией.
- [`docs/SECURITY_REVIEW.md`](docs/SECURITY_REVIEW.md) — обзор security hardening текущей стабилизации.
- [`docs/SECURITY_CONSOLIDATION.md`](docs/SECURITY_CONSOLIDATION.md) — сводка закрытых security-находок v27.0-rc4.
- [`docs/NOTES_EXPORT.md`](docs/NOTES_EXPORT.md) — формат и ограничения ZIP-экспорта заметок.
- [`docs/SUPPLY_CHAIN.md`](docs/SUPPLY_CHAIN.md) — Dependabot и правила обновления зависимостей/образов.
- [`docs/ADMIN_BOOTSTRAP.md`](docs/ADMIN_BOOTSTRAP.md) — безопасное создание стартового администратора через env.
- [`docs/REGISTRATION_SETTINGS.md`](docs/REGISTRATION_SETTINGS.md) — управление публичной регистрацией из админки.
- [`docs/USER_ROLES.md`](docs/USER_ROLES.md) — пользователи, роли ADMIN/USER и будущий задел FREE/PAID/VIP.
- [`docs/PERSONALIZATION.md`](docs/PERSONALIZATION.md) — темы, акцентный цвет и Unicode emoji-маркеры дней.
- [`docs/VPS_CHECKLIST.md`](docs/VPS_CHECKLIST.md) — чеклист боевого запуска.
- [`docs/ANDROID_API_PLAN.md`](docs/ANDROID_API_PLAN.md) — мобильный API.
- [`docs/ROADMAP.md`](docs/ROADMAP.md) — идеи развития.
- [`docs/PRODUCT_COPY.md`](docs/PRODUCT_COPY.md) — стиль пользовательских текстов.
- [`docs/OFFLINE_MODE.md`](docs/OFFLINE_MODE.md) — offline-режим, локальный снимок и очередь синхронизации.
- [`docs/RELEASE_CHECKLIST.md`](docs/RELEASE_CHECKLIST.md) — ручная проверка web/PWA-монолита перед релизом и VPS-деплоем.
- [`docs/RELEASE_CANDIDATE.md`](docs/RELEASE_CANDIDATE.md) — что проверено в v27.0-rc4 и как принимать RC.
- [`docs/USER_GUIDE.md`](docs/USER_GUIDE.md) — короткая пользовательская инструкция.
- [`docs/PRODUCTION_DEPLOY.md`](docs/PRODUCTION_DEPLOY.md) — пошаговый production deployment.
- [`docs/BACKUP_RESTORE.md`](docs/BACKUP_RESTORE.md) — резервное копирование и восстановление.
- [`docs/RELEASE_HARDENING.md`](docs/RELEASE_HARDENING.md) — фаза стабилизации, release gate и правила freeze.
- [`docs/CODE_CLEANUP.md`](docs/CODE_CLEANUP.md) — правила безопасной чистки кода во время стабилизации.
- [`docs/UX_RELEASE_POLISH.md`](docs/UX_RELEASE_POLISH.md) — UX-полировка релизной стабилизации.
- [`docs/UX_COMPACT_HOTFIX.md`](docs/UX_COMPACT_HOTFIX.md) — компактная правка экрана модулей и панели дня после UX-polish.
- [`docs/UX_CONSOLE_HOTFIX.md`](docs/UX_CONSOLE_HOTFIX.md) — скрытие технических деталей от обычных пользователей и чистка console-noise.
- [`docs/TEST_CONFIG_HOTFIX.md`](docs/TEST_CONFIG_HOTFIX.md) — правка `.properties`, тестовых ожиданий и cascade-зависимостей модулей.
- [`docs/ONBOARDING_TODAY_HOTFIX.md`](docs/ONBOARDING_TODAY_HOTFIX.md) — выделение выбранного onboarding-набора и более заметный текущий день.
- [`docs/DAY_HINT_DISMISS_HOTFIX.md`](docs/DAY_HINT_DISMISS_HOTFIX.md) — различение сегодняшнего и выбранного дня, закрываемая подсказка скрытых блоков.
- [`docs/UI_ALIGNMENT_TEST_HOTFIX.md`](docs/UI_ALIGNMENT_TEST_HOTFIX.md) — стабильное выравнивание правых controls в настройках и правка компиляции тестов.
- [`docs/NOTIFICATION_ADMIN_NAV_HOTFIX.md`](docs/NOTIFICATION_ADMIN_NAV_HOTFIX.md) — выравнивание уведомлений и навигация в админке.

## Текущая версия

`v27.0-rc4 — Security consolidation`

DutyLog находится в release-candidate фазе. Новые крупные функции заморожены. RC4 объединяет экспорт Markdown-заметок и закрытие замечаний двух независимых security-review.

Что вошло:

- версия `27.0-rc4` синхронизирована во frontend, backend, service worker, smoke-test и документации;
- notes export owner-scoped, streamed, bounded, YAML-safe and `no-store`;
- browser session и bearer-only mobile API разделены на две security chain;
- IDOR-тесты проверяют точный `404` для чужих ресурсов;
- production registration starts closed; auth endpoints are rate-limited inside the application;
- добавлены структурированные security-события и ротация логов;
- login JavaScript вынесен в отдельный файл, CSP больше не содержит `script-src 'unsafe-inline'`;
- Caddy/nginx/Spring policies aligned; application container runs as non-root;
- Dependabot следит за Maven, GitHub Actions и Docker;
- release-check закрепляет новые security/export-инварианты.

Критерии принятия:

- зелёный `mvn test`;
- зелёный `bash deploy/scripts/release-check.sh`;
- production preflight и smoke-test проходят на VPS;
- ручная проверка notes export, mobile auth boundary, registration defaults, backup/restore и PWA/offline не выявляет блокеров.

Следующий шаг: `v27.0` stable после периода спокойного использования либо `v27.0-rc5` только для блокирующих исправлений.

## Служебный профиль администратора

Диагностика не показывается в обычных пользовательских настройках. Администратор видит в шапке кнопку `Система`, где доступны пользователи и роли, версия интерфейса и сервера, состояние БД, Service Worker, Telegram-интеграция, переключатель публичной регистрации и безопасный отчёт без секретов.

Публичная регистрация больше не выдаёт `ADMIN` автоматически. На новой production-установке первый администратор задаётся в `.env`:

```env
DUTYLOG_ADMIN_USERNAME=your_admin_login
DUTYLOG_ADMIN_PASSWORD=long_random_password_at_least_20_chars
```

При старте backend создаёт этого пользователя, если его ещё нет, или повышает существующего пользователя с таким логином до `ADMIN`. После первого создания пароль можно менять в приложении; обычный рестарт не возвращает старый env-пароль. Для аварийного восстановления доступен `DUTYLOG_ADMIN_FORCE_PASSWORD_RESET=true`. Все остальные регистрации получают только `USER`.

### Публичная регистрация

Публичная регистрация обычных пользователей управляется из админского раздела `Система` → `Публичная регистрация`. Когда переключатель выключен, страница входа скрывает вкладку регистрации, а backend возвращает `403` даже на прямой запрос `POST /api/auth/register`.

Администраторы через публичную регистрацию не создаются. Дополнительных админов можно назначить только из закрытого раздела `Система` → `Пользователи и роли`.



## Module contracts

Since v25.3 the module registry has explicit developer contracts. See `docs/MODULE_CONTRACTS.md`.


CI permission stabilization in v27.0-rc4:

- GitHub Actions runs release checks through `bash ./deploy/scripts/release-check.sh`.
- CI no longer fails when executable bits are lost on Windows/archive checkouts.
