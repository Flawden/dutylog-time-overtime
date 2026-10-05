-- C4A: frozen, unbooked tariff references. No historical backfill or payroll money changes.
CREATE TABLE payroll_snapshot_article153_tariff (
    snapshot_id BIGINT PRIMARY KEY REFERENCES payroll_snapshot_article153(snapshot_id),
    schema_version VARCHAR(40) NOT NULL,
    payload_json TEXT NOT NULL CHECK (length(payload_json) > 0),
    fingerprint VARCHAR(64) NOT NULL CHECK (fingerprint ~ '^[0-9a-f]{64}$')
);
