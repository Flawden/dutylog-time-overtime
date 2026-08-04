# ADR-004 — Incremental strangler migration and bounded bridge rules

- Status: accepted
- Date: 2026-08-04
- Release: v27.35.0

## Decision

Domains migrate one at a time. A temporary bridge is allowed only for a named typed capability, minimal immutable payload, regression test and explicit removal release in the migration manifest. Generic `execute`, `getState`, `query` and arbitrary function-call bridges are forbidden.

## Consequences

After parity, legacy renderers, listeners, DOM, CSS, selectors and bridge capability are removed in the same domain release. Two UI owners are not an accepted steady state.
