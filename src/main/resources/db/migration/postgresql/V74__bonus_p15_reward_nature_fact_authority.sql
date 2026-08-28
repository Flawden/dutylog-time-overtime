-- DutyLog v27.48.0 PP-540 paragraph-15 reward-nature FACT authority.
--
-- F1 deliberately did not infer legal reward family from display name or
-- posting month. Paragraph 15, however, distinguishes monthly rewards,
-- rewards for a work period, annual-result rewards and one-time service-length
-- rewards. ONE_TIME_BONUS alone is therefore insufficient factual identity.
--
-- This table stores only the explicit source nature. It does not decide legal
-- inclusion, proportional reduction or money. Scalar parent identities are
-- intentionally not foreign keys to mutable F1/D2 rows.

CREATE TABLE payroll_bonus_p15_nature_facts (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    bonus_average_fact_id BIGINT NOT NULL,
    bonus_source_fact_id BIGINT NOT NULL,
    component_id BIGINT NOT NULL,
    earning_kind VARCHAR(32) NOT NULL,
    p15_nature VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_payroll_bonus_p15_nature_owner_average_fact
        UNIQUE (user_id, bonus_average_fact_id),

    CONSTRAINT uq_payroll_bonus_p15_nature_owner_source_fact
        UNIQUE (user_id, bonus_source_fact_id),

    CONSTRAINT ck_payroll_bonus_p15_nature_ids
        CHECK (
            bonus_average_fact_id > 0
            AND bonus_source_fact_id > 0
            AND component_id > 0
        ),

    CONSTRAINT ck_payroll_bonus_p15_nature_kind
        CHECK (earning_kind IN ('MONTHLY_BONUS', 'ONE_TIME_BONUS')),

    CONSTRAINT ck_payroll_bonus_p15_nature_value
        CHECK (
            p15_nature IN (
                'MONTHLY',
                'WORK_PERIOD',
                'ANNUAL_RESULT',
                'SERVICE_LENGTH'
            )
        ),

    CONSTRAINT ck_payroll_bonus_p15_nature_kind_compatibility
        CHECK (
            (p15_nature = 'MONTHLY' AND earning_kind = 'MONTHLY_BONUS')
            OR (
                p15_nature IN ('WORK_PERIOD', 'ANNUAL_RESULT', 'SERVICE_LENGTH')
                AND earning_kind = 'ONE_TIME_BONUS'
            )
        )
);

CREATE INDEX idx_payroll_bonus_p15_nature_owner_component
    ON payroll_bonus_p15_nature_facts(
        user_id,
        component_id,
        earning_kind,
        p15_nature,
        id
    );
