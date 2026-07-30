# DutyLog v27.21.1 — Schedule Templates Frontend Contract Alignment Hotfix

## Причина

Полный Maven gate v27.21.0 скомпилировал 126 production-классов и 100 test-классов, после чего выполнил 525 тестов. Серверные тесты шаблонов графика и календарных слоёв прошли, но четыре статические frontend-проверки остались привязаны к архитектуре до v27.21.0:

- `CalendarMonthReloadContractTest` искал прямой `api.month(...)` внутри `30-calendar.js`, хотя fresh-reload теперь проходит через `loadMonth()` → `dataLayer.loadCalendar()` → `api.month()`;
- `ScheduleTemplateFrontendContractTest` ожидал browser-side rotation и legacy `/api/days/fill` вместо authoritative preview/apply;
- `ScheduleTemplatesCalendarLayersFrontendContractTest` искал property-синтаксис API вместо async methods;
- тот же контракт ожидал несуществующий `schedulePreview`, хотя runtime, HTML и Playwright используют `tplPreview`.

## Исправление

### Authoritative fresh reload

Контракт проверяет всю фактическую цепочку:

```text
applyScheduleTemplate
→ loadMonth({ fresh:true })
→ dataLayer.loadCalendar(..., { fresh:true })
→ api.month(..., { fresh:true })
→ HTTP cache: no-store
```

Generation guard, month identity, render-after-cache-hit и IndexedDB metadata остаются защищены.

### Server-owned schedule semantics

Frontend-контракт теперь требует:

- `previewScheduleTemplate` и `applyScheduleTemplate`;
- диапазон `startDate/endDate`;
- явный `anchorDate`;
- явный `overwriteExistingShift`;
- обязательный preview перед apply.

Выравнивание `WEEKDAY`, конфликтные действия и безопасная запись остаются authoritative server behavior и покрываются service/controller тестами.

### Реальные API methods и DOM IDs

Проверки синхронизированы с runtime-контрактом:

```text
async scheduleTemplates()
async calendarLayers()
id="tplPreview"
```

## Совместимость

- Production business logic не меняется.
- HTTP API не меняется.
- JavaScript runtime не меняется.
- База данных не меняется.
- Новая миграция не требуется.
- Flyway остаётся V39.
- Baseline остаётся 100 Java test classes / 525 `@Test` methods / 30 Playwright scenarios.
