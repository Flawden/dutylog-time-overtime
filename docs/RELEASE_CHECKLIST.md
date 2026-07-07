# Release checklist для DutyLog

Этот чеклист нужен перед выдачей архива, тегом и VPS-деплоем. Текущая клиентская часть — web/PWA внутри Spring Boot-монолита. Отдельного native mobile-приложения в v22.2 нет, поэтому мобильная проверка означает проверку PWA в браузере телефона и установленной web-оболочке.

## 1. Статические проверки

```bash
node --check src/main/resources/static/app.js
node --check src/main/resources/static/service-worker.js
python3 -m json.tool src/main/resources/static/manifest.json
```

Проверить ссылки JS на HTML id:

```bash
python3 - <<'PY'
from pathlib import Path
import re
html = Path('src/main/resources/static/index.html').read_text(encoding='utf-8')
js = Path('src/main/resources/static/app.js').read_text(encoding='utf-8')
ids = set(re.findall(r'\bid=["\']([^"\']+)["\']', html))
refs = set(re.findall(r'\$\(["\']([^"\']+)["\']\)', js)) | set(re.findall(r'getElementById\(["\']([^"\']+)["\']\)', js))
print("missing ids:", sorted(refs - ids))
print("duplicate ids:", sorted([x for x in ids if list(re.findall(r'\bid=["\']([^"\']+)["\']', html)).count(x) > 1]))
PY
```

Дополнительно:

```bash
python3 - <<'PY'
import xml.etree.ElementTree as ET
ET.parse('pom.xml')
print('pom.xml ok')
PY

python3 - <<'PY'
from pathlib import Path
for p in ['docker-compose.yml','docker-compose.prod.yml']:
    text = Path(p).read_text(encoding='utf-8')
    assert 'services:' in text, p
    print(p, 'basic ok')
PY

bash -n deploy/scripts/*.sh
```

## 2. Offline QA через DevTools

DevTools → Network → Offline:

1. Открыть приложение онлайн и дождаться полной загрузки календаря.
2. Открыть панель синхронизации из индикатора в шапке. Убедиться, что диагностика оффлайна видна.
3. Выключить сеть.
4. Перезагрузить страницу.
5. Проверить, что календарь открылся из локальной копии.
6. Изменить смену выбранного дня.
7. Изменить заметку выбранного дня.
8. Поставить задачу выполненной и снять выполнение обратно.
9. Проверить, что счётчик «не отправлено» вырос.
10. Проверить `Скачать локальные данные`: JSON должен содержать `app`, `version`, `snapshot`, `queue`, `failed`, `meta`.
11. Включить сеть.
12. Проверить, что очередь отправилась, счётчик обнулился, snapshot обновился.
13. Перезагрузить страницу онлайн и проверить совпадение данных с сервером.

## 3. Проверка двух вкладок

1. Открыть DutyLog в двух вкладках одного браузера.
2. В первой вкладке выключить сеть и создать 2–3 offline-изменения.
3. Включить сеть.
4. Быстро нажать `Синхронизировать` в обеих вкладках.
5. Ожидаемый результат: одна вкладка берёт lock, вторая не запускает параллельный replay. Очередь не дублируется.

## 4. Проверка запрещённых offline-операций

В offline-режиме попытаться выполнить:

- начисление переработки;
- списание отгула;
- автозаполнение графика;
- изменение быстрых сценариев;
- изменение уведомлений;
- Telegram-настройки;
- изменение профиля.

Ожидаемый результат: операция не выполняется, UI показывает понятное сообщение, что нужен сервер. Offline scope не расширяется.

## 5. Проверка PWA на телефоне

1. Открыть приложение в мобильном браузере.
2. Установить как PWA, если браузер предлагает.
3. Проверить шапку, календарь, панель выбранного дня и панель синхронизации.
4. Повторить минимальный offline-сценарий: открыть онлайн → выключить сеть → перезагрузить → изменить заметку → включить сеть.
5. Проверить, что кнопки в панели синхронизации не ломают мобильную вёрстку.


## 6. Production preflight для v22.2

Перед первым VPS-запуском:

```bash
cp .env.production.example .env
cp deploy/caddy/Caddyfile.example deploy/caddy/Caddyfile
./deploy/scripts/check-production-env.sh
```

После запуска:

```bash
./deploy/scripts/smoke-test.sh https://your-domain.example
./deploy/scripts/backup-postgres.sh
```

Проверить в админском разделе `Система`, что серверная версия — `22.2`, база `ok`, Telegram-статус соответствует `.env`, публичная регистрация имеет ожидаемый статус.

## 7. Перед production-деплоем

- Проверить `.env.production`.
- Проверить `docker-compose.prod.yml`.
- Проверить `deploy/caddy/Caddyfile`.
- Сделать backup перед обновлением.
- Выполнить smoke test после запуска.
- Проверить `/login.html`, `/manifest.json`, `/actuator/health`.
- Проверить Telegram long polling/уведомления, если они включены.

## 8. Git

```bash
git add -A
git commit -m "fix: harden public registration"
git tag -a v22.2 -m "v22.2 — registration hardening"
```
