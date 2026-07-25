-- DutyLog v27.9.0 Overtime Interval Engine.
-- Integer minutes are authoritative; hours remain for backward-compatible API/reporting.
-- Existing absolute credits are converted to exact credited intervals using the v27.8 rule:
-- break and planned work are removed from the earliest minutes, so overtime ends with the entered interval.

UPDATE users
SET display_timezone = work_timezone
WHERE display_timezone IS DISTINCT FROM work_timezone;

ALTER TABLE overtime_credits
    ADD COLUMN IF NOT EXISTS credited_minutes INTEGER;
ALTER TABLE overtime_credits
    ADD COLUMN IF NOT EXISTS credited_start_at_instant TIMESTAMPTZ;
ALTER TABLE overtime_credits
    ADD COLUMN IF NOT EXISTS credited_end_at_instant TIMESTAMPTZ;
ALTER TABLE overtime_credits
    ADD COLUMN IF NOT EXISTS migrated_from_legacy BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE overtime_credits
SET credited_minutes = GREATEST(0, ROUND(hours * 60)::INTEGER)
WHERE credited_minutes IS NULL;

UPDATE overtime_credits
SET credited_end_at_instant = end_at_instant,
    credited_start_at_instant = end_at_instant - (credited_minutes * INTERVAL '1 minute')
WHERE start_at_instant IS NOT NULL
  AND end_at_instant IS NOT NULL
  AND credited_minutes > 0
  AND credited_start_at_instant IS NULL
  AND credited_end_at_instant IS NULL;

ALTER TABLE overtime_usages
    ADD COLUMN IF NOT EXISTS requested_minutes INTEGER;
UPDATE overtime_usages
SET requested_minutes = GREATEST(0, ROUND(hours * 60)::INTEGER)
WHERE requested_minutes IS NULL;

ALTER TABLE overtime_allocations
    ADD COLUMN IF NOT EXISTS allocated_minutes INTEGER;
ALTER TABLE overtime_allocations
    ADD COLUMN IF NOT EXISTS start_at_instant TIMESTAMPTZ;
ALTER TABLE overtime_allocations
    ADD COLUMN IF NOT EXISTS end_at_instant TIMESTAMPTZ;
ALTER TABLE overtime_allocations
    ADD COLUMN IF NOT EXISTS source_timezone VARCHAR(80);
ALTER TABLE overtime_allocations
    ADD COLUMN IF NOT EXISTS reconstructed BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE overtime_allocations
SET allocated_minutes = GREATEST(0, ROUND(hours * 60)::INTEGER)
WHERE allocated_minutes IS NULL;

-- Reconstruct exact ranges for allocations whose source credit already has an absolute credited interval.
WITH ordered AS (
    SELECT a.id,
           c.credited_start_at_instant,
           c.source_timezone,
           a.allocated_minutes,
           COALESCE(
               SUM(a.allocated_minutes) OVER (
                   PARTITION BY a.credit_id
                   ORDER BY u.usage_date, a.id
                   ROWS BETWEEN UNBOUNDED PRECEDING AND 1 PRECEDING
               ), 0
           ) AS previous_minutes
    FROM overtime_allocations a
    JOIN overtime_credits c ON c.id = a.credit_id
    JOIN overtime_usages u ON u.id = a.usage_id
    WHERE c.credited_start_at_instant IS NOT NULL
      AND c.credited_end_at_instant IS NOT NULL
)
UPDATE overtime_allocations a
SET start_at_instant = o.credited_start_at_instant + (o.previous_minutes * INTERVAL '1 minute'),
    end_at_instant = o.credited_start_at_instant + ((o.previous_minutes + o.allocated_minutes) * INTERVAL '1 minute'),
    source_timezone = o.source_timezone,
    reconstructed = TRUE
FROM ordered o
WHERE a.id = o.id
  AND a.start_at_instant IS NULL
  AND a.end_at_instant IS NULL;

ALTER TABLE overtime_credits
    DROP CONSTRAINT IF EXISTS chk_overtime_credited_interval_pair;
ALTER TABLE overtime_credits
    ADD CONSTRAINT chk_overtime_credited_interval_pair CHECK (
        (credited_start_at_instant IS NULL AND credited_end_at_instant IS NULL)
        OR
        (credited_start_at_instant IS NOT NULL AND credited_end_at_instant IS NOT NULL
            AND credited_end_at_instant > credited_start_at_instant)
    );

ALTER TABLE overtime_allocations
    DROP CONSTRAINT IF EXISTS chk_overtime_allocation_interval_pair;
ALTER TABLE overtime_allocations
    ADD CONSTRAINT chk_overtime_allocation_interval_pair CHECK (
        (start_at_instant IS NULL AND end_at_instant IS NULL)
        OR
        (start_at_instant IS NOT NULL AND end_at_instant IS NOT NULL
            AND end_at_instant > start_at_instant)
    );

CREATE INDEX IF NOT EXISTS idx_overtime_credits_credited_interval
    ON overtime_credits(user_id, credited_start_at_instant, credited_end_at_instant)
    WHERE credited_start_at_instant IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_overtime_allocations_interval
    ON overtime_allocations(credit_id, start_at_instant, end_at_instant)
    WHERE start_at_instant IS NOT NULL;
