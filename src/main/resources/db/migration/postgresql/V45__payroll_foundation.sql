-- DutyLog v27.28.0 Payroll Foundation.
-- Adds per-user money settings, append-only monetary adjustments and immutable
-- versioned payroll snapshots calculated only from a closed, healthy time ledger.

CREATE TABLE payroll_settings (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    currency_code VARCHAR(3) NOT NULL DEFAULT 'RUB',
    hourly_rate_minor BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_payroll_settings_user UNIQUE (user_id),
    CONSTRAINT ck_payroll_currency CHECK (currency_code ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_payroll_hourly_rate CHECK (hourly_rate_minor BETWEEN 0 AND 1000000000)
);

CREATE TABLE payroll_adjustments (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    period_month DATE NOT NULL,
    adjustment_type VARCHAR(20) NOT NULL,
    amount_minor BIGINT NOT NULL,
    title VARCHAR(120) NOT NULL,
    note VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_payroll_adjustment_month CHECK (period_month = date_trunc('month', period_month)::date),
    CONSTRAINT ck_payroll_adjustment_type CHECK (adjustment_type IN ('ADDITION', 'DEDUCTION')),
    CONSTRAINT ck_payroll_adjustment_amount CHECK (amount_minor BETWEEN 1 AND 1000000000000)
);

CREATE INDEX idx_payroll_adjustments_owner_month
    ON payroll_adjustments(user_id, period_month, id);

CREATE TABLE payroll_snapshots (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    period_month DATE NOT NULL,
    revision INTEGER NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    hourly_rate_minor BIGINT NOT NULL,
    planned_minutes INTEGER NOT NULL,
    worked_minutes INTEGER NOT NULL,
    vacation_minutes INTEGER NOT NULL,
    sick_minutes INTEGER NOT NULL,
    overtime_compensated_minutes INTEGER NOT NULL,
    unpaid_minutes INTEGER NOT NULL,
    time_adjustment_minutes INTEGER NOT NULL,
    paid_absence_minutes INTEGER NOT NULL,
    payable_minutes INTEGER NOT NULL,
    base_pay_minor BIGINT NOT NULL,
    additions_minor BIGINT NOT NULL,
    deductions_minor BIGINT NOT NULL,
    total_pay_minor BIGINT NOT NULL,
    source_period_closed_at TIMESTAMPTZ NOT NULL,
    source_integrity_checked_at TIMESTAMPTZ NOT NULL,
    calculation_hash CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    superseded_by_id BIGINT REFERENCES payroll_snapshots(id),
    CONSTRAINT uq_payroll_snapshot_revision UNIQUE (user_id, period_month, revision),
    CONSTRAINT ck_payroll_snapshot_month CHECK (period_month = date_trunc('month', period_month)::date),
    CONSTRAINT ck_payroll_snapshot_revision CHECK (revision > 0),
    CONSTRAINT ck_payroll_snapshot_currency CHECK (currency_code ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_payroll_snapshot_minutes CHECK (
        planned_minutes >= 0 AND worked_minutes >= 0 AND vacation_minutes >= 0
        AND sick_minutes >= 0 AND overtime_compensated_minutes >= 0 AND unpaid_minutes >= 0
        AND paid_absence_minutes >= 0 AND payable_minutes >= 0
    ),
    CONSTRAINT ck_payroll_snapshot_hash CHECK (calculation_hash ~ '^[0-9a-f]{64}$')
);

CREATE INDEX idx_payroll_snapshots_owner_month
    ON payroll_snapshots(user_id, period_month, revision DESC);
