# v26.6.2 — frontend runtime hotfix

Carried into release candidate: v27.1.0.
Цель hotfix — исправить runtime-регрессию после v26.6/v26.6.1, когда split JS падал на старте и интерфейс оставался без календаря и рабочих вкладок.

## Что произошло

`applyAppearance(loadLocalAppearance())` вызывался до объявления shared-хелперов `$` и `esc()`. В результате браузер получал:

- `ReferenceError: esc is not defined` в `10-core.js`;
- каскадные `ReferenceError: $ is not defined` в последующих split-файлах;
- календарь и вкладки не инициализировались.

Отдельно service worker пытался кэшировать запросы расширений браузера, что давало шумную ошибку `Request scheme 'chrome-extension' is unsupported`.

## Исправление

- `$` и `esc()` перенесены в начало `10-core.js`;
- позднее `const $ = ...` удалено, чтобы не было temporal dead zone на старте;
- локальное дублирование `esc()` в `30-calendar.js` удалено;
- service worker игнорирует не-same-origin и не-HTTP(S) запросы;
- cache writes для runtime assets стали non-fatal;
- `release-check.sh` теперь проверяет порядок boot-хелперов и защиту service worker от unsupported schemes.

## Ожидаемое поведение

После Ctrl+F5 или очистки service worker календарь должен рендериться, а вкладки должны переключаться без ошибок `esc is not defined` и `$ is not defined`.
