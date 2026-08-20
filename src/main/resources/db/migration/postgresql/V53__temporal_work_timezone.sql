-- DutyLog v27.46.1 Temporal Work Context foundation.
--
-- Work timezone is no longer only one mutable profile value.
-- Terms are effective-dated wall-clock context:
--
--   [effective_from, next effective_from)
--
-- AppUser.work_timezone remains as the current compatibility value while
-- historical business interpretation moves to this versioned timeline.

CREATE TABLE work_timezone_terms (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    effective_from TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    timezone_id VARCHAR(80) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_work_timezone_term_owner_effective
        UNIQUE (user_id, effective_from),

    CONSTRAINT ck_work_timezone_term_not_blank
        CHECK (BTRIM(timezone_id) <> '')
);

CREATE INDEX idx_work_timezone_terms_owner_effective
    ON work_timezone_terms(user_id, effective_from DESC);

-- Preserve the historical behaviour of every existing account.
-- Later user-entered terms may override any part of this baseline.
INSERT INTO work_timezone_terms(
    user_id,
    effective_from,
    timezone_id
)
SELECT
    id,
    TIMESTAMP '1970-01-01 00:00:00',
    work_timezone
FROM users
ON CONFLICT (user_id, effective_from) DO NOTHING;
