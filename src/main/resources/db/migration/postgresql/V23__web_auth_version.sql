-- Invalidates existing browser sessions after a password or role change.
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS auth_version BIGINT NOT NULL DEFAULT 0;
