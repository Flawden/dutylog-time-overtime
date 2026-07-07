-- v23.0: Personalization foundation.
-- Keep roles, account tier and visual preferences separate:
-- roles = permissions, account_tier = future commercial level, preferences = personal UI.

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS theme_preference VARCHAR(20) NOT NULL DEFAULT 'system';

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS accent_color VARCHAR(20) NOT NULL DEFAULT '#F5B841';

ALTER TABLE day_entries
    ADD COLUMN IF NOT EXISTS day_emoji VARCHAR(32);
