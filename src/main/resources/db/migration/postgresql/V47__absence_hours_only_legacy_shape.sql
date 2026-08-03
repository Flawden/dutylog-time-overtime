-- DutyLog v27.31.0 Canonical Absence Ledger & Legacy Retirement.
-- V42 remains immutable. Extend only the absence coverage/shape constraints so a
-- migrated legacy usage can preserve a known charged duration without inventing
-- an unknown start/end interval.

ALTER TABLE absence_periods
    DROP CONSTRAINT ck_absence_period_partial_shape,
    DROP CONSTRAINT ck_absence_period_coverage;

ALTER TABLE absence_periods
    ADD CONSTRAINT ck_absence_period_coverage
        CHECK (coverage IN ('FULL_DAY', 'PARTIAL', 'HOURS_ONLY')),
    ADD CONSTRAINT ck_absence_period_partial_shape CHECK (
        (coverage = 'FULL_DAY' AND start_time IS NULL AND end_time IS NULL)
        OR
        (coverage = 'PARTIAL' AND start_date = end_date
            AND start_time IS NOT NULL AND end_time IS NOT NULL AND end_time > start_time)
        OR
        (coverage = 'HOURS_ONLY' AND start_date = end_date
            AND start_time IS NULL AND end_time IS NULL)
    );
