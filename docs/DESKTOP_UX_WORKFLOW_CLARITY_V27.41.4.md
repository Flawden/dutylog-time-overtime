# Desktop UX & Workflow Clarity — v27.41.4

Base: proven-green v27.41.3 commit `63ca2b91b8f06319401b50a72769998d0a50a9da`, tree `ba0abb256c71e48d44170b334bceffd81d44eb57`.

## Goal

Close the desktop presentation/workflow gaps exposed by manual staging review without creating new business authorities. The green v27.41.3 Calendar/Today/overtime runtime stays canonical.

## Changes

- Calendar month/week/day surfaces are centered inside a 1680 px wide desktop boundary; standard Payroll and Settings workspaces follow the existing shell max width.
- Absence history defaults to relevant-first ordering (current, upcoming, older active, then recent history) and adds explicit type + newest/oldest controls on top of the existing status/search filter.
- Ledger-integrity groups expose concrete source records, context, affected time and a direct navigation action when the current source is resolvable. No record is repaired automatically.
- FIFO allocation rows use explicit date / interval / amount columns and stack safely on small screens.
- Notes Focus Mode teleports the same Vue editor to the viewport; draft identity and debounce/offline ownership stay unchanged.
- Selected-day overtime wording now says `Оформить отгул`. That action continues to open the canonical Absence composer; DutyLog owns reservation/posting through FIFO. It is not a second manual debit path.
- Canonical time-off workflow: **Absence → overtime bank → FIFO**. Manual bank correction remains a separate accounting/admin concept, not a competing user path for time off.

## Boundaries

No OpenAPI/Flyway change, no new dependency, no auth/session/CSRF change, no new offline store or executor, no accounting formula/FIFO mutation, and no People Profiles/shared-schedule data path.

## Acceptance

Exact CI remains authoritative: Node 20.18.1/npm 10.8.2 frontend gate, Java 17 Maven, strict Chromium 48/48, immutable image, PostgreSQL/Flyway V47 smoke and staging.
