# v27.17.3 — Java Contract Build Gate Hotfix

## Причина

`v27.17.2` прошёл быстрые shell/JavaScript проверки, но Maven остановился на `testCompile` из-за незакрытого строкового литерала в `CalendarMobileExperienceFrontendContractTest`.

Проблемная строка пыталась проверить JavaScript-фрагмент:

```javascript
[range, event.meta].filter(Boolean).join(" · ")
```

но закрывающая кавычка внутри Java-строки не была экранирована.

## Исправление

- Java-строка исправлена;
- продуктовый timeline-код `v27.17.2` не изменён;
- `release-check.sh` теперь создаёт минимальные локальные JUnit stubs и запускает `javac` для всех статических `*FrontendContractTest.java`;
- malformed Java source теперь блокирует локальный release gate до создания patch/ZIP.

## Границы

- API не меняется;
- база данных не меняется;
- Flyway остаётся V36;
- frontend-поведение не меняется;
- regression baseline остаётся 94 Java test classes / 492 `@Test` methods / 25 Playwright scenarios.
