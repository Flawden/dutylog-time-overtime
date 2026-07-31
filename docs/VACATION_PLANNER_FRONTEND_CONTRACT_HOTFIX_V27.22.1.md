# v27.22.1 — Vacation Planner Frontend Contract Hotfix

## Причина

Полный Maven gate v27.22.0 успешно скомпилировал 134 production sources и 103 test sources, затем остановился на трёх статических ожиданиях:

1. UI Core contract всё ещё ожидал навигацию Shift Worker до появления Vacation Planner.
2. Vacation frontend contract искал вымышленный token `vacation-day`, которого нет в принятом runtime.
3. Module service contract жёстко ожидал семь переключаемых non-admin модулей, хотя `vacation` стал восьмым.

Новые service/controller-тесты Vacation Planner прошли. Ошибки не затрагивают расчёт дней, лимиты, ownership, API, V40 или календарные данные.

## Исправление

- Shift Worker contract проверяет `navigation:["today","calendar","vacation","overtime","settings"]`.
- Today contract проверяет фактический порядок `shift / overtime / tasks / important`.
- Month/Week/Day composition проверяется по реальному пути `facts.absences` → `type:"vacation"` → `editAbsenceFromOccurrence`.
- Ожидаемое число persisted switchable modules вычисляется из `DutyLogModules.ALL` и больше не требует ручного обновления при добавлении модуля.

## Совместимость

- Production runtime behavior: без изменений.
- HTTP API / OpenAPI: без изменений.
- Database schema: без изменений.
- Flyway: V40.
- Baseline: 103 Java test classes / 544 `@Test` methods / 31 Playwright scenarios.
