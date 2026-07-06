# Изменения

## v14.2-overtime-time-calc

Поверх версии с аккордеоном добавлен автоподсчёт часов переработки по интервалу работы.

### Переработка по времени

- В начислении переработки теперь можно указать `начало` и `конец` через `datetime-local`.
- Добавлены поля `обед, мин` и `вычесть план, ч`.
- Формула: `переработка = конец - начало - обед - плановые часы`.
- Если заносишь только кусок переработки, плановые часы оставляешь `0`.
- Если заносишь всю фактическую смену целиком, можно вычесть план по кнопке `план по смене`.
- Добавлена быстрая кнопка `17–08` для сценария “остался в ночь”.
- Ручной ввод часов сохранён: можно по-прежнему просто вписать количество часов без начала/конца.
- В таблице переработок отображается, что запись рассчитана из интервала, с учётом обеда и вычтенных плановых часов.

### API и БД

- `POST /api/overtime/credits` теперь принимает `startDateTime`, `endDateTime`, `breakMinutes`, `plannedHours`.
- Backend пересчитывает часы сам, поэтому фронт не является единственным источником расчёта.
- Добавлена миграция `V5__overtime_time_calculation.sql`.

## v13-overtime-accounting

Переработка вынесена в полноценную бухгалтерию часов.

### Журнал начислений и списаний

- Добавлены сущности `OvertimeCredit`, `OvertimeUsage`, `OvertimeAllocation`.
- Начисление переработки хранит дату, диапазон времени, часы и причину.
- Списание отгула хранит дату, часы и причину.
- Списание автоматически распределяется по начислениям по FIFO: сначала самые старые остатки.
- Переработка больше не “сгорает” при переходе на следующий месяц.
- Можно начислить переработку в мае и списать её в августе.
- Добавлена таблица переработок в веб-интерфейсе: день, время, начислено, причина, использовано, куда списано, остаток.

### API и БД

- Добавлен `GET /api/overtime/account`.
- Добавлен `POST /api/overtime/credits`.
- Добавлен `DELETE /api/overtime/credits/{id}`.
- Добавлен `POST /api/overtime/usages`.
- Добавлен `DELETE /api/overtime/usages/{id}`.
- `GET /api/calendar?from=&to=` теперь дополнительно отдаёт `overtimeAccount` с общим остатком переработки.
- Добавлена миграция `V4__overtime_accounting.sql`.

## v12-android-ready

Слой подготовки под полноценное Android-приложение.

### Мобильная авторизация

- Добавлена сущность `MobileAuthToken`.
- Добавлен `MobileAuthService`.
- Добавлен `BearerTokenAuthenticationFilter`.
- Android может ходить в API через `Authorization: Bearer <accessToken>`.
- Веб-сессия `JSESSIONID` сохранена и не сломана.
- Access token живёт коротко, refresh token — дольше.
- Refresh token ротируется при обновлении.
- В базе хранятся SHA-256 хэши токенов, а не сами токены.
- Добавлено управление мобильными сессиями/устройствами.

### Mobile API

- Добавлен `POST /api/mobile/auth/login`.
- Добавлен `POST /api/mobile/auth/refresh`.
- Добавлен `POST /api/mobile/auth/logout`.
- Добавлен `GET /api/mobile/auth/me`.
- Добавлен `GET /api/mobile/auth/sessions`.
- Добавлен `DELETE /api/mobile/auth/sessions/{id}`.
- Добавлен `GET /api/mobile/bootstrap?from=&to=`.
- Добавлен `POST /api/mobile/sync` для пакетной синхронизации изменений дней.

### БД и документация

- Добавлена миграция `V3__mobile_auth_tokens.sql`.
- Обновлены `README.md`, `docs/API.md`, `docs/ANDROID_API_PLAN.md`.

## v11-important-days-tasks

Новый продуктовый слой поверх календаря смен.

### Задачи

- Добавлены отдельные задачи дня с чекбоксами.
- Добавлены endpoint’ы `GET/POST/PATCH/DELETE /api/tasks`.
- На клетке календаря появляется `!`, если в дне есть невыполненные задачи.
- Когда все задачи выполнены, красный индикатор гаснет и превращается в спокойную отметку `✓`.
- Задачи не смешиваются с Markdown-заметкой и готовы для Android/Telegram.

### Важные дни

- Добавлены важные дни: дни рождения, годовщины, платежи, техосмотры и любые пользовательские события.
- Поддерживаются повторы: `NONE`, `MONTHLY`, `YEARLY`.
- Добавлены endpoint’ы `GET/POST/PATCH/DELETE /api/important-days`.
- Добавлен endpoint `GET /api/important-days/occurrences?from=&to=` для развёрнутых повторений в диапазоне.
- На календаре важные дни помечаются `★`.
- 29 февраля в невисокосный год показывается 28 февраля, чтобы ежегодное событие не пропадало.
- Ежемесячное событие на 31 число в коротких месяцах показывается в последний день месяца.

### API и БД

- `GET /api/calendar?from=&to=` теперь отдаёт `tasks` и `importantDays`.
- Добавлены сущности `DayTask`, `ImportantDay`, `RepeatMode`.
- Добавлены репозитории и сервисы `TaskService`, `ImportantDayService`.
- Добавлена миграция `V2__important_days_and_tasks.sql`.


## v10-api-architecture

Следующий шаг к полноценному продукту и Android-клиенту.

### API

- Добавлен Android-friendly endpoint `GET /api/calendar?from=&to=`.
- Ответ `/api/calendar` включает типы смен, дни диапазона и сводку переработки.
- Добавлен endpoint `GET /api/overtime/balance?from=&to=`.
- Добавлен endpoint `GET /api/overtime/ledger?from=&to=`.
- Старые endpoint’ы веб-версии `/api/days`, `/api/days/{date}`, `/api/days/fill` сохранены.
- Ограничен диапазон запросов календаря/переработок: максимум 366 дней.

### Архитектура

- Добавлен сервисный слой: `CurrentUserService`, `ShiftTypeService`, `DayEntryService`, `CalendarService`, `OvertimeService`.
- Контроллеры стали тоньше и больше не держат основную бизнес-логику.
- Добавлено доменное исключение `ApiException`.
- `ApiExceptionHandler` теперь обрабатывает сервисные ошибки единым JSON-форматом.
- В `DayEntryRepository` добавлен метод сортированной загрузки диапазона по дате.

### Документация

- Добавлен `docs/API.md` с описанием основных endpoint’ов.
- Обновлён `docs/ANDROID_API_PLAN.md` под новую архитектуру.
- Обновлён README.

## v9-production-foundation

Первый шаг от MVP к нормальному продукту и серверному запуску.

### Инфраструктура

- Добавлен PostgreSQL-драйвер.
- Добавлен Flyway.
- Добавлена production-миграция `src/main/resources/db/migration/postgresql/V1__init.sql`.
- Добавлен production-профиль `application-prod.properties`.
- В production включён Flyway и `spring.jpa.hibernate.ddl-auto=validate`.
- В dev-режиме H2 оставлена для быстрого запуска в IntelliJ.
- В dev-режиме Flyway отключён, Hibernate по-прежнему может обновлять H2-схему.
- Добавлен Dockerfile.
- Добавлен `docker-compose.yml` с PostgreSQL и приложением.
- Добавлен `.env.example`.
- Добавлен пример nginx-конфига: `deploy/nginx/shift-calendar.conf.example`.
- Добавлен скрипт бэкапа PostgreSQL: `deploy/scripts/backup-postgres.sh`.
- Добавлен Spring Boot Actuator health endpoint `/actuator/health`.

### Код

- Поле `note` в `DayEntry` теперь явно мапится как `text`, чтобы нормально работать с PostgreSQL.
- Поля `overtime_hours` и `time_off_hours` помечены как `nullable=false`.
- `/actuator/health` разрешён без авторизации.

### Документация

- README переписан под dev/prod запуск.
- Добавлен `docs/ROADMAP.md`.
- Добавлен `docs/ANDROID_API_PLAN.md`.

## v8-overtime

- Добавлены поля переработки и списания отгула в день.
- Добавлен месячный баланс переработки.
- Добавлены отметки `+7ч`, `-8ч` и т.п. в календаре.
- Массовое заполнение графика не стирает переработки и отгулы.

## v7-monthfill

- Исправлено заполнение графика через границу месяца.
- По умолчанию график заполняется на 31 день вперёд.
- Пятидневка привязана к реальным дням недели.

## v6-schedules

- Добавлена встроенная смена «Выходной».
- Добавлено массовое заполнение графика.
- Добавлены шаблоны 2/2, день/ночь/48, пятидневка 5/2, день/72, ночь/72.

## v5-customonly

- Стартовыми оставлены только базовые смены.
- Остальные типы смен пользователь создаёт сам.

## v4-dorabotano

- Исправлено автосохранение заметок.
- Добавлена валидация.
- Добавлен `ApiExceptionHandler`.
- Добавлен базовый PWA-слой.
