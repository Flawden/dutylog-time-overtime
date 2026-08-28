-- DutyLog v27.48.0 immutable paragraph-15 bonus fact freeze.
--
-- Snapshot-time copy of already explicit source/F1 facts only. The source fact
-- itself may later be edited or deleted; historical average-earnings logic must
-- read this immutable snapshot companion instead of mutable current authority.
--
-- The manifest is persisted for every new Payroll snapshot. complete=true means
-- every explicit bonus source fact present for that payroll month also had a
-- paragraph-15 factual row at freeze time. Missing F1 facts are represented by
-- complete=false, never by guessed false/default values or posting-month dates.
--
-- No paragraph-15 inclusion, allocation or money formula is implemented here.

CREATE TABLE payroll_snapshot_bonus_average_earnings_manifests (
    snapshot_id BIGINT PRIMARY KEY
        REFERENCES payroll_snapshots(id) ON DELETE CASCADE,
    complete BOOLEAN NOT NULL,
    source_fact_count INTEGER NOT NULL,
    fact_count INTEGER NOT NULL,
    fingerprint VARCHAR(64) NOT NULL,

    CONSTRAINT ck_payroll_snapshot_bonus_average_manifest_counts
        CHECK (
            source_fact_count >= 0
            AND fact_count >= 0
            AND fact_count <= source_fact_count
            AND complete = (fact_count = source_fact_count)
        ),

    CONSTRAINT ck_payroll_snapshot_bonus_average_manifest_fingerprint
        CHECK (fingerprint ~ '^[0-9a-f]{64}$')
);

CREATE TABLE payroll_snapshot_bonus_average_earnings_facts (
    id BIGSERIAL PRIMARY KEY,
    snapshot_id BIGINT NOT NULL
        REFERENCES payroll_snapshots(id) ON DELETE CASCADE,
    fact_index INTEGER NOT NULL,

    -- Scalar historical identities; deliberately not foreign keys to mutable
    -- current source/config/F1 rows.
    bonus_source_fact_id BIGINT NOT NULL,
    bonus_average_fact_id BIGINT NOT NULL,
    component_id BIGINT NOT NULL,
    earning_kind VARCHAR(32) NOT NULL,

    -- Exact observed source-line identity from D2.
    source_period_from DATE NOT NULL,
    source_period_to DATE NOT NULL,
    amount_minor BIGINT NOT NULL,
    currency_code VARCHAR(3) NOT NULL,

    -- Exact additional F1 facts. Nullable booleans preserve UNKNOWN.
    indicator_key VARCHAR(96) NOT NULL,
    award_period_from DATE NOT NULL,
    award_period_to DATE NOT NULL,
    annual_result BOOLEAN,
    accrued_for_actual_work_time BOOLEAN,
    prorated_for_partial_award_period BOOLEAN,

    CONSTRAINT uq_payroll_snapshot_bonus_average_fact_order
        UNIQUE (snapshot_id, fact_index),

    CONSTRAINT uq_payroll_snapshot_bonus_average_source
        UNIQUE (snapshot_id, bonus_source_fact_id),

    CONSTRAINT uq_payroll_snapshot_bonus_average_fact_identity
        UNIQUE (snapshot_id, bonus_average_fact_id),

    CONSTRAINT ck_payroll_snapshot_bonus_average_fact_index
        CHECK (fact_index >= 0),

    CONSTRAINT ck_payroll_snapshot_bonus_average_fact_ids
        CHECK (
            bonus_source_fact_id > 0
            AND bonus_average_fact_id > 0
            AND component_id > 0
        ),

    CONSTRAINT ck_payroll_snapshot_bonus_average_fact_kind
        CHECK (earning_kind IN ('MONTHLY_BONUS', 'ONE_TIME_BONUS')),

    CONSTRAINT ck_payroll_snapshot_bonus_average_source_period
        CHECK (
            source_period_to >= source_period_from
            AND date_trunc('month', source_period_from)::date =
                date_trunc('month', source_period_to)::date
        ),

    CONSTRAINT ck_payroll_snapshot_bonus_average_amount
        CHECK (amount_minor BETWEEN 1 AND 1000000000000),

    CONSTRAINT ck_payroll_snapshot_bonus_average_currency
        CHECK (currency_code ~ '^[A-Z]{3}$'),

    CONSTRAINT ck_payroll_snapshot_bonus_average_indicator
        CHECK (indicator_key ~ '^[A-Z0-9][A-Z0-9._:-]{0,95}$'),

    CONSTRAINT ck_payroll_snapshot_bonus_average_award_period
        CHECK (award_period_to >= award_period_from),

    CONSTRAINT ck_payroll_snapshot_bonus_average_annual
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

CREATE INDEX idx_payroll_snapshot_bonus_average_facts_snapshot
    ON payroll_snapshot_bonus_average_earnings_facts(snapshot_id, fact_index);

CREATE INDEX idx_payroll_snapshot_bonus_average_facts_indicator
    ON payroll_snapshot_bonus_average_earnings_facts(
        snapshot_id,
        indicator_key,
        award_period_from,
        award_period_to,
        fact_index
    );
