# v26.6.1 — UX boot hotfix

Carried into release candidate: v27.2.1.
Цель hotfix — убрать риск, что PWA визуально застрянет на стартовом экране после frontend-полировки v26.6.

Что изменено:

- `index.html` больше не поставляет `<body class="appBooting">` как начальное состояние.
- Boot overlay включается только из `init()` после загрузки JS.
- Добавлен boot failsafe на 15 секунд: если стартовая загрузка зависла, интерфейс разблокируется и пользователь видит ошибку в шапке.
- Добавлены обработчики `error` и `unhandledrejection`, которые снимают boot overlay во время старта.
- `release-check.sh` теперь запрещает возвращать начальный `<body class="appBooting">`.

Это не новая фича, а стабилизационный UX-fix внутри release freeze.
