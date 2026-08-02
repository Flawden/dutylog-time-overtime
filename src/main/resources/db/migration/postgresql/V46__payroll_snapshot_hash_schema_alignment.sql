-- DutyLog v27.28.3 Payroll Snapshot Hash Schema Validation Hotfix.
-- V45 is immutable. Align PostgreSQL with the existing JPA VARCHAR(64) mapping.
-- PostgreSQL preserves the existing NOT NULL and ck_payroll_snapshot_hash constraints.

ALTER TABLE payroll_snapshots
    ALTER COLUMN calculation_hash TYPE VARCHAR(64)
    USING BTRIM(calculation_hash);
