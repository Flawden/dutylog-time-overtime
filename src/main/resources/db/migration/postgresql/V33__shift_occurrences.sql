-- DutyLog v27.11.0 Shift Occurrences & Calendar Projection.
-- Concrete dated shifts receive immutable UTC identity plus their source IANA zone.
-- Existing shifts are intentionally left legacy-local; the application freezes them
-- automatically in the old zone on the next timezone change or through the migration UI.
ALTER TABLE day_entries ADD COLUMN IF NOT EXISTS shift_start_instant TIMESTAMPTZ;
ALTER TABLE day_entries ADD COLUMN IF NOT EXISTS shift_end_instant TIMESTAMPTZ;
ALTER TABLE day_entries ADD COLUMN IF NOT EXISTS shift_source_timezone VARCHAR(80);
ALTER TABLE day_entries ADD COLUMN IF NOT EXISTS shift_source_date DATE;
ALTER TABLE day_entries ADD COLUMN IF NOT EXISTS shift_source_start_time TIME;
ALTER TABLE day_entries ADD COLUMN IF NOT EXISTS shift_source_end_time TIME;
ALTER TABLE day_entries ADD COLUMN IF NOT EXISTS shift_break_minutes INTEGER;
ALTER TABLE day_entries ADD COLUMN IF NOT EXISTS shift_net_minutes BIGINT;

ALTER TABLE day_entries DROP CONSTRAINT IF EXISTS chk_day_entry_shift_occurrence_pair;
ALTER TABLE day_entries ADD CONSTRAINT chk_day_entry_shift_occurrence_pair CHECK (
    (shift_start_instant IS NULL AND shift_end_instant IS NULL AND shift_source_timezone IS NULL)
    OR
    (shift_start_instant IS NOT NULL AND shift_end_instant IS NOT NULL
      AND shift_source_timezone IS NOT NULL
      AND shift_end_instant > shift_start_instant)
);

CREATE INDEX IF NOT EXISTS idx_day_entries_shift_occurrence
    ON day_entries(user_id, shift_start_instant, shift_end_instant)
    WHERE shift_start_instant IS NOT NULL AND shift_end_instant IS NOT NULL;
