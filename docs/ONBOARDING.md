# First-run onboarding

Status: v27.2.1.

DutyLog can be feature-rich, so new users should not land directly in the full interface.
The first-run onboarding lets a user choose the modules they need right now.

## What it does

- Shows a first-run overlay after login when `users.onboarding_completed = false`.
- Lets the user pick module presets:
  - minimum;
  - work + overtime;
  - enable all.
- Lets the user fine-tune individual optional modules.
- Saves module settings through `PATCH /api/modules`.
- Marks onboarding as complete through `PUT /api/profile` with `onboardingCompleted: true`.

## What it does not do

- It does not delete data.
- It does not create a separate app mode.
- It does not change roles, tariff tiers, security rules or server-side module guards.
- Existing users are marked as completed by migration so upgrades do not interrupt them.

## Database

Migration:

```sql
V21__user_onboarding.sql
```

Adds:

```sql
users.onboarding_completed BOOLEAN NOT NULL DEFAULT TRUE
```

JPA explicitly stores `false` for newly registered users until they finish or skip onboarding.

## UX principle

Onboarding controls perceived complexity. Modules remain the long-term source of truth.
A skipped onboarding keeps current/default module settings; a completed onboarding applies the selected module set.
