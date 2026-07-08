CREATE TABLE IF NOT EXISTS user_module_settings (
    id BIGSERIAL PRIMARY KEY,
    owner_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    module_key VARCHAR(60) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMP,
    CONSTRAINT uk_user_module_settings_owner_key UNIQUE (owner_id, module_key)
);

CREATE INDEX IF NOT EXISTS idx_user_module_settings_owner ON user_module_settings(owner_id);
