-- DutyLog v27.46.1 FIFO allocation credit offsets.
--
-- Materializes the already-existing canonical FIFO consumed offset so an
-- allocation can later be intersected with OvertimeCreditSlice provenance.
--
-- This does NOT alter FIFO ordering or balances.

ALTER TABLE overtime_allocations
    ADD COLUMN credit_offset_start_minutes INTEGER;

WITH ordered_allocations AS (
    SELECT
        a.id,
        COALESCE(
            SUM(
                COALESCE(
                    a.allocated_minutes,
                    GREATEST(
                        0,
                        ROUND(a.hours * 60)::INTEGER
                    )
                )
            ) OVER (
                PARTITION BY a.credit_id
                ORDER BY
                    u.usage_date ASC,
                    u.id ASC,
                    a.id ASC
                ROWS BETWEEN UNBOUNDED PRECEDING
                    AND 1 PRECEDING
            ),
            0
        )::INTEGER AS credit_offset_start_minutes
    FROM overtime_allocations a
    JOIN overtime_usages u
        ON u.id = a.usage_id
)
UPDATE overtime_allocations target
SET credit_offset_start_minutes =
        ordered.credit_offset_start_minutes
FROM ordered_allocations ordered
WHERE ordered.id = target.id;

UPDATE overtime_allocations
SET credit_offset_start_minutes = 0
WHERE credit_offset_start_minutes IS NULL;

ALTER TABLE overtime_allocations
    ALTER COLUMN credit_offset_start_minutes
        SET DEFAULT 0;

ALTER TABLE overtime_allocations
    ALTER COLUMN credit_offset_start_minutes
        SET NOT NULL;

ALTER TABLE overtime_allocations
    ADD CONSTRAINT ck_overtime_allocation_credit_offset
        CHECK (credit_offset_start_minutes >= 0);

CREATE INDEX idx_overtime_allocations_credit_offset
    ON overtime_allocations(
        credit_id,
        credit_offset_start_minutes,
        id
    );
