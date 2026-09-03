-- DutyLog Payroll Trust P1B2A
-- Effective-dated work/legal jurisdiction authority.
--
-- IMPORTANT:
-- Existing users are deliberately NOT backfilled.
-- Timezone, language, locale and current Russian legal policies are not evidence
-- of a user's historical jurisdiction. Missing authority must remain unknown
-- until an explicit term is configured.

CREATE TABLE work_jurisdiction_terms (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    effective_from DATE NOT NULL,
    jurisdiction_code VARCHAR(16) NOT NULL,
    region_code VARCHAR(32),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT uq_work_jurisdiction_term_owner_effective
        UNIQUE (user_id, effective_from),

    CONSTRAINT ck_work_jurisdiction_code_nonblank
        CHECK (BTRIM(jurisdiction_code) <> ''),

    CONSTRAINT ck_work_jurisdiction_region_nonblank
        CHECK (region_code IS NULL OR BTRIM(region_code) <> '')
);

CREATE INDEX idx_work_jurisdiction_terms_owner_effective
    ON work_jurisdiction_terms(user_id, effective_from);
