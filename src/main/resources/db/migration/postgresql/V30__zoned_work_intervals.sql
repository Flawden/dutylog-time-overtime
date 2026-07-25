-- DutyLog v27.8.0 Zoned Work Intervals.
-- New calculated overtime credits persist absolute interval identity.
-- Existing rows are intentionally not backfilled: their original timezone was never stored.
ALTER TABLE overtime_credits
    ADD COLUMN IF NOT EXISTS start_at_instant TIMESTAMPTZ;

ALTER TABLE overtime_credits
    ADD COLUMN IF NOT EXISTS end_at_instant TIMESTAMPTZ;

ALTER TABLE overtime_credits
    ADD COLUMN IF NOT EXISTS source_timezone VARCHAR(80);

CREATE INDEX IF NOT EXISTS idx_overtime_credits_absolute_interval
    ON overtime_credits(user_id, start_at_instant, end_at_instant)
    WHERE start_at_instant IS NOT NULL AND end_at_instant IS NOT NULL;

ALTER TABLE overtime_credits
    DROP CONSTRAINT IF EXISTS chk_overtime_absolute_interval_pair;

ALTER TABLE overtime_credits
    ADD CONSTRAINT chk_overtime_absolute_interval_pair
    CHECK (
        (start_at_instant IS NULL AND end_at_instant IS NULL AND source_timezone IS NULL)
        OR
        (start_at_instant IS NOT NULL AND end_at_instant IS NOT NULL AND source_timezone IS NOT NULL)
    );
