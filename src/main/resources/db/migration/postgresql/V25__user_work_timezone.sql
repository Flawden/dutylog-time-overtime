-- v27.3.0: persist a validated IANA work timezone per user.
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS work_timezone VARCHAR(80);

UPDATE users
SET work_timezone = 'Europe/Moscow'
WHERE work_timezone IS NULL OR BTRIM(work_timezone) = '';

ALTER TABLE users
    ALTER COLUMN work_timezone SET DEFAULT 'Europe/Moscow';

ALTER TABLE users
    ALTER COLUMN work_timezone SET NOT NULL;
