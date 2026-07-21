# v27.3.1 — Persistent session and editor modals

## Browser session

DutyLog keeps the explicit `DUTYLOG_REMEMBER_ME` cookie for 30 days. The previous Spring Security persistent-token implementation rotated the token on every automatic login. A restored PWA starts several API requests in parallel; the first request rotated the token and the remaining requests looked like token theft, which removed the remembered login.

`StablePersistentRememberMeServices` keeps the random series/token pair stable until its fixed expiration time. Cookies remain HTTPS-only, HttpOnly and SameSite=Lax. Password changes, role changes and explicit logout keep their existing revocation behavior.

## Task editor

Task editing now uses one modal form with text, task date, category, priority, due date, due time and reminder settings. No browser `prompt()`/`confirm()` chain is used.

## Shift-type manager

The `+` chip in the selected-day shift row opens a dedicated manager. The manager lists built-in and custom shift types and edits name/color where allowed, calendar hours, start/end, break, planned hours and notification settings. The former Shift types card and navigation entry were removed from Settings.

## Regression coverage

- same remember-me cookie accepted by multiple restored requests;
- static contract rejects task and shift-type prompt chains;
- Playwright creates and edits a task through the modal and creates a shift through the manager.
