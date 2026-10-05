-- Explicit review of the entire monthly remuneration system. No inferred backfill.
CREATE TABLE article153_remuneration_authorities (
    id BIGSERIAL PRIMARY KEY,
    owner_id BIGINT NOT NULL REFERENCES users(id),
    period_month DATE NOT NULL,
    revision INTEGER NOT NULL CHECK (revision > 0),
    payload_json TEXT NOT NULL CHECK (length(trim(payload_json)) > 0),
    fingerprint VARCHAR(64) NOT NULL CHECK (fingerprint ~ '^[0-9a-f]{64}$'),
    certified_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_article153_remuneration_revision UNIQUE (owner_id, period_month, revision),
    CONSTRAINT ck_article153_remuneration_month CHECK (EXTRACT(DAY FROM period_month) = 1)
);
