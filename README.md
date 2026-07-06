# Журнал смен

Календарь смен с Markdown-заметками, автозаполнением графиков, важными днями, задачами и учётом переработки/отгулов.

Текущий стек:

- Spring Boot 3.3.5
- Java 17
- Spring Web / Data JPA / Security / Validation
- H2 для локальной разработки
- PostgreSQL + Flyway для production-профиля
- простой веб-интерфейс на HTML/CSS/JS
- PWA-заготовка: manifest, service worker, иконки
- Docker Compose для запуска на сервере

## Идея продукта

Пользователь открывает календарь и отмечает, какая смена была в конкретный день. В базовом наборе есть три встроенные смены: **«Дневная»**, **«Ночная»** и **«Выходной»**. Всё остальное пользователь создаёт сам: «5 часов», «12 часов», «Сутки», «Отпуск», «Больничный» и любые другие варианты. Для каждой кастомной смены задаются название, цвет и количество часов.

В каждый день можно провалиться и написать заметку в Markdown. Если в дне есть заметка, на клетке календаря появляется отметка.

Также у дня теперь есть отдельные задачи с чекбоксами. Это не Markdown-задачи внутри заметки, а отдельные сущности: если есть невыполненные задачи, на клетке календаря появляется `!`; когда задачи выполнены, индикатор гаснет/становится спокойным.

Важные дни вынесены отдельно: дни рождения, годовщины, платежи, техосмотры и любые повторяющиеся события. Поддерживаются режимы: один раз, каждый месяц, каждый год. В календаре такие дни помечаются `★`.

От выбранного дня можно запустить автозаполнение графика: 2/2, день/ночь/48, пятидневка 5/2, день/72, ночь/72. По умолчанию график заполняется на 31 день вперёд, включая следующий месяц. Пятидневка привязана к реальным дням недели: Пн–Пт рабочие, Сб–Вс выходные. После заполнения каждый день всё равно редактируется вручную.

Добавлен простой учёт переработки и отгулов: в дне можно отдельно указать дополнительные часы, например ППР после смены, и отдельно часы, которые списаны как отгул. Баланс периода считается как:

```text
переработка - отгулы
```

Пример: 18 июня остался с 17:00 до 24:00 — ставишь `+7`. 19 июня продолжил с 00:00 до 08:00 — ставишь `+8`, но если из-за этого не вышел на дневную смену, списываешь `-8`. Итого за два дня остаётся `+7` часов.

## Что добавлено в v11-important-days-tasks

Эта версия добавляет поверх v10 новый продуктовый слой:

- отдельные задачи дня с чекбоксами;
- индикатор `!` в календаре, пока есть невыполненные задачи;
- отдельные важные дни;
- повторения важных дней: один раз, каждый месяц, каждый год;
- индикатор `★` на календаре для важных дней;
- новые сущности `DayTask` и `ImportantDay`;
- новые endpoint’ы `/api/tasks` и `/api/important-days`;
- `/api/calendar?from=&to=` теперь отдаёт ещё `tasks` и `importantDays`;
- добавлена миграция `V2__important_days_and_tasks.sql`.

## Что добавлено в v9-production-foundation

Эта версия — первый шаг от MVP к нормальному продукту:

- добавлен PostgreSQL-драйвер;
- добавлен Flyway;
- добавлена production-миграция `V1__init.sql`;
- добавлен production-профиль `application-prod.properties`;
- в production отключён `ddl-auto=update`, используется `ddl-auto=validate`;
- H2 оставлена только для локального dev-режима;
- добавлен Dockerfile;
- добавлен `docker-compose.yml` с PostgreSQL и приложением;
- добавлен `.env.example`;
- добавлен пример nginx-конфига;
- добавлен скрипт бэкапа PostgreSQL;
- добавлен Spring Boot Actuator health endpoint;
- подготовлены документы `docs/ROADMAP.md` и `docs/ANDROID_API_PLAN.md`.

## Быстрый старт для разработки

Нужны: **JDK 17+** и **Maven**. Через IntelliJ IDEA можно просто открыть папку с `pom.xml`.

### Через IntelliJ IDEA

1. File → Open → выбери папку проекта.
2. Дождись загрузки Maven-зависимостей.
3. Запусти `ShiftsApplication`.
4. Открой http://localhost:8080

### Через консоль

```bash
mvn spring-boot:run
```

В dev-режиме используется H2. База лежит в:

```text
./data/shifts.mv.db
```

Консоль БД:

```text
http://localhost:8080/h2-console
JDBC URL: jdbc:h2:file:./data/shifts
User: sa
Password: пустой
```

## Запуск production-режима через Docker Compose

1. Скопируй `.env.example` в `.env`:

```bash
cp .env.example .env
```

2. Открой `.env` и поменяй пароль:

```text
POSTGRES_PASSWORD=change_me_strong_password
SPRING_DATASOURCE_PASSWORD=change_me_strong_password
```

3. Запусти:

```bash
docker compose up -d --build
```

4. Открой:

```text
http://localhost:8080
```

5. Проверка health endpoint:

```text
http://localhost:8080/actuator/health
```

В production-режиме таблицы создаёт Flyway из файла:

```text
src/main/resources/db/migration/postgresql/V1__init.sql
```

Hibernate только проверяет, что схема БД соответствует сущностям.

## Запуск production-режима без Docker

Нужен установленный PostgreSQL. Создай базу и пользователя, затем задай переменные окружения:

```bash
export SPRING_PROFILES_ACTIVE=prod
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/shift_calendar
export SPRING_DATASOURCE_USERNAME=shift_calendar
export SPRING_DATASOURCE_PASSWORD=your_password
mvn spring-boot:run
```

## Бэкап базы

Если проект запущен через Docker Compose:

```bash
./deploy/scripts/backup-postgres.sh
```

Бэкап появится в папке:

```text
./backups/
```

## Пример nginx

Шаблон лежит здесь:

```text
deploy/nginx/shift-calendar.conf.example
```

Для реального сервера:

1. подставь свой домен;
2. прокинь домен на сервер;
3. поставь nginx;
4. добавь конфиг;
5. выпусти HTTPS через certbot.

## Авторизация

Сейчас используется классическая сессионная авторизация через cookie `JSESSIONID`.

Это нормально для веб-версии, но для Android потом лучше добавить отдельный token-based API:

```text
POST /api/auth/login
POST /api/auth/refresh
POST /api/auth/logout
```

Пока Android можно будет подключать и к текущему API, но удобнее будет после JWT/refresh token слоя.

Пароли хранятся через BCrypt. Данные пользователей изолированы: каждый запрос берёт текущего пользователя из сессии и работает только с его сменами/днями.

Важное ограничение текущей версии: CSRF отключён для удобства fetch-запросов. Перед полноценным публичным запуском это надо заменить на нормальную схему: CSRF для web-сессий или JWT API для web/mobile.

## Что внутри

```text
src/main/java/ru/daniil/shifts/
├── ShiftsApplication.java
├── config/SecurityConfig.java
├── dto/Dtos.java
├── model/
│   ├── AppUser.java
│   ├── ShiftType.java
│   ├── DayEntry.java
│   ├── DayTask.java
│   ├── ImportantDay.java
│   └── RepeatMode.java
├── repo/
│   ├── UserRepository.java
│   ├── ShiftTypeRepository.java
│   ├── DayEntryRepository.java
│   ├── DayTaskRepository.java
│   └── ImportantDayRepository.java
├── service/
│   ├── CurrentUserService.java
│   ├── ShiftTypeService.java
│   ├── DayEntryService.java
│   ├── CalendarService.java
│   ├── OvertimeService.java
│   ├── TaskService.java
│   ├── ImportantDayService.java
│   └── exception/ApiException.java
└── web/
    ├── ApiExceptionHandler.java
    ├── AuthController.java
    ├── ShiftTypeController.java
    ├── DayController.java
    ├── CalendarController.java
    ├── OvertimeController.java
    ├── TaskController.java
    └── ImportantDayController.java

src/main/resources/
├── application.properties
├── application-prod.properties
├── db/migration/postgresql/V1__init.sql
├── db/migration/postgresql/V2__important_days_and_tasks.sql
└── static/
    ├── index.html
    ├── login.html
    ├── manifest.json
    ├── service-worker.js
    └── icons/

deploy/
├── nginx/shift-calendar.conf.example
└── scripts/backup-postgres.sh
```

## v11-important-days-tasks

В v11 добавлены задачи дня и важные дни. Это отдельные сущности, поэтому Android и Telegram потом смогут работать с ними напрямую: показывать список задач, чекать выполнение, присылать уведомления по важным датам.

## v10-api-architecture

В v10 бизнес-логика вынесена из контроллеров в сервисы, а для Android/PWA добавлен диапазонный календарный endpoint. Старые endpoint’ы веб-версии сохранены, поэтому текущий интерфейс продолжает работать.

## API

Все эндпоинты, кроме регистрации и health, требуют залогиненной сессии.

| Метод | URL | Что делает |
|---|---|---|
| POST | `/api/auth/register` | регистрация |
| GET | `/api/auth/me` | текущий пользователь |
| POST | `/perform_login` | вход через Spring Security |
| POST | `/logout` | выход |
| GET | `/api/shift-types` | список типов смен |
| POST | `/api/shift-types` | создать тип смены |
| DELETE | `/api/shift-types/{id}` | удалить пользовательский тип |
| GET | `/api/calendar?from=2026-06-01&to=2026-07-31` | Android-friendly календарь диапазоном: смены + дни + баланс |
| GET | `/api/days?year=2026&month=7` | записи месяца для текущего веба |
| PUT | `/api/days/2026-07-02` | сохранить день |
| POST | `/api/days/fill` | заполнить график |
| GET | `/api/overtime/balance?from=&to=` | сводка переработок и отгулов |
| GET | `/api/overtime/ledger?from=&to=` | журнал переработок/отгулов |
| GET | `/actuator/health` | health check |

Пример сохранения дня:

```json
{
  "shiftTypeId": 1,
  "note": "# ППР\nОстался после смены.",
  "overtimeHours": 7,
  "timeOffHours": 0
}
```

## Важные замечания

- Старую H2-базу нельзя автоматически перенести в PostgreSQL этой версией. Для теста production-режима проще начать с чистой базы.
- Если уже запускал старые версии в dev-режиме, а поведение странное — удали папку `data` и зарегистрируй пользователя заново.
- Production-режим уже не использует `ddl-auto=update`; все будущие изменения БД надо делать через новые файлы Flyway: `V2__...sql`, `V3__...sql` и так далее.
- Android-приложение лучше делать после стабилизации API и авторизации через токены.

## Следующие шаги

Смотри:

```text
docs/ROADMAP.md
docs/ANDROID_API_PLAN.md
docs/API.md
```
