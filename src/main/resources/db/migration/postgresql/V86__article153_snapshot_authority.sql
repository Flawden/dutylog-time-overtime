-- P1B3C3: immutable authority evidence, not HOLIDAY_PAY money activation.
-- No historical backfill: missing rows mean unknown provenance.
CREATE TABLE payroll_snapshot_article153 (
    snapshot_id BIGINT PRIMARY KEY REFERENCES payroll_snapshots(id),
    schema_version VARCHAR(40) NOT NULL,
    piece_count INTEGER NOT NULL CHECK (piece_count >= 0),
    qualified_minutes BIGINT NOT NULL CHECK (qualified_minutes >= 0),
    payload_json TEXT NOT NULL CHECK (length(payload_json) > 0),
    fingerprint VARCHAR(64) NOT NULL CHECK (fingerprint ~ '^[0-9a-f]{64}$')
);
