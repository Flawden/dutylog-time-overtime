-- DutyLog v27.46.1
-- Actual Work historical timezone / absolute identity foundation.
--
-- Existing rows intentionally stay legacy/null until Temporal Work Context
-- reconciliation reconstructs them.
--
-- New/edited rows will be wired to absolute identity in Step 2B1b.

ALTER TABLE actual_work_intervals
    ADD COLUMN IF NOT EXISTS source_timezone VARCHAR(80),
    ADD COLUMN IF NOT EXISTS start_instant TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS end_instant TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS identity_reconstructed BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE actual_work_intervals
    DROP CONSTRAINT IF EXISTS ck_actual_work_absolute_identity;

ALTER TABLE actual_work_intervals
    ADD CONSTRAINT ck_actual_work_absolute_identity CHECK (
        (
            source_timezone IS NULL
            AND start_instant IS NULL
            AND end_instant IS NULL
            AND identity_reconstructed = FALSE
        )
        OR
        (
            source_timezone IS NOT NULL
            AND BTRIM(source_timezone) <> ''
            AND start_instant IS NOT NULL
            AND end_instant IS NOT NULL
            AND end_instant > start_instant
        )
    );

CREATE INDEX IF NOT EXISTS idx_actual_work_intervals_owner_start_instant
    ON actual_work_intervals(user_id, start_instant, id);
