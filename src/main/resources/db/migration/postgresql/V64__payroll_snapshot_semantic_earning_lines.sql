-- DutyLog v27.48.0 / 8A3D1 Historical Semantic Earnings Foundation.
--
-- Frozen machine-owned earning semantics below one immutable Payroll snapshot.
--
-- Existing snapshots are intentionally NOT backfilled:
-- reconstructing semantic kinds from display names, gross totals or generic
-- aggregates would fabricate historical payroll truth.
--
-- posting/accounting period belongs to payroll_snapshots.period_month.
-- earning_period_* and coverage_* are separate optional business concepts.

CREATE TABLE payroll_snapshot_earning_lines (
    id BIGSERIAL PRIMARY KEY,

    snapshot_id BIGINT NOT NULL
        REFERENCES payroll_snapshots(id)
        ON DELETE CASCADE,

    line_index INTEGER NOT NULL,

    earning_kind VARCHAR(48) NOT NULL,
    earning_phase VARCHAR(48) NOT NULL,

    amount_minor BIGINT NOT NULL,

    qualified_quantity_value BIGINT,
    qualified_quantity_unit VARCHAR(32),

    earning_period_from DATE,
    earning_period_to DATE,

    coverage_from DATE,
    coverage_to DATE,

    CONSTRAINT uq_payroll_snapshot_earning_line_order
        UNIQUE (
            snapshot_id,
            line_index
        ),

    CONSTRAINT ck_payroll_snapshot_earning_line_index
        CHECK (
            line_index >= 0
        ),

    CONSTRAINT ck_payroll_snapshot_earning_line_amount
        CHECK (
            amount_minor >= 0
        ),

    CONSTRAINT ck_payroll_snapshot_earning_kind
        CHECK (
            earning_kind IN (
                'BASE_PAY',
                'HOLIDAY_PAY',
                'NIGHT_PREMIUM',
                'HARMFUL_CONDITIONS',
                'COMBINATION',
                'MONTHLY_BONUS',
                'ONE_TIME_BONUS',
                'REGIONAL_COEFFICIENT',
                'MEDICAL_COMPENSATION',
                'VACATION_PAY'
            )
        ),

    CONSTRAINT ck_payroll_snapshot_earning_phase
        CHECK (
            earning_phase IN (
                'BASE_PAY',
                'TIME_PREMIUM',
                'WORK_ALLOWANCE',
                'EXTERNAL_EPISODIC_ALLOWANCE',
                'PERFORMANCE_BONUS',
                'GROSS_COEFFICIENT',
                'OTHER_EARNING'
            )
        ),

    CONSTRAINT ck_payroll_snapshot_earning_quantity_shape
        CHECK (
            (
                qualified_quantity_value IS NULL
                AND qualified_quantity_unit IS NULL
            )
            OR
            (
                qualified_quantity_value IS NOT NULL
                AND qualified_quantity_value >= 0
                AND qualified_quantity_unit IN (
                    'MINUTES',
                    'CALENDAR_DAYS'
                )
            )
        ),

    CONSTRAINT ck_payroll_snapshot_earning_period_shape
        CHECK (
            (
                earning_period_from IS NULL
                AND earning_period_to IS NULL
            )
            OR
            (
                earning_period_from IS NOT NULL
                AND earning_period_to IS NOT NULL
                AND earning_period_to >= earning_period_from
            )
        ),

    CONSTRAINT ck_payroll_snapshot_earning_coverage_shape
        CHECK (
            (
                coverage_from IS NULL
                AND coverage_to IS NULL
            )
            OR
            (
                coverage_from IS NOT NULL
                AND coverage_to IS NOT NULL
                AND coverage_to >= coverage_from
            )
        )
);

CREATE INDEX idx_payroll_snapshot_earning_lines_snapshot
    ON payroll_snapshot_earning_lines(
        snapshot_id,
        line_index
    );

-- Completeness/integrity manifest for the frozen semantic earning set.
--
-- Existing Payroll snapshots intentionally receive NO manifest row.
-- Absence means semantic completeness is unknown and historical average
-- calculations must fail closed.
--
-- complete=false explicitly represents a known partial freeze.
-- complete=true means line_count / amount_minor / fingerprint describe the
-- complete semantic earning set, including the valid zero-line case.

CREATE TABLE payroll_snapshot_earning_manifests (
    snapshot_id BIGINT PRIMARY KEY
        REFERENCES payroll_snapshots(id)
        ON DELETE CASCADE,

    complete BOOLEAN NOT NULL,

    line_count INTEGER NOT NULL,

    amount_minor BIGINT NOT NULL,

    fingerprint VARCHAR(64) NOT NULL,

    CONSTRAINT ck_payroll_snapshot_earning_manifest_line_count
        CHECK (
            line_count >= 0
        ),

    CONSTRAINT ck_payroll_snapshot_earning_manifest_amount
        CHECK (
            amount_minor >= 0
        ),

    CONSTRAINT ck_payroll_snapshot_earning_manifest_fingerprint
        CHECK (
            fingerprint ~ '^[0-9a-f]{64}$'
        )
);
