# v27.29.3 — Custom Workspace Today Widget Order Persistence Hotfix

## Failure boundary

The v27.29.2 browser run reached 37/38. Custom Workspace inherited Overtime and Tasks correctly, but after Tasks was moved above Shift and the profile autosave completed, the server response restored Shift to the first position.

## Root cause

`ProfileController.safeTodayWidgets(...)` always inserted `shift` before every accepted widget. That behavior was safe for missing Shift, but it also destroyed an explicit user order such as `tasks, shift, important`.

## Fix

- preserve the accepted `LinkedHashSet` order when Shift is already present;
- prepend mandatory Shift only when the incoming list omits it;
- keep the existing allowlist and duplicate filtering;
- keep empty-list fallback behavior unchanged;
- prove the order through the real profile HTTP/persistence contract;
- retain the browser DOM-order assertion after autosave.

This is a bounded Workspace Studio profile-persistence hotfix. It does not change the profile JSON shape, PostgreSQL schema, Payroll, Unified Ledger, calendar business logic or Flyway V46.
