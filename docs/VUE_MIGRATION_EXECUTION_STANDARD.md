---
title: "DutyLog — Vue Migration Execution Standard"
status: binding
created: 2026-08-04
updated: 2026-08-04
applies_to: "v27.35.0–v27.40.0"
---

# DutyLog — Vue Migration Execution Standard

Этот документ является обязательным Definition of Done для каждого релиза полного перехода DutyLog на Vue. Его цель — не просто переписать интерфейс, а удалить двойное владение, сохранить поведение продукта и не перенести старую архитектурную энтропию в `.vue`-файлы.

## 1. Неподвижные архитектурные правила

1. Spring Boot остаётся владельцем бизнес-правил, расчётов, прав доступа, FIFO, Payroll, closed periods и persistence.
2. Vue отвечает за presentation, interaction, client-side orchestration и локальное UI-state.
3. Один repository, одна версия, один release archive, один application image и один runtime app-container сохраняются.
4. Прямой доступ Vue к mutable legacy `window.state`, hidden legacy DOM и внутренним legacy functions запрещён.
5. Новый глобальный store допускается только при явной межмаршрутной необходимости; server response не копируется в несколько независимых stores.
6. Доменные API-типы берутся из generated OpenAPI contract, а не дублируются вручную.
7. Новые product features не добавляются в legacy frontend.

## 2. Migration Manifest — создаётся до начала доменного релиза

Для каждого домена создаётся файл:

```text
docs/migration/<domain>-vue-migration-manifest.md
```

Обязательные разделы:

```text
Domain owner
Legacy entry points
User journeys
API endpoints
Server invariants
Offline/PWA behavior
Accessibility requirements
Existing tests
Vue target modules
Temporary bridge capabilities
Legacy files to delete
Rollback expectations
Known non-goals
```

## 3. Parity Matrix

Каждый пользовательский сценарий получает одну строку:

| Сценарий | Legacy baseline | Vue implementation | Component test | E2E | Accessibility | Offline | Status |
|---|---|---|---|---|---|---|---|
| Создание | ссылка/описание | компонент/route | test id | spec | keyboard/screen reader | yes/no | planned/done |

Нельзя считать домен перенесённым по внешнему сходству. Нужны доказательства поведения.

## 4. Definition of Done доменного Vue-релиза

### Архитектура

- [ ] У домена один UI-владелец.
- [ ] Backend invariants не продублированы во frontend.
- [ ] API идёт через typed client.
- [ ] UI-state локален или имеет документированную причину быть глобальным.
- [ ] Нет прямых обращений к legacy DOM/state.

### Поведение

- [ ] Все legacy user journeys перечислены и подтверждены.
- [ ] Loading, empty, error, retry и permission states реализованы.
- [ ] Double-submit предотвращён.
- [ ] `409 Conflict` и stale edit имеют понятное поведение, если домен редактируемый.
- [ ] Mobile, keyboard и reduced-motion сценарии проверены.

### Тесты

- [ ] `vue-tsc` проходит.
- [ ] Vitest покрывает components/composables/stores с бизнес-значимой логикой.
- [ ] Playwright проверяет публичный пользовательский путь, а не hidden implementation selectors.
- [ ] Static architecture contracts проверяют границы, а не конкретные строки реализации.
- [ ] Upgrade с предыдущего release image проверен.

### Удаление legacy

- [ ] Старые render/functions/listeners домена удалены.
- [ ] Неиспользуемые HTML nodes и CSS удалены.
- [ ] Временные bridge capabilities удалены или явно остаются с причиной и сроком.
- [ ] Старые E2E helpers/selectors удалены.
- [ ] Release guard запрещает возврат удалённого legacy owner.

### Release safety

- [ ] Clean checkout воспроизводимо собирается через lockfile и `npm ci`.
- [ ] Patch tree, ZIP tree и release tree совпадают.
- [ ] Previous image rollback проверен на данных, записанных новой версией, либо документировано ограничение.
- [ ] PWA/service worker не смешивает несовместимые shell/assets.

## 5. Разрешённые временные мосты

Bridge допустим только когда:

- Vue shell должен открыть ещё не мигрированный legacy domain;
- legacy domain должен опубликовать immutable snapshot;
- capability имеет typed name, минимальный payload и regression test;
- в manifest указан релиз удаления.

Запрещены bridge-методы вида:

```text
execute(code)
getState()
query(selector)
call(functionName, args)
```

## 6. Структура Vue-домена

Рекомендуемый минимум:

```text
frontend/src/features/<domain>/
├── api/
├── components/
├── composables/
├── pages/
├── stores/
├── types/
└── tests/
```

Большой файл не считается архитектурой. Ориентир: компоненты разделяются по ответственности, а не по произвольному лимиту строк.

## 7. Release review

В выдаче каждого доменного релиза обязательно указывать:

```text
Migrated
Parity verified
Legacy removed
Bridge removed/remaining
OpenAPI changes
PWA impact
Rollback impact
Known limitations
```

## 8. Финальное завершение в v27.40.0

Миграция завершена только когда:

- [ ] Vue владеет всеми пользовательскими экранами.
- [ ] Vue Router является единственным UI router.
- [ ] Numbered legacy JavaScript удалён.
- [ ] Legacy global state и modal managers удалены.
- [ ] Temporary bridge удалён.
- [ ] E2E не нажимает hidden legacy chrome.
- [ ] Implementation-string contracts заменены behavior/architecture tests.
- [ ] Bundle, PWA, accessibility и performance gates обязательны в CI.
- [ ] Production topology остаётся one-image / one-app-container.
