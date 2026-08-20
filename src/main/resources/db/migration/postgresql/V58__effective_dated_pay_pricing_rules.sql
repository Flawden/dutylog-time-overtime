-- DutyLog v27.46.1 Effective-dated Pay Pricing Rules.
--
-- Pricing policy is versioned independently from factual classification,
-- Time Bank settlement and Payroll snapshots.
--
-- No baseline/default multipliers are inserted intentionally:
-- absence of a term remains distinguishable from an explicitly configured
-- rule set. DutyLog must not silently invent labor-law coefficients.

CREATE TABLE pay_pricing_terms (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL
        REFERENCES users(id)
        ON DELETE CASCADE,
    effective_from DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
        DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL
        DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_pay_pricing_term_owner_effective
        UNIQUE (user_id, effective_from)
);

CREATE INDEX idx_pay_pricing_terms_owner_effective
    ON pay_pricing_terms(
        user_id,
        effective_from DESC
    );

CREATE TABLE pay_pricing_rules (
    id BIGSERIAL PRIMARY KEY,
    term_id BIGINT NOT NULL
        REFERENCES pay_pricing_terms(id)
        ON DELETE CASCADE,
    rule_code VARCHAR(80) NOT NULL,
    dimension VARCHAR(16) NOT NULL,
    premium_bps INTEGER NOT NULL,
    from_minute INTEGER NOT NULL DEFAULT 0,
    to_minute_exclusive INTEGER,
    exclusive_group VARCHAR(80),

    CONSTRAINT uq_pay_pricing_rule_term_code
        UNIQUE (term_id, rule_code),

    CONSTRAINT ck_pay_pricing_rule_dimension
        CHECK (
            dimension IN (
                'NIGHT',
                'HOLIDAY',
                'OVERTIME'
            )
        ),

    CONSTRAINT ck_pay_pricing_rule_premium
        CHECK (
            premium_bps
            BETWEEN 0 AND 10000000
        ),

    CONSTRAINT ck_pay_pricing_rule_shape
        CHECK (
            (
                dimension = 'OVERTIME'
                AND from_minute >= 0
                AND (
                    to_minute_exclusive IS NULL
                    OR to_minute_exclusive
                        > from_minute
                )
            )
            OR
            (
                dimension IN (
                    'NIGHT',
                    'HOLIDAY'
                )
                AND from_minute = 0
                AND to_minute_exclusive IS NULL
            )
        )
);

CREATE INDEX idx_pay_pricing_rules_term
    ON pay_pricing_rules(
        term_id,
        id
    );
