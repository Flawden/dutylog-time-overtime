# ADR-003 — Spring Boot as business source of truth

- Status: accepted
- Date: 2026-08-04
- Release: v27.35.0

## Decision

Spring Boot owns validation, authorization, persistence, FIFO allocation, absence/ledger invariants, approvals, closed periods and payroll calculations. Vue owns presentation, interaction, request orchestration and local UI state.

## Consequences

Frontend previews call typed APIs rather than reimplementing server rules. Server responses are not copied into competing global stores.
