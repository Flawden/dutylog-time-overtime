-- New revisions only. Frozen C3/C4A/B1 evidence chain; no historical backfill.
CREATE TABLE payroll_snapshot_article153_remuneration (
    snapshot_id BIGINT PRIMARY KEY REFERENCES payroll_snapshot_article153_tariff(snapshot_id),
    remuneration_authority_id BIGINT NOT NULL REFERENCES article153_remuneration_authorities(id),
    schema_version VARCHAR(40) NOT NULL,
    payload_json TEXT NOT NULL CHECK (length(trim(payload_json)) > 0),
    fingerprint VARCHAR(64) NOT NULL CHECK (fingerprint ~ '^[0-9a-f]{64}$')
);
