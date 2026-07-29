# DutyLog v27.19.1 — Task Board Date Range Compatibility Hotfix

## Problem

v27.19.0 reused `from` / `to` for planned intervals. Those query names had already meant “deadline, or task date when no deadline exists”, so the change broke existing controller/service tests and could break older clients.

## Contract

- `from` / `to` preserve the legacy deadline-or-date filter.
- `scheduledFrom` / `scheduledTo` filter by overlap with the planned all-day, point or interval schedule.
- The pairs are independent and may be combined.
- Web/PWA board date controls use `scheduledFrom` / `scheduledTo` because their product meaning is “planned during this period”.

## Compatibility

Existing `TaskService.listBoard(...)` overloads remain source-compatible and delegate to the expanded query with no planned-range filter. No database migration or mobile payload change is introduced.

## Regression protection

The existing 507-test baseline now verifies both range modes inside the current service/controller/frontend contract methods. Playwright remains at 28 scenarios and Flyway remains V37.
