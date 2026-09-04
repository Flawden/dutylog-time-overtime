-- P1B2C1: immutable regional statutory-holiday legal dataset authority.
-- No regional legal data is seeded by this migration.
-- Production calendar BASE/LOCAL_OVERRIDE is intentionally not reused as legal authority.

CREATE TABLE regional_statutory_holiday_datasets (
    id BIGSERIAL PRIMARY KEY,
    jurisdiction_code VARCHAR(16) NOT NULL,
    region_code VARCHAR(32) NOT NULL,
    coverage_from DATE NOT NULL,
    coverage_to DATE NOT NULL,
    legal_regime VARCHAR(160) NOT NULL,
    legal_basis VARCHAR(500) NOT NULL,
    source_revision VARCHAR(240) NOT NULL,
    source_reference VARCHAR(1000) NOT NULL,
    complete BOOLEAN NOT NULL,
    fingerprint VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_regional_holiday_dataset_fingerprint UNIQUE (fingerprint),
    CONSTRAINT ck_regional_holiday_dataset_coverage CHECK (coverage_to >= coverage_from),
    CONSTRAINT ck_regional_holiday_dataset_fingerprint CHECK (fingerprint ~ '^[0-9a-f]{64}$')
);
CREATE INDEX idx_regional_holiday_dataset_lookup ON regional_statutory_holiday_datasets (jurisdiction_code, region_code, coverage_from, coverage_to, id);

CREATE TABLE regional_statutory_holiday_date_facts (
    id BIGSERIAL PRIMARY KEY,
    dataset_id BIGINT NOT NULL REFERENCES regional_statutory_holiday_datasets(id) ON DELETE RESTRICT,
    holiday_date DATE NOT NULL,
    holiday_code VARCHAR(96) NOT NULL,
    holiday_label VARCHAR(240),
    legal_basis VARCHAR(500) NOT NULL,
    source_reference VARCHAR(1000) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_regional_holiday_dataset_date UNIQUE (dataset_id, holiday_date)
);
CREATE INDEX idx_regional_holiday_date_fact_lookup ON regional_statutory_holiday_date_facts (dataset_id, holiday_date, id);

COMMENT ON COLUMN regional_statutory_holiday_datasets.complete IS 'TRUE means absence of a date fact inside this exact coverage window proves a negative regional statutory-holiday result.';
COMMENT ON COLUMN regional_statutory_holiday_datasets.fingerprint IS 'SHA-256 over canonical manifest metadata plus ordered date facts.';

-- Existing users and regions are deliberately NOT backfilled.
-- LOCAL_OVERRIDE and ProductionCalendarDay are deliberately NOT legal authority.
