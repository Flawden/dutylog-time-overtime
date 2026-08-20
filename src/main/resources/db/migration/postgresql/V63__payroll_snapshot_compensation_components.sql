-- DutyLog v27.47.0 Generic Compensation Component Payroll Snapshot Foundation.
--
-- V62 introduced mutable/effective-dated component configuration.
-- V63 introduces only immutable Payroll revision storage.
--
-- Historical snapshots predate generic component money and are backfilled
-- with a neutral aggregate and zero child lines.
--
-- Child rows copy component/version ids as scalar provenance intentionally:
-- there are no foreign keys back to mutable component configuration.

ALTER TABLE payroll_snapshots
    ADD COLUMN compensation_component_count INTEGER,
    ADD COLUMN compensation_component_earnings_minor BIGINT,
    ADD COLUMN compensation_component_fingerprint VARCHAR(64);

UPDATE payroll_snapshots
SET compensation_component_count = 0,
    compensation_component_earnings_minor = 0,
    compensation_component_fingerprint = NULL;

ALTER TABLE payroll_snapshots
    ALTER COLUMN compensation_component_count SET NOT NULL,
    ALTER COLUMN compensation_component_earnings_minor SET NOT NULL,

    ADD CONSTRAINT ck_payroll_snapshot_compensation_component_values
        CHECK (
            compensation_component_count >= 0
            AND compensation_component_earnings_minor >= 0
        ),

    ADD CONSTRAINT ck_payroll_snapshot_compensation_component_identity
        CHECK (
            (
                compensation_component_count = 0
                AND compensation_component_earnings_minor = 0
                AND compensation_component_fingerprint IS NULL
            )
            OR
            (
                compensation_component_count > 0
                AND compensation_component_fingerprint
                    ~ '^[0-9a-f]{64}$'
            )
        );

CREATE TABLE payroll_snapshot_compensation_component_lines (
    id BIGSERIAL PRIMARY KEY,

    snapshot_id BIGINT NOT NULL
        REFERENCES payroll_snapshots(id)
        ON DELETE CASCADE,

    line_index INTEGER NOT NULL,

    -- Frozen scalar provenance. Deliberately no FK to mutable configuration.
    component_id BIGINT NOT NULL,
    version_id BIGINT NOT NULL,

    effective_from DATE NOT NULL,
    display_name VARCHAR(120) NOT NULL,

    calculation_type VARCHAR(24) NOT NULL,
    calculation_base VARCHAR(32),
    rate_bps INTEGER,

    configured_amount_minor BIGINT,
    configured_currency_code VARCHAR(3),

    reference_base_minor BIGINT NOT NULL,
    amount_minor BIGINT NOT NULL,

    CONSTRAINT uq_payroll_snapshot_component_line_order
        UNIQUE (snapshot_id, line_index),

    CONSTRAINT uq_payroll_snapshot_component_identity
        UNIQUE (snapshot_id, component_id),

    CONSTRAINT ck_payroll_snapshot_component_line_identity
        CHECK (
            line_index >= 0
            AND component_id > 0
            AND version_id > 0
            AND effective_from =
                date_trunc('month', effective_from)::date
            AND char_length(btrim(display_name))
                BETWEEN 1 AND 120
        ),

    CONSTRAINT ck_payroll_snapshot_component_line_money
        CHECK (
            reference_base_minor >= 0
            AND amount_minor >= 0
        ),

    CONSTRAINT ck_payroll_snapshot_component_line_type
        CHECK (
            calculation_type IN (
                'FIXED_AMOUNT',
                'PERCENT_OF_BASE'
            )
        ),

    CONSTRAINT ck_payroll_snapshot_component_line_shape
        CHECK (
            (
                calculation_type = 'FIXED_AMOUNT'
                AND calculation_base IS NULL
                AND rate_bps IS NULL
                AND configured_amount_minor
                    BETWEEN 1 AND 1000000000000
                AND configured_currency_code
                    ~ '^[A-Z]{3}$'
                AND reference_base_minor = 0
                AND amount_minor = configured_amount_minor
            )
            OR
            (
                calculation_type = 'PERCENT_OF_BASE'
                AND calculation_base IN (
                    'NOMINAL_SALARY',
                    'EARNED_BASE_PAY'
                )
                AND rate_bps BETWEEN 1 AND 10000000
                AND configured_amount_minor IS NULL
                AND configured_currency_code IS NULL
            )
        )
);

CREATE INDEX idx_payroll_snapshot_component_lines_snapshot
    ON payroll_snapshot_compensation_component_lines(
        snapshot_id,
        line_index
    );
