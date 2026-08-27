-- DutyLog v27.48.0 Employment Coverage Foundation.
--
-- Machine-owned factual employment periods.
--
-- Historical policy:
-- - NO backfill;
-- - account created_at is not employment provenance;
-- - first compensation term is not employment provenance;
-- - first DayEntry is not employment provenance;
-- - first Payroll snapshot is not employment provenance;
-- - absence of rows means employment history is UNCONFIGURED,
--   not that the user was never employed.
--
-- Multiple non-overlapping periods support termination and later rehire.
-- Open-ended end_date means the employment period is still active.
--
-- This migration changes no Payroll money.

CREATE TABLE employment_periods (
    id BIGSERIAL PRIMARY KEY,

    user_id BIGINT NOT NULL
        REFERENCES users(id)
        ON DELETE CASCADE,

    start_date DATE NOT NULL,
    end_date DATE,

    created_at TIMESTAMPTZ NOT NULL
        DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMPTZ NOT NULL
        DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_employment_period_owner_start
        UNIQUE (user_id, start_date),

    CONSTRAINT ck_employment_period_dates
        CHECK (
            end_date IS NULL
            OR end_date >= start_date
        )
);

CREATE INDEX idx_employment_periods_owner_dates
    ON employment_periods(
        user_id,
        start_date,
        end_date,
        id
    );

-- Deliberately no INSERT / inferred baseline.
