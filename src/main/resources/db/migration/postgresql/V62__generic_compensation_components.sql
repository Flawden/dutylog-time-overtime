-- DutyLog v27.47.0 Generic Compensation Components foundation.
--
-- A component has stable owner-scoped identity.
-- Mutable business meaning lives in effective-month versions so renames,
-- percentage changes, disabling and future presets never rewrite history.
--
-- This migration introduces configuration only.
-- It does not change Payroll money or historical snapshots.

CREATE TABLE compensation_components (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_compensation_components_owner
    ON compensation_components(user_id, id);

CREATE TABLE compensation_component_versions (
    id BIGSERIAL PRIMARY KEY,
    component_id BIGINT NOT NULL
        REFERENCES compensation_components(id) ON DELETE CASCADE,
    effective_from DATE NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    calculation_type VARCHAR(24) NOT NULL,
    calculation_base VARCHAR(32),
    rate_bps INTEGER,
    amount_minor BIGINT,
    currency_code VARCHAR(3),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_compensation_component_version
        UNIQUE (component_id, effective_from),

    CONSTRAINT ck_compensation_component_version_month
        CHECK (
            effective_from =
            date_trunc('month', effective_from)::date
        ),

    CONSTRAINT ck_compensation_component_display_name
        CHECK (
            char_length(btrim(display_name))
            BETWEEN 1 AND 120
        ),

    CONSTRAINT ck_compensation_component_calculation_type
        CHECK (
            calculation_type IN (
                'FIXED_AMOUNT',
                'PERCENT_OF_BASE'
            )
        ),

    CONSTRAINT ck_compensation_component_calculation_base
        CHECK (
            calculation_base IS NULL
            OR calculation_base IN (
                'NOMINAL_SALARY',
                'EARNED_BASE_PAY'
            )
        ),

    CONSTRAINT ck_compensation_component_shape
        CHECK (
            (
                calculation_type = 'FIXED_AMOUNT'
                AND calculation_base IS NULL
                AND rate_bps IS NULL
                AND amount_minor BETWEEN 1 AND 1000000000000
                AND currency_code ~ '^[A-Z]{3}$'
            )
            OR
            (
                calculation_type = 'PERCENT_OF_BASE'
                AND calculation_base IS NOT NULL
                AND rate_bps BETWEEN 1 AND 10000000
                AND amount_minor IS NULL
                AND currency_code IS NULL
            )
        )
);

CREATE INDEX idx_compensation_component_versions_effective
    ON compensation_component_versions(
        component_id,
        effective_from DESC,
        id DESC
    );
