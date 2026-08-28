-- DutyLog v27.48.0 COMBINATION Episode Fact Authority.
--
-- Explicit observed source-line facts only:
-- - source period;
-- - qualified minutes;
-- - observed payout money + currency;
-- - optional agreed percentage.
--
-- The external reference salary is deliberately NOT stored or inferred.
-- Missing rows mean no explicit source evidence is available; they do not
-- prove that no combination episode existed.
--
-- component_id is a scalar historical identity, not a FK to mutable
-- compensation configuration. Facts therefore survive later configuration
-- deletion just like frozen snapshot component provenance.

CREATE TABLE payroll_combination_episode_facts (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    component_id BIGINT NOT NULL,
    period_from DATE NOT NULL,
    period_to DATE NOT NULL,
    qualified_minutes BIGINT NOT NULL,
    amount_minor BIGINT NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    agreed_rate_bps INTEGER,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_payroll_combination_episode_owner_component_start
        UNIQUE (user_id, component_id, period_from),

    CONSTRAINT ck_payroll_combination_episode_component
        CHECK (component_id > 0),

    CONSTRAINT ck_payroll_combination_episode_period
        CHECK (
            period_to >= period_from
            AND date_trunc('month', period_from)::date =
                date_trunc('month', period_to)::date
        ),

    CONSTRAINT ck_payroll_combination_episode_minutes
        CHECK (qualified_minutes > 0),

    CONSTRAINT ck_payroll_combination_episode_amount
        CHECK (amount_minor BETWEEN 1 AND 1000000000000),

    CONSTRAINT ck_payroll_combination_episode_currency
        CHECK (currency_code ~ '^[A-Z]{3}$'),

    CONSTRAINT ck_payroll_combination_episode_rate
        CHECK (
            agreed_rate_bps IS NULL
            OR agreed_rate_bps BETWEEN 1 AND 10000000
        )
);

CREATE INDEX idx_payroll_combination_episode_owner_component_dates
    ON payroll_combination_episode_facts(
        user_id,
        component_id,
        period_from,
        period_to,
        id
    );

CREATE INDEX idx_payroll_combination_episode_owner_month
    ON payroll_combination_episode_facts(
        user_id,
        period_from,
        component_id,
        id
    );
