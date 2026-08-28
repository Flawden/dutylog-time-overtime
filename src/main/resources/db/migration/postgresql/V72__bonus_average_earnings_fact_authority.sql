-- DutyLog v27.48.0 paragraph-15 bonus factual authority foundation.
--
-- This table is intentionally separate from payroll_bonus_source_facts.
-- Source provenance answers what line/period/money was observed; this table
-- stores only additional facts needed later by PP-540 paragraph 15:
-- - stable premium indicator identity;
-- - work/award period for which the bonus was accrued;
-- - explicit annual-result identity;
-- - explicit actual-work/proportional accrual facts.
--
-- It does NOT calculate paragraph-15 inclusion and does NOT infer award period
-- from payroll/source month, display text or component effective_from.
-- bonus_source_fact_id and component_id are scalar historical identities,
-- deliberately not foreign keys to mutable source/config rows.

CREATE TABLE payroll_bonus_average_earnings_facts (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    bonus_source_fact_id BIGINT NOT NULL,
    component_id BIGINT NOT NULL,
    earning_kind VARCHAR(32) NOT NULL,
    indicator_key VARCHAR(96) NOT NULL,
    award_period_from DATE NOT NULL,
    award_period_to DATE NOT NULL,
    annual_result BOOLEAN,
    accrued_for_actual_work_time BOOLEAN,
    prorated_for_partial_award_period BOOLEAN,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_payroll_bonus_average_owner_source_fact
        UNIQUE (user_id, bonus_source_fact_id),

    CONSTRAINT ck_payroll_bonus_average_source_fact
        CHECK (bonus_source_fact_id > 0),

    CONSTRAINT ck_payroll_bonus_average_component
        CHECK (component_id > 0),

    CONSTRAINT ck_payroll_bonus_average_kind
        CHECK (earning_kind IN ('MONTHLY_BONUS', 'ONE_TIME_BONUS')),

    CONSTRAINT ck_payroll_bonus_average_indicator
        CHECK (indicator_key ~ '^[A-Z0-9][A-Z0-9._:-]{0,95}$'),

    CONSTRAINT ck_payroll_bonus_average_period
        CHECK (award_period_to >= award_period_from),

    CONSTRAINT ck_payroll_bonus_average_annual
        CHECK (
            annual_result IS DISTINCT FROM TRUE
            OR (
                earning_kind = 'ONE_TIME_BONUS'
                AND EXTRACT(MONTH FROM award_period_from) = 1
                AND EXTRACT(DAY FROM award_period_from) = 1
                AND EXTRACT(MONTH FROM award_period_to) = 12
                AND EXTRACT(DAY FROM award_period_to) = 31
                AND EXTRACT(YEAR FROM award_period_from) =
                    EXTRACT(YEAR FROM award_period_to)
            )
        )
);

CREATE INDEX idx_payroll_bonus_average_owner_indicator
    ON payroll_bonus_average_earnings_facts(
        user_id,
        indicator_key,
        award_period_from,
        award_period_to,
        id
    );

CREATE INDEX idx_payroll_bonus_average_owner_component
    ON payroll_bonus_average_earnings_facts(
        user_id,
        component_id,
        earning_kind,
        id
    );
