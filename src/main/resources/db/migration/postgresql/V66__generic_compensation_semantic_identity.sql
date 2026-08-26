-- DutyLog v27.48.0 Generic Compensation Semantic Identity Boundary.
--
-- Nullable machine-owned semantic provenance only.
--
-- Historical policy:
-- - NO backfill;
-- - existing rows remain NULL / UNCLASSIFIED;
-- - display_name is never interpreted;
-- - calculation type/base are never interpreted;
-- - UNCLASSIFIED exists only at the API boundary and is persisted as NULL.
--
-- The snapshot column is reserved now so the following plumbing slice can
-- freeze semantic provenance without another schema transition.
--
-- This migration changes no Payroll money.

ALTER TABLE compensation_component_versions
    ADD COLUMN earning_kind VARCHAR(40);

ALTER TABLE compensation_component_versions
    ADD CONSTRAINT ck_compensation_component_version_earning_kind
        CHECK (
            earning_kind IS NULL
            OR earning_kind IN (
                'HARMFUL_CONDITIONS',
                'COMBINATION',
                'MONTHLY_BONUS',
                'ONE_TIME_BONUS',
                'REGIONAL_COEFFICIENT'
            )
        );

ALTER TABLE payroll_snapshot_compensation_component_lines
    ADD COLUMN earning_kind VARCHAR(40);

ALTER TABLE payroll_snapshot_compensation_component_lines
    ADD CONSTRAINT ck_payroll_snapshot_component_line_earning_kind
        CHECK (
            earning_kind IS NULL
            OR earning_kind IN (
                'HARMFUL_CONDITIONS',
                'COMBINATION',
                'MONTHLY_BONUS',
                'ONE_TIME_BONUS',
                'REGIONAL_COEFFICIENT'
            )
        );
