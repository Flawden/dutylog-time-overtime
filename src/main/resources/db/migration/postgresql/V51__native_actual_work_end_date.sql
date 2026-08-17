-- DutyLog v27.45.1 Native Workday cross-midnight acceptance hotfix.
-- One factual interval keeps its start date and stores an explicit end date so
-- next-day / multi-date reality remains one source fact while read models split it by day.

ALTER TABLE actual_work_intervals
    ADD COLUMN end_date DATE;

UPDATE actual_work_intervals
SET end_date = CASE
    WHEN end_time <= start_time THEN work_date + 1
    ELSE work_date
END
WHERE end_date IS NULL;

ALTER TABLE actual_work_intervals
    ALTER COLUMN end_date SET NOT NULL,
    ADD CONSTRAINT ck_actual_work_end_date CHECK (end_date >= work_date);

CREATE INDEX idx_actual_work_intervals_owner_end_date
    ON actual_work_intervals(user_id, end_date, id);
