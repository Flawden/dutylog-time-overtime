-- DutyLog v27.48.0 REGIONAL_COEFFICIENT Source Fact Authority.
--
-- Explicit observed source-line facts only:
-- - earning/source period;
-- - observed regional coefficient money + currency.
--
-- The eligible earnings base remains machine-owned by
-- LOCAL_ELIGIBLE_EARNINGS / PayrollEarningBaseEligibility. This table does not
-- rebuild, split or backsolve that base. Missing rows mean no exact source
-- period evidence is available; they do not authorize using the posting month.
--
-- component_id is a scalar historical identity, not a FK to mutable
-- compensation configuration. Facts therefore survive later configuration
-- deletion just like frozen snapshot component provenance.

CREATE TABLE payroll_regional_coefficient_source_facts (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    component_id BIGINT NOT NULL,
    period_from DATE NOT NULL,
    period_to DATE NOT NULL,
    amount_minor BIGINT NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_payroll_regional_source_owner_component_start
        UNIQUE (user_id, component_id, period_from),

    CONSTRAINT ck_payroll_regional_source_component
        CHECK (component_id > 0),

    CONSTRAINT ck_payroll_regional_source_period
        CHECK (
            period_to >= period_from
            AND date_trunc('month', period_from)::date =
                date_trunc('month', period_to)::date
        ),

    CONSTRAINT ck_payroll_regional_source_amount
        CHECK (amount_minor BETWEEN 1 AND 1000000000000),

    CONSTRAINT ck_payroll_regional_source_currency
        CHECK (currency_code ~ '^[A-Z]{3}$')
);

CREATE INDEX idx_payroll_regional_source_owner_component_dates
    ON payroll_regional_coefficient_source_facts(
        user_id,
        component_id,
        period_from,
        period_to,
        id
    );

CREATE INDEX idx_payroll_regional_source_owner_month
    ON payroll_regional_coefficient_source_facts(
        user_id,
        period_from,
        component_id,
        id
    );
