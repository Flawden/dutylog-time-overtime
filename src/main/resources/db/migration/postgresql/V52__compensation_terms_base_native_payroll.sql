-- DutyLog v27.46.0 Compensation Setup & Base Native Payroll.
-- Effective-month compensation terms replace the single mutable rate as the canonical
-- money input while preserving V45 payroll_settings as a compatibility adapter.

CREATE TABLE compensation_terms (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    effective_from DATE NOT NULL,
    pay_mode VARCHAR(16) NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    hourly_rate_minor BIGINT,
    monthly_salary_minor BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_compensation_term_owner_month UNIQUE (user_id, effective_from),
    CONSTRAINT ck_compensation_term_month CHECK (effective_from = date_trunc('month', effective_from)::date),
    CONSTRAINT ck_compensation_term_mode CHECK (pay_mode IN ('HOURLY', 'SALARY')),
    CONSTRAINT ck_compensation_term_currency CHECK (currency_code ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_compensation_term_shape CHECK (
        (pay_mode = 'HOURLY' AND hourly_rate_minor BETWEEN 1 AND 1000000000 AND monthly_salary_minor IS NULL)
        OR
        (pay_mode = 'SALARY' AND monthly_salary_minor BETWEEN 1 AND 1000000000000 AND hourly_rate_minor IS NULL)
    )
);

CREATE INDEX idx_compensation_terms_owner_effective
    ON compensation_terms(user_id, effective_from DESC);

-- Preserve every existing configured hourly rate for all historical months.
INSERT INTO compensation_terms(user_id, effective_from, pay_mode, currency_code, hourly_rate_minor, monthly_salary_minor)
SELECT user_id, DATE '1970-01-01', 'HOURLY', currency_code, hourly_rate_minor, NULL
FROM payroll_settings
WHERE hourly_rate_minor > 0
ON CONFLICT (user_id, effective_from) DO NOTHING;

ALTER TABLE payroll_snapshots
    ADD COLUMN pay_mode VARCHAR(16),
    ADD COLUMN compensation_effective_from DATE,
    ADD COLUMN configured_hourly_rate_minor BIGINT,
    ADD COLUMN monthly_salary_minor BIGINT,
    ADD COLUMN production_norm_minutes INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN salary_covered_minutes INTEGER NOT NULL DEFAULT 0;

UPDATE payroll_snapshots
SET pay_mode = 'HOURLY',
    compensation_effective_from = DATE '1970-01-01',
    configured_hourly_rate_minor = hourly_rate_minor,
    monthly_salary_minor = NULL
WHERE pay_mode IS NULL;

ALTER TABLE payroll_snapshots
    ALTER COLUMN pay_mode SET NOT NULL,
    ALTER COLUMN compensation_effective_from SET NOT NULL,
    ADD CONSTRAINT ck_payroll_snapshot_pay_mode CHECK (pay_mode IN ('HOURLY', 'SALARY')),
    ADD CONSTRAINT ck_payroll_snapshot_compensation_shape CHECK (
        (pay_mode = 'HOURLY' AND configured_hourly_rate_minor IS NOT NULL AND monthly_salary_minor IS NULL)
        OR
        (pay_mode = 'SALARY' AND configured_hourly_rate_minor IS NULL AND monthly_salary_minor IS NOT NULL)
    ),
    ADD CONSTRAINT ck_payroll_snapshot_compensation_minutes CHECK (
        production_norm_minutes >= 0 AND salary_covered_minutes >= 0
        AND salary_covered_minutes <= production_norm_minutes
    );
