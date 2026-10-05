CREATE TABLE payroll_snapshot_article153_payable (
    snapshot_id BIGINT PRIMARY KEY REFERENCES payroll_snapshot_article153_remuneration(snapshot_id),
    schema_version VARCHAR(40) NOT NULL,
    payload_json TEXT NOT NULL,
    fingerprint VARCHAR(64) NOT NULL CHECK (fingerprint ~ '^[0-9a-f]{64}$')
);
