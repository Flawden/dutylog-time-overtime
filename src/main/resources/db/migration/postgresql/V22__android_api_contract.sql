-- Android API v1: optimistic day versions and durable idempotency keys.
ALTER TABLE day_entries
    ADD COLUMN IF NOT EXISTS row_version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE day_entries
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

CREATE TABLE IF NOT EXISTS mobile_sync_operations (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    operation_id VARCHAR(64) NOT NULL,
    entity_type VARCHAR(40) NOT NULL,
    entity_key VARCHAR(120) NOT NULL,
    status VARCHAR(32) NOT NULL,
    server_version BIGINT,
    error_code VARCHAR(64),
    message VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_mobile_sync_owner_operation UNIQUE (user_id, operation_id)
);

CREATE INDEX IF NOT EXISTS idx_mobile_sync_owner_created
    ON mobile_sync_operations(user_id, created_at);

CREATE INDEX IF NOT EXISTS idx_mobile_sync_operation_id
    ON mobile_sync_operations(operation_id);
