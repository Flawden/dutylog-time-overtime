# v27.20.2 — Calendar Day Details E2E Flow Hotfix

## Причина

Полный Chromium gate v27.20.1 подтвердил исправление всех трёх дефектов предыдущего запуска и дошёл до 28 из 29 сценариев. Единственное падение оставалось в `notes-important-events-next.spec.js`: после проверки timed-события сценарий сохранял Day mode, а затем пытался открыть Month-only accordion заметок напрямую.

Приложение вело себя корректно. Месячная панель и `data-day-module="notes"` намеренно скрыты в почасовом Day view. Ошибка находилась в маршруте теста.

## Исправление

Добавлен общий Playwright helper `openSelectedDayDetails(page)`:

1. сохраняет текущую focused date;
2. использует реальную кнопку `#calendarDayOpenDetails` — «Все детали дня»;
3. при необходимости сначала открывает Day mode;
4. ждёт возвращения Month mode;
5. ждёт видимую полную панель `#panel`;
6. только после этого разрешает открыть Notes и другие Month-only модули дня.

Новый Notes & Important Events сценарий больше не обращается к скрытой месячной сетке после почасовой проверки события.

## Защита от повторения

- `CalendarMobileExperienceFrontendContractTest` проверяет helper, product-route selector и использование helper в E2E.
- `release-check.sh` защищает те же контракты до Maven/Chromium gate.
- `selectDate()` остаётся mode-aware и идемпотентным; его поведение не откатывается.

## Совместимость

- HTTP API не меняется.
- Production JavaScript календаря не меняется.
- Модель данных не меняется.
- Новая миграция не требуется.
- Flyway остаётся V38.
- Baseline остаётся 97 Java test classes / 513 `@Test` methods / 29 Playwright scenarios.
