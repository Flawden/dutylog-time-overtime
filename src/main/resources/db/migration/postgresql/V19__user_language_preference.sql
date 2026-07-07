ALTER TABLE users ADD COLUMN IF NOT EXISTS language_preference VARCHAR(10) NOT NULL DEFAULT 'ru';
UPDATE users SET language_preference = 'ru' WHERE language_preference IS NULL OR language_preference = '';
