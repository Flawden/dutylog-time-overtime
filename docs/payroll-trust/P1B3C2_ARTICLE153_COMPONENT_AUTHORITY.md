# P1B3C2 — Article 153 remuneration-system component authority

## Contract

`Article153ComponentAuthorityService` classifies the **configured** effective
compensation-component set for a persisted worker and a source work date.
It reuses `CompensationComponentResolverService`: one version per stable
component, effective from the first day of its month, never a future version.
Ownership is derived through the existing version/component relationship;
there is no parallel organization or compensation model.

For every enabled version a reviewer must supply an explicit INCLUDE, EXCLUDE,
or UNCLASSIFIED decision, source kind/reference/revision, and classification
basis (including the applicable clause). A reviewed source must cover that
whole effective version. Conditional/day-specific applicability which cannot
be established under this contract must remain UNCLASSIFIED. No monetary
multiplier, proration or allocation rule is implied by INCLUDE.

Source kinds follow the existing C1 local remuneration-source vocabulary:
COLLECTIVE_AGREEMENT, LOCAL_NORMATIVE_ACT, EMPLOYMENT_CONTRACT. These identifiers
record supplied evidence; the application does not authenticate documents or
prove a local exclusion lawful merely because a reference is non-empty.
EXCLUDE must have its own reviewed basis and cannot override mandatory law.

A missing authority, explicit UNCLASSIFIED, or changed certified fingerprint
blocks the entire resolution. Blocked results expose no partial component list.
Ready results retain both INCLUDE and EXCLUDE facts with typed formula values,
source revisions, owner/component/version identity and certification time.
The facts are immutable value records; final payroll snapshot persistence is C3.

The generic earningKind may be null: its meaning is separate from the explicit
Article 153 decision. No keyword or display-name inference is performed. The
nullable earningKind is itself fingerprinted, so later semantic classification
requires a fresh effective version and source review.

## Fingerprint and history

SHA-256 schema `article153-component-v1` covers worker id, stable component id,
version id, effective month, earningKind, calculation type/base, rate, amount,
currency and enabled state. The canonical tuple has fixed positions and explicit
null markers. Names and updatedAt are presentation/audit metadata, not formulas.

Certifying the same version and identical evidence is idempotent. Changing any
reviewed evidence or decision is rejected. Entity fields are non-updatable and
Hibernate-immutable; V85 enforces one authority per version. Certification locks
the owner-scoped version row so simultaneous certifications serialize. These are
application/relational protections, not protection against an administrator
issuing direct SQL. No HTTP endpoint is exposed by this stage.

An uncertified disabled version is not active. Editing a certified enabled
version into a disabled version changes its fingerprint and blocks. Correct
termination is a new disabled effective version, preserving earlier history.
New versions never inherit an earlier decision automatically. Source changes
within an existing month have no correction workflow in C2; keep resolution
blocked until an explicit historical correction design is implemented.

An empty configured set resolves to an empty list. This proves only that there
are no configured effective components, not that the employer supplied its full
remuneration system. C3/activation must not treat this local gate as proof of
external source completeness, event qualification, monetary applicability or
all other Article 153 authorities.

## Source review boundary

Checked during implementation on 2026-10-01 against the Constitutional Court's
published official 2018 and 2019 summaries (search-index excerpts; PDF retrieval
was unavailable in this execution environment):

- https://www.ksrf.ru/Decision/generalization/documents/Information_2018.pdf
  — summary of 28.06.2018 No. 26-P: the relevant remuneration-system payments
  participate alongside tariff pay under the conditions described by the Court.
- https://www.ksrf.ru/about/Maintenance/Documents/Report_2019.pdf
  — explanation that the legal position is general across employee categories.

These sources support requiring explicit remuneration evidence. This stage does
not hardcode a legal inclusion/exclusion list or extend the ruling into a new
money formula. Current primary-source and applicability review is still required
before final pricing activation, especially conditional components and rest-day
or norm branches. Existing statutory policy is unchanged.

## Migration and tests

V85 adds only article153_component_authorities; there is no historical backfill.
The foreign key restricts deletion of a certified version. Existing user-facing
configuration does not delete stable components or their history.

Tests cover H2/JPA persistence, owner isolation, historical month selection,
INCLUDE/EXCLUDE/UNCLASSIFIED, all source kinds, source validation, missing evidence,
all formula drift dimensions, renaming, idempotency, immutable recertification,
new disabled versions, no partial results and migration scope. Actual PostgreSQL
DDL execution is covered by the existing staging clean-database image smoke gate.
The local H2 tests do not execute the PostgreSQL migration.

## Stage boundary

HOLIDAY_PAY remains disabled. PayrollService wiring NONE. No current payroll
money, OpenAPI, ordinary pay, overtime bank, statutory floor or election behavior
is changed. P1B3C3 must freeze the authority set and all remaining Article 153
inputs before final activation. C2 is not GREEN until targeted/full Maven,
release-check, coverage, exact commit/push and exact-head staging evidence pass.

C2 source baseline: 2557 `@Test` methods / 367 test classes. The 49 new
executed cases include parameterized invocations; executed counts are recorded
from Surefire, not inferred from the source-method count.
