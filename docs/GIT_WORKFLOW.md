# Git workflow для DutyLog

DutyLog уже слишком большой для разработки архивами. Git нужен не для красоты, а чтобы спокойно откатываться и не терять ветки.

## Что хранить в Git

Храним:

- `src/`
- `docs/`
- `deploy/`
- `README.md`
- `CHANGES.md`
- `pom.xml`
- `Dockerfile`
- `docker-compose.yml`
- `.env.example`

Не храним:

- `.env`
- реальные пароли
- Telegram bot token
- backup-файлы с личными данными
- папку `data/`
- PostgreSQL volume

## Коммит новой версии

```bash
git add -A
git commit -m "feat: add diagnostics and settings polish"
git tag -a v20.3 -m "v20.3 — diagnostics and settings polish"
git push
git push --tags
```

## Как снести код, но не базу

Код можно удалять и клонировать заново. Данные живут в PostgreSQL volume, а не в папке с исходниками.

Безопасно:

```bash
docker compose down
git pull
docker compose up -d --build
```

Опасно для данных:

```bash
docker compose down -v
```

`-v` удаляет volumes, то есть может снести базу.

## Откат на прошлую версию

```bash
git checkout v20.2.1
docker compose up -d --build
```

Перед откатом production-базы лучше сделать backup, потому что Flyway-миграции обычно идут только вперёд.

## Полезные команды

История:

```bash
git log --oneline --decorate --graph --all
```

Посмотреть теги:

```bash
git tag
```

Сравнить версии:

```bash
git diff v20.2.1..v20.3
```
