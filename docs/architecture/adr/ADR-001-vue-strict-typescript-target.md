# ADR-001 — Vue 3 + strict TypeScript as the only frontend target

- Status: accepted
- Date: 2026-08-04
- Release: v27.35.0

## Context

The ordered legacy JavaScript frontend has accumulated manual DOM ownership, listener lifecycle and string contracts. Parallel frontend targets would preserve that cost.

## Decision

Vue 3 with strict TypeScript, Vite, Pinia, Vue Router and Vitest is the only target for new and migrated UI. `strict`, `noUncheckedIndexedAccess` and `exactOptionalPropertyTypes` remain enabled. New product features are not added to legacy UI.

## Consequences

Domain releases must prove parity and delete their legacy owner. Framework migration does not move backend business rules into Vue and does not justify giant stores or components.
