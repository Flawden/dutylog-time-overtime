-- DutyLog v27.7.0 Time Foundation.
-- Work timezone owns calendar calculations; display timezone only projects absolute moments.
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS display_timezone VARCHAR(80);

UPDATE users
SET display_timezone = work_timezone
WHERE display_timezone IS NULL OR BTRIM(display_timezone) = '';

ALTER TABLE users
    ALTER COLUMN display_timezone SET DEFAULT 'Europe/Moscow';

ALTER TABLE users
    ALTER COLUMN display_timezone SET NOT NULL;

-- New Telegram deliveries persist an absolute identity. Existing rows only contain
-- a local wall-clock value and the original timezone was never stored, so guessing
-- an instant from the owner's current timezone would silently corrupt history.
ALTER TABLE telegram_notification_deliveries
    ADD COLUMN IF NOT EXISTS remind_at_instant TIMESTAMPTZ;

ALTER TABLE telegram_notification_deliveries
    DROP CONSTRAINT IF EXISTS uq_tg_notification_once;

ALTER TABLE telegram_notification_deliveries
    ADD CONSTRAINT uq_tg_notification_once_instant
        UNIQUE (user_id, reminder_id, remind_at_instant);

CREATE INDEX IF NOT EXISTS idx_tg_notifications_remind_at_instant
    ON telegram_notification_deliveries(remind_at_instant);

-- Legacy rows intentionally keep remind_at_instant = NULL. Runtime deduplication
-- checks their original local key until all future deliveries have absolute identity.
CREATE INDEX IF NOT EXISTS idx_tg_notifications_legacy_local
    ON telegram_notification_deliveries(user_id, reminder_id, remind_at)
    WHERE remind_at_instant IS NULL;
