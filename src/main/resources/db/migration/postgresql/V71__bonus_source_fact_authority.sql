-- DutyLog v27.48.0 bonus Source Fact Authority.
--
-- Explicit observed source-line facts only:
-- - semantic bonus kind (MONTHLY_BONUS / ONE_TIME_BONUS);
-- - earning/source period;
-- - observed bonus money + currency.
--
-- MONTHLY_BONUS formula/base authority remains machine-owned separately by
-- LOCAL_ELIGIBLE_EARNINGS. ONE_TIME_BONUS may have no percentage-base
-- authority. This table does not infer or backsolve either base, and it does
-- not implement average-earnings paragraph 15 bonus treatment.
--
-- component_id is a scalar historical identity, not a FK to mutable
-- compensation configuration. Missing rows mean no exact source-period fact;
-- they do not authorize using posting month or effective_from.

CREATE TABLE payroll_bonus_source_facts (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    component_id BIGINT NOT NULL,
    earning_kind VARCHAR(32) NOT NULL,
    period_from DATE NOT NULL,
    period_to DATE NOT NULL,
    amount_minor BIGINT NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_payroll_bonus_source_owner_component_kind_start
        UNIQUE (user_id, component_id, earning_kind, period_from),

    CONSTRAINT ck_payroll_bonus_source_component
        CHECK (component_id > 0),

    CONSTRAINT ck_payroll_bonus_source_kind
        CHECK (earning_kind IN ('MONTHLY_BONUS', 'ONE_TIME_BONUS')),

    CONSTRAINT ck_payroll_bonus_source_period
        CHECK (
            period_to >= period_from
            AND date_trunc('month', period_from)::date =
                date_trunc('month', period_to)::date
        ),

    CONSTRAINT ck_payroll_bonus_source_amount
        CHECK (amount_minor BETWEEN 1 AND 1000000000000),

    CONSTRAINT ck_payroll_bonus_source_currency
        CHECK (currency_code ~ '^[A-Z]{3}$')
);

CREATE INDEX idx_payroll_bonus_source_owner_component_kind_dates
    ON payroll_bonus_source_facts(
        user_id,
        component_id,
        earning_kind,
        period_from,
        period_to,
        id
    );

CREATE INDEX idx_payroll_bonus_source_owner_month
    ON payroll_bonus_source_facts(
        user_id,
        period_from,
        earning_kind,
        component_id,
        id
    );
