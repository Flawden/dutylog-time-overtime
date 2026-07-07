# v22.2 Production launch

Этот документ — короткий боевой сценарий первого запуска DutyLog на VPS. Он не заменяет `PRODUCTION_RUNBOOK.md`, а помогает не потеряться в день деплоя.

Текущий клиент: **web/PWA внутри Spring Boot-монолита**. Отдельного native mobile-приложения в v22.2 нет.

## Что считается успешным запуском

- Приложение открывается по HTTPS-домену.
- Bootstrap-администратор из `.env` создан или обновлён.
- Публично зарегистрированный пользователь не получает `ADMIN`.
- Неожиданные старые `ADMIN`-аккаунты демоутятся до `USER`, если bootstrap env настроен.
- Администратор видит `Система` и может закрыть публичную регистрацию.
- `/actuator/health` возвращает `UP`.
- `./deploy/scripts/smoke-test.sh https://domain` проходит.
- Backup PostgreSQL создаётся и копируется за пределы VPS.
- PWA открывается на телефоне, календарь и offline snapshot работают.
- Telegram включён и проверен, либо явно оставлен выключенным.

## 1. Подготовить VPS

```bash
sudo mkdir -p /opt/dutylog
sudo chown -R "$USER":"$USER" /opt/dutylog
cd /opt/dutylog
```

```bash
git clone <repo-url> .
git checkout v22.2
```

Проверить, что домен уже смотрит на IP сервера:

```bash
getent hosts dutylog.example.com
```

## 2. Создать production `.env`

```bash
cp .env.production.example .env
nano .env
```

Минимально заменить:

```env
DUTYLOG_DOMAIN=dutylog.example.com
POSTGRES_PASSWORD=<long-random-password>
SPRING_DATASOURCE_PASSWORD=<long-random-password>
```

Если Telegram пока не нужен:

```env
DUTYLOG_TELEGRAM_ENABLED=false
DUTYLOG_TELEGRAM_POLLING_ENABLED=false
```

Если Telegram нужен сразу:

```env
DUTYLOG_TELEGRAM_ENABLED=true
DUTYLOG_TELEGRAM_BOT_TOKEN=<botfather-token>
DUTYLOG_TELEGRAM_BOT_USERNAME=<bot-username-without-at>
DUTYLOG_TELEGRAM_POLLING_ENABLED=true
DUTYLOG_TELEGRAM_NOTIFICATIONS_ENABLED=true
```

## 3. Подготовить Caddy

```bash
cp deploy/caddy/Caddyfile.example deploy/caddy/Caddyfile
```

Обычно менять `Caddyfile` не нужно: домен берётся из `DUTYLOG_DOMAIN`.

## 4. Прогнать preflight

```bash
./deploy/scripts/check-production-env.sh
```

Ошибки надо исправить до запуска. Предупреждения можно оставить только осознанно.

## 5. Первый старт

```bash
docker compose -f docker-compose.prod.yml up -d --build
```

Проверить контейнеры:

```bash
docker compose -f docker-compose.prod.yml ps
docker compose -f docker-compose.prod.yml logs --tail=200 app
docker compose -f docker-compose.prod.yml logs --tail=100 caddy
```

## 6. Smoke test

```bash
./deploy/scripts/smoke-test.sh https://dutylog.example.com
```

Проверяется:

- `/actuator/health`;
- `login.html`;
- app shell;
- `manifest.json`;
- `service-worker.js` версии `v22.2`;
- `app.js` версии `22.2`;
- защищённый admin API не падает.

## 7. Первый пользователь

Открыть:

```text
https://dutylog.example.com
```

Администратор создаётся из `.env`, а не из первой публичной регистрации:

```env
DUTYLOG_ADMIN_USERNAME=your_admin_login
DUTYLOG_ADMIN_PASSWORD=long_random_password_at_least_20_chars
```

Войти под этим пользователем. Затем создать обычного пользователя и убедиться, что он не админ. После создания нужных аккаунтов открыть `Система` → `Публичная регистрация` и закрыть регистрацию, если приложение личное и новых пользователей пока не ждём.

Проверить:

- у bootstrap-админа в шапке есть `Система`;
- у обычного пользователя `Система` скрыта;
- в `Система` версия сервера `22.2`;
- база данных `ok`;
- Telegram-статус соответствует `.env`;
- публичная регистрация имеет ожидаемый статус: `открыта` или `закрыта`.

## 8. Backup сразу после первого запуска

```bash
./deploy/scripts/backup-postgres.sh
./deploy/scripts/list-backups.sh
```

Скопировать backup за пределы VPS:

```bash
scp /opt/dutylog/backups/dutylog-*.dump user@other-host:/safe/place/
```

## 9. PWA/offline smoke на телефоне

- Открыть сайт на телефоне.
- Войти в аккаунт.
- Открыть календарь и дождаться загрузки.
- Установить PWA, если браузер предлагает.
- Выключить сеть.
- Перезагрузить страницу.
- Проверить, что календарь открылся из локального snapshot.
- Изменить заметку.
- Включить сеть.
- Проверить, что очередь ушла.

## 10. Telegram smoke, если включён

В профиле DutyLog создать код привязки и отправить боту:

```text
/start DL-XXXXXX
```

Проверить команды:

```text
/today
/tasks
/balance
/help
```

## 11. После запуска

Минимальная эксплуатационная привычка:

```bash
cd /opt/dutylog
./deploy/scripts/backup-postgres.sh
git pull
docker compose -f docker-compose.prod.yml up -d --build
./deploy/scripts/smoke-test.sh https://dutylog.example.com
```

Перед каждым обновлением — backup. Перед любыми рискованными действиями — backup. Команду `docker compose down -v` не использовать, если не нужно специально удалить базу.
