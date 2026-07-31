# v27.22.2 — Workspace Route E2E Navigation Hotfix

## Причина

Полный Playwright gate v27.22.1 дошёл до 27/31 сценариев. Все четыре падения имели одну причину: тесты напрямую нажимали `#tabbar a[data-view="tasks"]`, хотя Shift Worker workspace после Vacation Planner намеренно держит Tasks вне primary navigation. DOM-узел существует для общего роутера, но получает `workspaceHidden`.

Падали:

- `mobile-layout.spec.js` — переход к мобильным фильтрам задач;
- `task-details.spec.js` — проверка timed deadline после смены timezone;
- `task-modules.spec.js` — сохранность данных при выключении/включении модуля;
- `task-modules.spec.js` — быстрый Inbox → Task flow.

## Решение

- Сценарии открывают Tasks через существующий `openView(page, 'tasks')`.
- Helper сначала использует видимый primary tab, а при скрытом workspace route меняет `location.hash` на штатный `#tasks`.
- Проверка module toggle смотрит на `moduleHidden` у `#view-tasks`, а не на видимость пункта в конкретной workspace-компоновке.
- Primary navigation Shift Worker не расширяется обратно: Vacation остаётся на принятом месте, Tasks доступен через hidden-route links и прямой hash route.

## Границы

Не меняются:

- production runtime;
- HTTP API и OpenAPI;
- модель данных и Flyway V40;
- бизнес-логика задач, Inbox, Vacation Planner и timezone-проекций;
- baseline: 103 Java test classes / 544 `@Test` methods / 31 Playwright scenarios.

## Ожидаемый CI

```text
Maven:      Tests run: 544, Failures: 0, Errors: 0
Playwright: 31 passed
```
