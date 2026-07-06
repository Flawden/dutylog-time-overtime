# Изменения

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
