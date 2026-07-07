-- v22.3: admin users management foundation.
-- role = permissions (USER/ADMIN), account_tier = future business tier (FREE/PAID/VIP), not used for permissions yet.
ALTER TABLE users ADD COLUMN IF NOT EXISTS account_tier varchar(20) NOT NULL DEFAULT 'FREE';
ALTER TABLE users ADD COLUMN IF NOT EXISTS created_at timestamp with time zone;
ALTER TABLE users ADD COLUMN IF NOT EXISTS updated_at timestamp with time zone;

UPDATE users SET account_tier = 'FREE' WHERE account_tier IS NULL OR account_tier = '';
UPDATE users SET created_at = now() WHERE created_at IS NULL;
UPDATE users SET updated_at = now() WHERE updated_at IS NULL;

ALTER TABLE users ALTER COLUMN created_at SET NOT NULL;
ALTER TABLE users ALTER COLUMN updated_at SET NOT NULL;
