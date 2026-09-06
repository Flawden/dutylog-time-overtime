-- P1B3B2B: source-locked Article 153 employee election of another rest day.
-- This is legal-choice authority only. It does not create compensatory-time
-- balances, schedule the later rest day, or calculate payroll money.

CREATE TABLE article153_rest_day_elections (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    work_date DATE NOT NULL,
    source_kind VARCHAR(20) NOT NULL,
    source_identity VARCHAR(96) NOT NULL,
    source_actual_work_interval_id BIGINT,
    source_day_entry_id BIGINT,
    source_evidence_start_instant TIMESTAMPTZ NOT NULL,
    source_evidence_end_instant TIMESTAMPTZ NOT NULL,
    source_evidence_timezone VARCHAR(80) NOT NULL,
    qualified_cause VARCHAR(32) NOT NULL,
    qualified_minutes INTEGER NOT NULL,
    source_event_fingerprint VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ELECTED',
    elected_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    revocation_reason VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT uq_article153_rest_day_election_source
        UNIQUE (user_id, work_date, source_identity),

    CONSTRAINT ck_article153_rest_day_source_kind
        CHECK (source_kind IN ('EXPLICIT', 'PLAN_DERIVED')),

    CONSTRAINT ck_article153_rest_day_source_shape
        CHECK (
            (source_kind = 'EXPLICIT'
                AND source_actual_work_interval_id IS NOT NULL
                AND source_actual_work_interval_id > 0
                AND source_day_entry_id IS NULL
                AND source_identity = 'EXPLICIT:' || source_actual_work_interval_id::text)
            OR
            (source_kind = 'PLAN_DERIVED'
                AND source_day_entry_id IS NOT NULL
                AND source_day_entry_id > 0
                AND source_actual_work_interval_id IS NULL
                AND source_identity = 'PLAN_DERIVED:' || source_day_entry_id::text)
        ),

    CONSTRAINT ck_article153_rest_day_evidence_order
        CHECK (source_evidence_end_instant > source_evidence_start_instant),

    CONSTRAINT ck_article153_rest_day_timezone
        CHECK (length(trim(source_evidence_timezone)) > 0),

    CONSTRAINT ck_article153_rest_day_cause
        CHECK (qualified_cause IN ('PUBLIC_HOLIDAY', 'EMPLOYEE_REST_DAY', 'BOTH')),

    CONSTRAINT ck_article153_rest_day_minutes
        CHECK (qualified_minutes > 0),

    CONSTRAINT ck_article153_rest_day_fingerprint
        CHECK (source_event_fingerprint ~ '^[0-9a-f]{64}$'),

    CONSTRAINT ck_article153_rest_day_status
        CHECK (status IN ('ELECTED', 'REVOKED')),

    CONSTRAINT ck_article153_rest_day_lifecycle
        CHECK (
            (status = 'ELECTED' AND revoked_at IS NULL AND revocation_reason IS NULL)
            OR
            (status = 'REVOKED' AND revoked_at IS NOT NULL AND length(trim(revocation_reason)) > 0)
        )
);

CREATE INDEX idx_article153_rest_day_elections_owner_date
    ON article153_rest_day_elections(user_id, work_date, id);

CREATE INDEX idx_article153_rest_day_elections_source
    ON article153_rest_day_elections(user_id, source_identity, id);
