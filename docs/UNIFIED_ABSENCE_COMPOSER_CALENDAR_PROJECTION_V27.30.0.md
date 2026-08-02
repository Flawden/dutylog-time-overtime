# v27.30.0 — Unified Absence Composer & Calendar Projection

## Product outcome

DutyLog now has one absence flow and treats vacation, overtime-backed time off, sick leave, unpaid leave and custom absence categories as one user action: **create an absence**. The reason, full/partial coverage and compensation source live in one composer, while the existing plan remains intact underneath the factual calendar layer.

## One composer, several entry points

The same `#vacationPeriodForm` is reused from:

- Vacation & Absences;
- the global quick-add menu;
- the selected-day overtime action;
- the overtime ledger add-usage action;
- the calendar's selected-date absence action.

The global Quick Add opens the neutral type chooser, while entry from Overtime preselects `TIME_OFF / OVERTIME_BANK`. The form is moved into `#absenceComposerModal` only for modal entry points and restored to the Vacation workspace afterwards. No duplicate form, API or business implementation is introduced.

## Dynamic balance contract

The selected type determines its compensation source:

- Vacation → `VACATION_ALLOWANCE`;
- Time off → `OVERTIME_BANK`;
- Sick leave → `SICK_PAY`;
- Unpaid leave → `UNPAID`;
- Other → `NONE` unless a compatible policy is selected.

The composer displays the relevant available balance and projected remainder. Vacation cannot exceed the work-year allowance and overtime-backed time off cannot exceed the canonical FIFO bank. Sick and unpaid leave explicitly show that no consumable balance is used.

## Calendar projection

- Full-day absence becomes the factual visual state and keeps the planned shift as a muted `По графику` projection.
- Partial absence leaves the planned shift visible and adds a typed time strip.
- Type glyphs and colors make vacation, time off, sick leave, unpaid leave and custom categories distinguishable without relying on color alone.
- Planned absence uses a dashed treatment; approved/completed facts use the normal solid treatment.

## Compatibility

Existing direct overtime usages remain editable through the legacy usage editor. New user-facing time-off creation is routed through the absence composer and creates the linked FIFO usage through the existing service boundary.

No database migration is required. Flyway remains V46. Payroll, ledger posting, approval workflow, API payloads and the plan/fact persistence model remain unchanged.

## Regression contract

- `UnifiedAbsenceComposerFrontendContractTest` protects one-form reuse, balance routing and calendar projection.
- `unified-absence-composer.spec.js` creates a partial overtime-backed absence and a full-day sick leave through real UI/API paths.
- `overtime-editor-modals.spec.js` now proves that new usage entry points create a linked absence instead of a detached raw usage.

Baseline: 123 Java test classes / 616 `@Test` methods / 39 Chromium Playwright scenarios.
