# v27.22.2 — Workspace-Aware Tasks E2E Navigation Hotfix

- Updated four stale Playwright task flows to use the shared workspace-aware `openView()` route.
- Tasks stay outside Shift Worker primary navigation by design; the hotfix does not re-add the tab or alter runtime behavior.
- Module toggling is now asserted through `moduleHidden`, independently from workspace placement through `workspaceHidden`.
- Runtime behavior, API, Flyway V40 and the 103 / 544 / 31 regression baseline remain unchanged.

> Current release: **v27.22.2 — Workspace-Aware Tasks E2E Navigation Hotfix**.

# v27.22.1 — Vacation Planner Frontend Contract Hotfix

- Aligned three stale Java/static contracts with the accepted Vacation Planner runtime.
- Shift Worker now explicitly protects the `vacation` route and actual Today widget order.
- Calendar composition now checks the real all-day absence path instead of an invented CSS class.
- Module persistence derives its expected count from the canonical module registry instead of a hardcoded pre-vacation number.
- Runtime behavior, API, Flyway V40 and the 103 / 544 / 31 regression baseline remain unchanged.

> Current release: **v27.22.2 — Workspace-Aware Tasks E2E Navigation Hotfix**.

# v27.22.0 — Vacation Planner

- Added a separate vacation/absence domain instead of encoding leave as a shift.
- Added annual allowance, carryover, configurable work year and calendar-day / Monday-Friday counting.
- Added 14 / 28 / 35 day presets, conflict-aware preview, hard overlap protection and work-year allowance validation.
- Added owner-scoped absence types, calendar projections and a responsive unified-shell planner.
- Flyway advances to V40; regression baseline advances to 103 Java test classes, 544 `@Test` methods and 31 Playwright scenarios.

> Current release: **v27.22.2 — Workspace-Aware Tasks E2E Navigation Hotfix**.

# v27.21.2 — Schedule Accordion E2E Selector Hotfix

- Added an ID-specific accordion helper for browser scenarios where one product module owns multiple day panels.
- Routed the schedule-template E2E through `#accSched` instead of the ambiguous `[data-day-module="shifts"]` selector.
- Preserved strict locator behavior: duplicate module surfaces still fail unless the scenario names the intended accordion.
- No production runtime, API, database or Flyway changes; the baseline remains 100 Java test classes, 525 `@Test` methods and 30 Playwright scenarios.

> Current release: **v27.22.2 — Workspace-Aware Tasks E2E Navigation Hotfix**.

# DutyLog

Current release: **v27.22.2 — Workspace-Aware Tasks E2E Navigation Hotfix**

DutyLog — приложение для учёта смен, переработок, отгулов, задач, важных событий, заметок и напоминаний. Оно объединяет календарь смен, журнал переработок, задачи дня, Markdown-заметки, Telegram-бота и PWA-интерфейс в одном Spring Boot backend.

## Текущая версия: v27.22.2 — Workspace-Aware Tasks E2E Navigation Hotfix


**v27.22.2** исправляет четыре Playwright-сценария, которые пытались кликнуть скрытую рабочим окружением вкладку задач. Сценарии используют общий `openView()`, а включение модуля проверяется независимо через `moduleHidden`. Production-логика не меняется.

### Предыдущий hotfix: v27.22.1 — Vacation Planner Frontend Contract Hotfix

**v27.22.1** синхронизировал три Java/static контракта с принятым runtime Vacation Planner.

### Базовый продуктовый релиз: v27.22.0 — Vacation Planner

**v27.22.0** добавляет отдельный планировщик отпусков и отсутствий:

- отпуск не является сменой и не меняет рабочие часы, статистику смен или FIFO переработок;
- годовая норма, перенос остатка, начало рабочего года и способ подсчёта настраиваются пользователем;
- доступны шаблоны 14 / 28 / 35 дней и произвольный период;
- preview заранее показывает списываемые дни, пересечения со сменами, другие отсутствия и остаток;
- пересечение отсутствий и превышение лимита блокируются с понятным кодом ошибки; конкурентные записи сериализуются по пользователю, а изменение правил проверяет все сохранённые рабочие годы;
- больничный, отпуск без содержания и пользовательские типы существуют в той же независимой модели;
- календарь показывает отсутствие в месяце, неделе, почасовом дне и панели выбранной даты.

Схема обновлена до **Flyway V40**. Автоматическая база: **103 Java-тестовых класса, 544 `@Test` метода и 31 Playwright browser scenario**.

### Предыдущий hotfix: v27.21.2 — Schedule Accordion E2E Selector Hotfix

**v27.21.2** устраняет последнее падение полного Playwright gate после Schedule Templates & Calendar Layers:

- общий `openDayModule()` остаётся строгим и по-прежнему ловит неоднозначные module-key селекторы;
- новый `openDayModuleById()` открывает ровно один указанный `<details>`;
- сценарий шаблонов графика обращается к `#accSched`, а не к двум секциям модуля `shifts` одновременно;
- `.first()` не используется, поэтому тест не может случайно открыть обычный редактор смен вместо шаблонов.

Production-код и схема не менялись. Flyway остаётся **V39**, автоматическая база — **100 Java-тестовых классов, 525 `@Test` методов и 30 Playwright browser scenarios**.

### Предыдущий hotfix: v27.21.1 — Schedule Templates Frontend Contract Alignment Hotfix

**v27.21.1** синхронизировал четыре статических frontend-контракта с data-layer fresh reload, server-owned preview/apply, реальными async API methods и `tplPreview`.

### Базовый продуктовый релиз: v27.21.0 — Schedule Templates & Calendar Layers

**v27.21.0** отделяет повторяемые правила планирования от фактических записей календаря:

- пользовательские шаблоны графиков поддерживают циклы от 1 до 64 шагов, выравнивание от даты начала или по дням недели и пять неизменяемых встроенных пресетов;
- preview заранее показывает `APPLY / OVERWRITE / SAME / SKIP_CONFLICT`, а безопасное применение по умолчанию не трогает занятые дни;
- явный overwrite меняет только смену и сохраняет остальные данные дня;
- календарные слои отображают чужой или вспомогательный повторяющийся график как read-only проекцию в Month / Week / Day;
- слой хранит имя, цвет, IANA timezone, шаблон, anchor, границы дат, порядок и серверную видимость;
- пользовательскую смену нельзя удалить, пока её использует хотя бы один шаблон графика.

Схема обновлена до **Flyway V39**. Автоматическая база: **100 Java-тестовых классов, 525 `@Test` методов и 30 Playwright browser scenarios**.

### Предыдущий релиз: v27.20.2 — Calendar Day Details E2E Flow Hotfix

**v27.20.2** закрыл последнее падение полного Playwright gate после Notes & Important Events Next: сценарий использует реальную кнопку «Все детали дня» вместо прямого обращения к скрытому Month-only модулю.

### Предыдущий hotfix: v27.20.1 — Important Event Modal & Offline Notes E2E Hotfix

**v27.20.1** закрыл single-modal lifecycle важных событий, mode-aware выбор даты и полный offline→sync контракт существующей заметки.

### Базовый продуктовый релиз: v27.20.0 — Notes & Important Events Next

**Заметки** получили поиск по заголовкам и Markdown-тексту, переход к точному дню/заметке, экспорт и безопасное оффлайн-редактирование существующих записей через синхронизируемую очередь.

**Важные события** получили режимы `IMPORTANT_DATE / EVENT / PERIOD`, all-day/timed семантику, исходную IANA-зону, canonical instants, read-first карточки, полноценный редактор, почасовой календарь и индивидуальные напоминания.

### Предыдущий hotfix: v27.19.4 — Ghost Button Transition E2E Stabilization Hotfix

**v27.19.4** стабилизировал semantic alpha-проверку Ghost/Outline в Chromium без изменения production CSS.

### Предыдущий hotfix: v27.19.3 — Task Deadline Validation E2E Contract Hotfix

**v27.19.3** синхронизировал planned-interval deadline validation с browser-контрактом:

- timed-задача сохраняет точную ошибку «Дедлайн не может быть раньше окончания запланированного интервала.»;
- all-day/date fallback остаётся отдельным контрактом;
- production-валидация, Tasks API и данные не менялись;
- `release-check.sh` защищает planned-interval E2E-сообщение.

### Предыдущий hotfix: v27.19.2 — Frontend Asset Contract Stability Hotfix

**v27.19.2** стабилизировал статические frontend-контракты после cache-busting обновлений:

- Today, UI Core, Calendar Experience и Design System по-прежнему проверяют точные имена ассетов и порядок загрузки;
- тесты больше не зашивают конкретный номер релиза в `?v=...`;
- `release-check.sh` отклоняет новые hardcoded semantic versions в `*FrontendContractTest.java`;
- runtime-ассеты, Service Worker, backend metadata и smoke-проверки строго привязаны к версии релиза;
- API, бизнес-логика задач и схема данных не менялись.

### Предыдущий hotfix: v27.19.1 — Task Board Date Range Compatibility Hotfix

**v27.19.1** сохранил обратную совместимость фильтров доски задач и отделил её от нового планового диапазона:

- `from` / `to` снова фильтруют по дедлайну, а без дедлайна — по дате задачи;
- `scheduledFrom` / `scheduledTo` фильтруют по пересечению запланированного интервала;
- экран задач использует плановый диапазон для полей дат и пресета «этот месяц»;
- старые Web/API/mobile-вызовы не меняют смысл после обновления;
- overnight-интервалы попадают в каждый пересекаемый плановый день.

### Базовый продуктовый релиз: v27.19.0 — Tasks & Inbox Next


**v27.19.0** превращает задачи из списка сроков в полноценный план дня:

- запланированное время отделено от дедлайна и напоминания;
- доступны весь день, точечная задача, точные интервалы и быстрые длительности 15/30/45/60/90/120 минут;
- интервалы сохраняют абсолютные instants и исходный IANA timezone, а all-day задачи остаются плавающими датами;
- ночная задача появляется на каждом покрытом дне и корректно делится на почасовом календаре;
- read-first карточка показывает план крупно, отдельно — проект, дедлайн, напоминание и исходную проекцию;
- проекты участвуют в metadata, подсказках, поиске, chips и фильтрах доски;
- Inbox получил поиск по локальной очереди и серверным записям;
- мобильный редактор получил безопасную bottom-sheet компоновку и крупные duration presets.

Схема обновлена до **Flyway V37**. Автоматическая база: **97 Java-тестовых классов, 507 `@Test` методов и 28 Playwright browser scenarios**.

Предыдущие продуктовые релизы:

- **v27.18.3 — UI Settings & Button Variants Quality Hotfix** — исправил Theme palette reset и развёл Ghost/Outline;
- **v27.18.2 — Overtime Snapshot Sync & Timezone E2E Stabilization Hotfix** — синхронизировал usage snapshot и стабилизировал выбор дня;
- **v27.18.1 — Overtime Next E2E Contract Hotfix** — выровнял responsive delete selectors и дневные/месячные chart keys;
- **v27.18.0 — Overtime Next** — добавил balance-first сводку, периоды, тренд, FIFO-очередь, desktop-таблицу и мобильные карточки;
- **v27.17.6 — Classic Sunset** — удалил второй пользовательский shell и оставил единый UI Core;
- **v27.17.5 — UI Core E2E Accordion Hotfix** — восстановил state-aware browser gate;
- **v27.17.4 — UI Core & Workspace Foundation** — добавил UI Core v1, workspaces, layouts, независимые темы и палитры;
- **v27.17.3 — Java Contract Build Gate Hotfix** — вернул зелёный `testCompile` и добавил быстрый `javac`-gate;
- **v27.17.2 — Calendar Timeline Readability Hotfix** — исправил читаемость коротких timed-events на desktop;
- **v27.17.1 — Calendar & Notes Quality Hotfix** — исправил container-aware заметки, all-day rail и минутную точность срока;
- **v27.17.0 — Calendar Mobile Experience** — добавил Month / Week / Day и почасовой день;
- **v27.16.3 — Time Settings Transaction Hotfix** — сериализовал ручное и автоматическое применение времени;
- **v27.16.2 — Next Route & Time Settings E2E Hotfix** — выровнял E2E с Today-first навигацией;
- **v27.16.1 — Today Runtime & Repository Truth Hotfix** — устранил `openQuickActions` load-order crash;
- **v27.16.0 — Today Dashboard** — ежедневный рабочий экран;
- **v27.15.0 — Design System & Mobile Shell Foundation** — заложил дизайн-систему и переходный Classic fallback;
- **v27.14.2 — Calendar Notes Persistence E2E Hotfix** — закрепил новый Notes CRUD в календарной регрессии;
- **v27.14.1 — Mobile Notes Tombstone Hotfix** — сохранил versioned tombstone Android API v1;
- **v27.14.0 — Multiple Daily Notes** — добавил независимые заметки, pin/reorder/delete и offline snapshot.

## История временного фундамента

- v27.7.0 — Time Foundation
- v27.7.1 — Task & Ledger Layout Hotfix
- v27.8.0 — Zoned Work Intervals
- v27.8.1 — Timezone Projection Refresh Hotfix
- v27.9.2 — Overtime Ledger Integrity Hotfix
- v27.9.3 — Overtime Preflight Integrity Hotfix
- v27.9.4 — Overtime Split Projection Contract Hotfix
- v27.9.0 — Overtime Interval Engine
- v27.11.0 — Shift Occurrences & Calendar Projection
- v27.11.3 — Shift Template & Reminder Timezone Hotfix
- v27.11.4 — Task Deadline & Reminder Timezone Hotfix
- v27.12.0 — Zoned Daily Projection Engine
- v27.12.1 — Midnight Projection Contract Hotfix
- v27.13.0 — Temporal Consistency & Legacy Cleanup
- v27.10.0 — Task Details
- v27.11.1 — CI & Contract Hotfix
- v27.11.2 — E2E Stability Hotfix

## Возможности

- Календарь смен с неизменными абсолютными экземплярами, timezone-проекцией, переносом на соседний день и типами `Дневная`, `Ночная`, `Выходной`.
- Модульный режим: пользователь может включать и выключать Notes, Tasks, Overtime, Important dates, Notifications, Telegram и Scenarios без удаления данных.
- Первый запуск: новый пользователь выбирает нужные модули через спокойный onboarding, а не сразу попадает в перегруженный интерфейс.
- Шаблоны графиков: пять встроенных пресетов, пользовательские циклы до 64 шагов, preview, безопасное применение и явное перезаписывание конфликтов.
- Календарные слои: read-only графики других людей или вспомогательные расписания с цветом, IANA timezone, границами и общей серверной видимостью.
- Несколько независимых Markdown-заметок на каждый день с названиями, закреплением, сортировкой, полноэкранным редактором, живым превью и ZIP-экспортом для Obsidian/резервной копии.
- Единый DutyLog UI Core: адаптивная фирменная шапка, нижняя мобильная навигация, workspaces, layouts, независимые темы и палитры.
- Персонализация: светлая/тёмная/системная тема, акцентный цвет и emoji-маркеры дней без хранения картинок.
- Задачи дня с all-day/point/interval-планированием, точной длительностью, проектами, независимыми дедлайнами и напоминаниями, read-first деталями, категориями, тегами, приоритетами и одноуровневыми подзадачами.
- Универсальный быстрый ввод: запись во «Входящие», заготовка задачи, дополнение заметки на сегодня или форма важного дня.
- Компактный сворачиваемый лоток «Входящие» с offline-очередью и преобразованием записи в задачу.
- Важные даты: разовые, ежемесячные и ежегодные события.
- Журнал переработок и отгулов с поминутным FIFO, точными исходными интервалами и provenance каждого списания.
- Расчёт переработки по интервалу: начало, конец, обед и вычитаемый план; старые local-only записи можно безопасно привязать к IANA-зоне через мастер миграции.
- Быстрые сценарии для типовых переработок.
- Уведомления в браузере и Telegram.
- Telegram-бот с видимым меню команд, постоянной клавиатурой быстрых действий и timezone-aware сводками.
- Профиль пользователя, смена пароля и управление мобильными сессиями.
- Версионированный Android API v1 с Bearer-токенами, OpenAPI, idempotency keys и optimistic conflict detection.
- Служебная диагностика состояния приложения, сервера, базы данных и Telegram-интеграции в отдельном профиле администратора.
- Скрипты резервного копирования и восстановления PostgreSQL.
- Staging/production CI/CD с immutable GHCR images, проверенными backup, health/smoke gates и application rollback.

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

Для VPS используется CI/CD runtime за уже установленным системным Nginx. Приложение публикуется только на loopback:

```text
stage.yaruga-trophy.ru   -> nginx -> 127.0.0.1:18082
dutylog.yaruga-trophy.ru -> nginx -> 127.0.0.1:18083
```

Первичная настройка описана в [`docs/HOST_NGINX_DEPLOYMENT_V27.2.30.md`](docs/HOST_NGINX_DEPLOYMENT_V27.2.30.md). Старый `docker-compose.prod.yml` с Caddy оставлен только как legacy/manual вариант и не используется активными workflow.

Безопасная остановка:

```bash
docker compose down
```

Команда ниже удаляет Docker volumes и может стереть базу данных:

```bash
docker compose down -v
```

## Резервные копии

Создать и проверить backup PostgreSQL:

```bash
DUTYLOG_ENV_FILE=.env bash deploy/scripts/backup-postgres.sh
```

Проверить свежесть, checksum и читаемость последней копии:

```bash
DUTYLOG_ENV_FILE=.env bash deploy/scripts/check-backup-freshness.sh
```

Безопасно отрепетировать восстановление в отдельном временном PostgreSQL:

```bash
DUTYLOG_ENV_FILE=.env bash deploy/scripts/restore-drill.sh
```

Настоящее восстановление выбранного окружения выполняется только вручную с явным подтверждением:

```bash
CONFIRM_RESTORE=RESTORE DUTYLOG_ENV_FILE=.env \
  bash deploy/scripts/restore-postgres.sh backups/<file>.dump
```

Ежедневный systemd timer устанавливается отдельным скриптом. Подробный runbook: [`docs/BACKUP_RESTORE_OPERATIONS_V27.5.0.md`](docs/BACKUP_RESTORE_OPERATIONS_V27.5.0.md).

## Production-профиль

В production используется PostgreSQL и Flyway-миграции. Hibernate работает в режиме валидации схемы, поэтому изменения БД должны оформляться новыми файлами миграций в `src/main/resources/db/migration`.

Для боевого запуска подготовлены:

- `deploy/compose/docker-compose.deploy.yml` — отдельный staging/production runtime с loopback-портами;
- `deploy/nginx/dutylog-staging.conf.example` и `dutylog-production.conf.example` — маршруты общего системного Nginx;
- `.github/workflows/deploy-staging.yml` и `deploy-production.yml` — автоматическая доставка immutable images;
- `deploy/env/.env.staging.example` и `.env.production.cicd.example` — серверные шаблоны окружений;
- `deploy/scripts/local-smoke-test.sh` — проверка контейнера до DNS/TLS/Nginx;
- `docker-compose.prod.yml` и `deploy/caddy/*` — прежний legacy/manual вариант, не активный CI/CD;
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
- [`docs/CICD.md`](docs/CICD.md) — ветки `test`/`main`/`master`, GitHub Environments и автоматический deploy.
- [`docs/STAGING.md`](docs/STAGING.md) — изоляция и безопасный сброс тестовой среды.
- [`docs/MIGRATION_SAFETY.md`](docs/MIGRATION_SAFETY.md) — правила Flyway и защита production-данных.
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
- [`docs/REGRESSION_TEST_BASELINE.md`](docs/REGRESSION_TEST_BASELINE.md) — карта ручных сценариев и автоматических regression-тестов, запуск `mvn verify` и JaCoCo.
- [`docs/CALENDAR_MOBILE_EXPERIENCE_V27.17.0.md`](docs/CALENDAR_MOBILE_EXPERIENCE_V27.17.0.md) — контракт месяца, недели и почасового дня.
- [`docs/TIME_SETTINGS_TRANSACTION_HOTFIX_V27.16.3.md`](docs/TIME_SETTINGS_TRANSACTION_HOTFIX_V27.16.3.md) — защита черновика формы и сериализация ручного/автоматического применения времени.
- [`docs/NEXT_ROUTE_TIME_SETTINGS_E2E_HOTFIX_V27.16.2.md`](docs/NEXT_ROUTE_TIME_SETTINGS_E2E_HOTFIX_V27.16.2.md) — выравнивание E2E с Today route и базовая отмена debounce.
- [`docs/TODAY_RUNTIME_HOTFIX_V27.16.1.md`](docs/TODAY_RUNTIME_HOTFIX_V27.16.1.md) — причина каскадного падения Playwright и контракт исправления forward-reference между frontend bundles.
- [`docs/SHIFT_OCCURRENCES_CALENDAR_PROJECTION_V27.11.0.md`](docs/SHIFT_OCCURRENCES_CALENDAR_PROJECTION_V27.11.0.md) — абсолютные экземпляры смен, перенос по локальным датам и миграция legacy-строк.
- [`docs/TASK_DETAILS_V27.10.0.md`](docs/TASK_DETAILS_V27.10.0.md) — read-first детали задачи, описание, owner-scoped GET и границы редактора.
- [`docs/OVERTIME_SPLIT_PROJECTION_CONTRACT_HOTFIX_V27.9.4.md`](docs/OVERTIME_SPLIT_PROJECTION_CONTRACT_HOTFIX_V27.9.4.md) — устойчивые номера частей split-отгула в ledger DTO и корректный midnight E2E-контракт.
- [`docs/OVERTIME_PREFLIGHT_INTEGRITY_HOTFIX_V27.9.3.md`](docs/OVERTIME_PREFLIGHT_INTEGRITY_HOTFIX_V27.9.3.md) — preflight-проверка отгулов до мутации и синхронизация CI-контрактов.
- [`docs/OVERTIME_LEDGER_INTEGRITY_HOTFIX_V27.9.2.md`](docs/OVERTIME_LEDGER_INTEGRITY_HOTFIX_V27.9.2.md) — атомарная пересборка FIFO, инварианты журнала и ясное удаление целого отгула.
- [`docs/OVERTIME_ALLOCATION_RENDERING_HOTFIX_V27.9.1.md`](docs/OVERTIME_ALLOCATION_RENDERING_HOTFIX_V27.9.1.md) — исправление runtime-рендера точных межсуточных списаний.
- [`docs/OVERTIME_INTERVAL_ENGINE_V27.9.0.md`](docs/OVERTIME_INTERVAL_ENGINE_V27.9.0.md) — поминутный FIFO, точные интервалы и мастер миграции legacy overtime.
- [`docs/TIMEZONE_PROJECTION_REFRESH_V27.8.1.md`](docs/TIMEZONE_PROJECTION_REFRESH_V27.8.1.md) — hotfix authoritative refresh после смены work/display timezone.
- [`docs/ZONED_WORK_INTERVALS_V27.8.0.md`](docs/ZONED_WORK_INTERVALS_V27.8.0.md) — контракт абсолютных смен, work/display-проекций и новых timezone-aware начислений переработки.
- [`docs/TASK_LEDGER_LAYOUT_HOTFIX_V27.7.1.md`](docs/TASK_LEDGER_LAYOUT_HOTFIX_V27.7.1.md) — контракт исправления карточек задач и действий журнала переработок.
- [`docs/TIME_FOUNDATION_V27.7.0.md`](docs/TIME_FOUNDATION_V27.7.0.md) — контракт рабочего/display времени, абсолютных моментов, DST и будущих рабочих интервалов.
- [`docs/TASK_POLISH_CONSISTENCY_V27.6.3.md`](docs/TASK_POLISH_CONSISTENCY_V27.6.3.md) — контракт релиза качества задач: сроки, open-first, прогресс, подзадачи и mobile polish.
- [`docs/TASK_SUBTASKS_V27.6.2.md`](docs/TASK_SUBTASKS_V27.6.2.md) — продуктовый и технический контракт одноуровневых подзадач.
- [`docs/RELEASE_CANDIDATE.md`](docs/RELEASE_CANDIDATE.md) — что проверено в v27.2.5 и как принимать RC.
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


## История контрольных точек

Ниже сохранены названия опубликованных релизов, на которые опираются regression-contracts и эксплуатационная документация:

- **v27.2.5 — Calendar day identity hotfix**
- **v27.2.10 — Task board status validation hotfix**
- **v27.2.11 — Task priority regression test correction**
- **v27.2.12 — Important dates regression suite**
- **v27.2.13 — Shift types and calendar patterns regression suite**
- **v27.2.14 — Quick scenarios and overtime API regression suite**
- **v27.2.15 — Structured module-disabled error envelope hotfix**
- **v27.2.16 — Profile and administration regression suite**
- **v27.2.17 — Admin test context bootstrap hotfix**
- **v27.2.18 — Mobile auth and sync lifecycle regression suite**
- **v27.2.19 — PostgreSQL migration and CI version hotfix**
- **v27.2.20 — Telegram bot regression and delivery hardening suite**
- **v27.2.21 — Telegram date validation and test harness hotfix**
- **v27.2.22 — Security infrastructure regression and auth hardening suite**
- **v27.2.23 — Security test contract and secret-safe error logging hotfix**
- **v27.2.24 — Coverage floor and startup/module regression suite**
- **v27.2.25 — Playwright browser E2E regression baseline**
- **v27.2.26 — Playwright selector, accordion and line-ending hotfix**
- **v27.2.27 — Playwright marker accordion hotfix**
- **v27.2.28 — Staging deployment gate and diagnostics hardening**
- **v27.2.29 — Final security and product audit hardening**
- **v27.2.30 — Host nginx CI/CD deployment hardening**
- **v27.2.31 — Authenticated deployment smoke-test hotfix**
- **v27.4.0 — Unified overtime editors**
- **v27.4.1 — Overtime scenario manager**
- **v27.4.2 — Timezone simplification and critical regression pack**
- **v27.4.3 — Reminder timezone and sync UX bugfix**
- **v27.5.1 — Telegram commands and mobile sync status bugfix**
- **v27.5.2 — Telegram command menu and quick actions**
- **v27.6.0 — Mobile Tasks & Inbox UX**
- **v27.6.1 — Quick Capture Polish**
- **v27.6.3 — Polish & Consistency**
- **v27.6.2 — Tasks & Subtasks**
- **v27.5.0 — Backup and recovery hardening**

## Текущая стратегия развёртывания

DutyLog пока работает как закрытая beta на `https://stage.yaruga-trophy.ru`. Отдельный production на общем VPS сознательно не поднимается: сервер уже обслуживает YARUGA, а постоянный третий Spring Boot/PostgreSQL-контур оставил бы слишком мало запаса по памяти.

Текущий рабочий процесс:

- ветка `test` собирает immutable image, запускает все проверки и автоматически обновляет staging;
- staging защищён HTTPS, health/smoke gates и ежедневным PostgreSQL backup через systemd timer;
- isolated restore drill уже доказал восстановление схемы, Flyway и пользовательских таблиц без вмешательства в живую базу;
- production workflow, rollback и отдельные environment-шаблоны сохраняются в репозитории, но будут активированы только на отдельном более мощном сервере и собственном домене;
- YARUGA и её контейнеры не участвуют в DutyLog deployment.

Следующий практический шаг — пропустить v27.22.2 через полный Maven и Playwright gate, подтвердить `/actuator/info` на staging и затем вручную проверить нормы, preview, конфликты, пресеты 14/28/35 и проекции отсутствий в Month / Week / Day. Следующий продуктовый этап — **v27.23.0 — External Calendar Sync**.

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


CI permission stabilization in v27.2.5:

- GitHub Actions runs release checks through `bash ./deploy/scripts/release-check.sh`.
- CI no longer fails when executable bits are lost on Windows/archive checkouts.
