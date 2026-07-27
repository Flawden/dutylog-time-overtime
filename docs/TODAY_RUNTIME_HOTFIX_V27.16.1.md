# DutyLog v27.16.1 — Today Runtime & Repository Truth Hotfix

## Симптом

После v27.16.0 backend успешно запускался и API отвечали `200`, однако все 24 Playwright-сценария падали. Общая browser page error:

```text
openQuickActions is not defined
```

Большинство последующих timeout/hidden-ошибок были каскадом: выполнение `35-today.js` останавливалось до инициализации остальной страницы.

## Причина

`index.html` загружает frontend bundles синхронно в порядке:

```text
10-core → 20-data → 30-calendar → 35-today → 40-overtime → 50-tasks → 60-settings → 70-user-boot
```

`openQuickActions` объявлена в `50-tasks.js`, но `35-today.js` передавал её как уже существующую ссылку:

```js
$("todayQuickMore")?.addEventListener("click", openQuickActions);
```

JavaScript должен вычислить второй аргумент сразу во время выполнения `35-today.js`. В этот момент `50-tasks.js` ещё не загружен, поэтому возникал `ReferenceError`.

## Исправление

Обработчик теперь использует отложенное разрешение имени:

```js
$("todayQuickMore")?.addEventListener("click", () => openQuickActions());
```

К моменту реального клика все синхронные bundles уже загружены, а `openQuickActions` определена.

## Защита от регрессии

- Java frontend-contract требует безопасную форму и запрещает прямую forward-reference.
- `deploy/scripts/release-check.sh` повторяет ту же проверку в быстром локальном gate.
- Полный Playwright gate остаётся обязательным перед staging deploy.

## Repository truth

Hotfix также синхронизирует основные документы с фактическим состоянием:

- current release: v27.16.1;
- runtime/build baseline: Java 17;
- Flyway: V1–V36;
- current roadmap: v27.17.0 Calendar Mobile Experience;
- API/release checklist examples no longer advertise v27.9.4/v27.2.5 as the current release.

Backend, database schema, overtime/FIFO, task deadlines, shift occurrences and note persistence не менялись.
