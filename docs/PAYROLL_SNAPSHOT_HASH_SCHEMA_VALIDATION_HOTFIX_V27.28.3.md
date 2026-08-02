# v27.28.3 — Payroll Snapshot Hash Schema Validation Hotfix

This is a forward-only schema-validation hotfix.

## Incident

The v27.28.2 pipeline completed Maven, the 37-scenario Playwright suite and image construction, then failed while starting DutyLog against a clean PostgreSQL database:

```text
Schema-validation: wrong column type encountered in column
[calculation_hash] in table [payroll_snapshots];
found [bpchar (Types#CHAR)],
but expecting [varchar(64) (Types#VARCHAR)]
```

V45 created `payroll_snapshots.calculation_hash` as `CHAR(64)`. The existing `PayrollSnapshot` mapping declares a non-null string with `length = 64`, which Hibernate validates as `VARCHAR(64)`.

## Resolution

V45 remains byte-for-byte immutable. V46 performs the only required forward change:

```sql
ALTER TABLE payroll_snapshots
    ALTER COLUMN calculation_hash TYPE VARCHAR(64)
    USING BTRIM(calculation_hash);
```

`BTRIM(...)` safely removes fixed-width padding during conversion. PostgreSQL retains the column's `NOT NULL` state and the existing `ck_payroll_snapshot_hash` constraint, which requires exactly 64 lowercase hexadecimal characters.

## Regression protection

`PayrollSnapshotHashSchemaValidationHotfixTest`:

- pins the SHA-256 of released V45;
- requires V46 to use `VARCHAR(64)` and `BTRIM(...)`;
- rejects dropping the hash constraint or nullability;
- keeps the entity mapping at `nullable = false, length = 64`;
- rejects a dialect-specific `CHAR(64)` override in Java.

The release gate protects the same contract before packaging. GitHub Actions remains the authority for the real clean PostgreSQL migration smoke and Hibernate `ddl-auto=validate` startup.

## Deliberately unchanged

- Payroll calculations and rounding;
- settings, adjustments and immutable snapshot revisions;
- API and OpenAPI;
- Payroll workspace and browser behavior;
- approval workflow and Unified Ledger;
- V45 contents and checksum;
- all 37 Playwright scenarios.

Flyway advances to V46. The regression baseline advances to 118 Java test classes / 605 `@Test` methods / 37 Playwright scenarios.
