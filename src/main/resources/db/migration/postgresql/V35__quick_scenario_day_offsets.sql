ALTER TABLE quick_scenarios
    ADD COLUMN IF NOT EXISTS end_day_offset INTEGER;

UPDATE quick_scenarios
SET end_day_offset = CASE WHEN end_next_day THEN 1 ELSE 0 END
WHERE end_day_offset IS NULL;

ALTER TABLE quick_scenarios
    ALTER COLUMN end_day_offset SET DEFAULT 0;

ALTER TABLE quick_scenarios
    ALTER COLUMN end_day_offset SET NOT NULL;
