-- DutyLog v27.48.0 immutable paragraph-15 reward-nature freeze.
--
-- Freezes only explicit F3A nature facts beside the immutable Payroll snapshot.
-- Missing nature is represented by complete=false; no display-name, posting-month,
-- award-period-length or other heuristic backfill is allowed.
-- No paragraph-15 inclusion, allocation or money formula is implemented here.

CREATE TABLE payroll_snapshot_bonus_p15_nature_manifests (
    snapshot_id BIGINT PRIMARY KEY
        REFERENCES payroll_snapshots(id) ON DELETE CASCADE,
    complete BOOLEAN NOT NULL,
    average_fact_count INTEGER NOT NULL,
    nature_fact_count INTEGER NOT NULL,
    fingerprint VARCHAR(64) NOT NULL,

    CONSTRAINT ck_payroll_snapshot_bonus_p15_nature_manifest_counts
        CHECK (
            average_fact_count >= 0
            AND nature_fact_count >= 0
            AND nature_fact_count <= average_fact_count
            AND complete = (nature_fact_count = average_fact_count)
        ),
    CONSTRAINT ck_payroll_snapshot_bonus_p15_nature_manifest_fingerprint
        CHECK (fingerprint ~ '^[0-9a-f]{64}$')
);

CREATE TABLE payroll_snapshot_bonus_p15_nature_facts (
    id BIGSERIAL PRIMARY KEY,
    snapshot_id BIGINT NOT NULL
        REFERENCES payroll_snapshots(id) ON DELETE CASCADE,
    fact_index INTEGER NOT NULL,

    -- Scalar immutable identities; deliberately not foreign keys to mutable F1/F3A rows.
    bonus_source_fact_id BIGINT NOT NULL,
    bonus_average_fact_id BIGINT NOT NULL,
    bonus_nature_fact_id BIGINT NOT NULL,
    component_id BIGINT NOT NULL,
    earning_kind VARCHAR(32) NOT NULL,
    p15_nature VARCHAR(32) NOT NULL,

    CONSTRAINT uq_payroll_snapshot_bonus_p15_nature_order
        UNIQUE (snapshot_id, fact_index),
    CONSTRAINT uq_payroll_snapshot_bonus_p15_nature_source
        UNIQUE (snapshot_id, bonus_source_fact_id),
    CONSTRAINT uq_payroll_snapshot_bonus_p15_nature_average
        UNIQUE (snapshot_id, bonus_average_fact_id),
    CONSTRAINT uq_payroll_snapshot_bonus_p15_nature_identity
        UNIQUE (snapshot_id, bonus_nature_fact_id),

    CONSTRAINT ck_payroll_snapshot_bonus_p15_nature_index
        CHECK (fact_index >= 0),
    CONSTRAINT ck_payroll_snapshot_bonus_p15_nature_ids
        CHECK (
            bonus_source_fact_id > 0
            AND bonus_average_fact_id > 0
            AND bonus_nature_fact_id > 0
            AND component_id > 0
        ),
    CONSTRAINT ck_payroll_snapshot_bonus_p15_nature_kind
        CHECK (earning_kind IN ('MONTHLY_BONUS', 'ONE_TIME_BONUS')),
    CONSTRAINT ck_payroll_snapshot_bonus_p15_nature_value
        CHECK (p15_nature IN ('MONTHLY', 'WORK_PERIOD', 'ANNUAL_RESULT', 'SERVICE_LENGTH')),
    CONSTRAINT ck_payroll_snapshot_bonus_p15_nature_coarse_identity
        CHECK (
            (p15_nature = 'MONTHLY' AND earning_kind = 'MONTHLY_BONUS')
            OR
            (p15_nature <> 'MONTHLY' AND earning_kind = 'ONE_TIME_BONUS')
        )
);

CREATE INDEX idx_payroll_snapshot_bonus_p15_nature_snapshot
    ON payroll_snapshot_bonus_p15_nature_facts(snapshot_id, fact_index);
