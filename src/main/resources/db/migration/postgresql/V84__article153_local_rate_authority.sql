-- P1B3C1: source-backed legal authority for effective HOLIDAY PayPricing terms.
-- The economic rule remains in pay_pricing_terms/pay_pricing_rules. This table
-- binds the exact HOLIDAY rule fingerprint to the Article 153 legal source.
-- It does not calculate money and does not activate HOLIDAY_PAY.

CREATE TABLE article153_local_rate_authorities (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    pay_pricing_term_id BIGINT NOT NULL REFERENCES pay_pricing_terms(id) ON DELETE CASCADE,
    source_kind VARCHAR(40) NOT NULL,
    source_reference VARCHAR(500) NOT NULL,
    source_revision VARCHAR(160) NOT NULL,
    holiday_policy_fingerprint VARCHAR(64) NOT NULL,
    certified_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT uq_article153_local_rate_term
        UNIQUE (user_id, pay_pricing_term_id),

    CONSTRAINT ck_article153_local_rate_source_kind
        CHECK (source_kind IN (
            'COLLECTIVE_AGREEMENT',
            'LOCAL_NORMATIVE_ACT',
            'EMPLOYMENT_CONTRACT'
        )),

    CONSTRAINT ck_article153_local_rate_source_reference
        CHECK (length(trim(source_reference)) > 0),

    CONSTRAINT ck_article153_local_rate_source_revision
        CHECK (length(trim(source_revision)) > 0),

    CONSTRAINT ck_article153_local_rate_fingerprint
        CHECK (holiday_policy_fingerprint ~ '^[0-9a-f]{64}$')
);

CREATE INDEX idx_article153_local_rate_owner_term
    ON article153_local_rate_authorities(user_id, pay_pricing_term_id, id);
