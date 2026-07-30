# DutyLog v27.21.2 — Schedule Accordion E2E Selector Hotfix

## Причина

После зелёного Maven gate полный Playwright run выполнил 29 из 30 сценариев. Единственное падение возникло в `schedule-templates-calendar-layers.spec.js`: общий helper искал `[data-day-module="shifts"]`, а интерфейс содержит две валидные секции одного модуля — `#accShift` и `#accSched`. Playwright strict mode корректно отказался выбирать одну из двух поверхностей автоматически.

## Исправление

Добавлен точный helper:

```js
openDayModuleById(page, id)
```

Он:

- требует ровно один элемент с указанным ID;
- требует его видимость;
- открывает непосредственный `summary`;
- подтверждает атрибут `open`.

Schedule Templates E2E теперь использует:

```js
await openDayModuleById(page, 'accSched');
```

Общий `openDayModule(page, moduleKey)` не ослаблен и не использует `.first()`, поэтому будущие дубли module-key по-прежнему обнаруживаются как контрактная неоднозначность.

## Совместимость

- Production runtime не меняется.
- HTTP API не меняется.
- База данных не меняется.
- Новая миграция не требуется.
- Flyway остаётся V39.
- Baseline остаётся 100 Java test classes / 525 `@Test` methods / 30 Playwright scenarios.
