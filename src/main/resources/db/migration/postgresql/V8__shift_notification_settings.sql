ALTER TABLE shift_types
    ADD COLUMN IF NOT EXISTS notifications_enabled boolean NOT NULL DEFAULT true;

ALTER TABLE shift_types
    ADD COLUMN IF NOT EXISTS notification_minutes_before integer;
