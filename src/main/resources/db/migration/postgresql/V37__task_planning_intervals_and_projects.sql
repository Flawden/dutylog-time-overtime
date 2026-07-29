ALTER TABLE day_tasks
    ADD COLUMN project VARCHAR(80),
    ADD COLUMN all_day BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN scheduled_start_date DATE,
    ADD COLUMN scheduled_start_time TIME,
    ADD COLUMN scheduled_end_date DATE,
    ADD COLUMN scheduled_end_time TIME,
    ADD COLUMN scheduled_start_instant TIMESTAMPTZ,
    ADD COLUMN scheduled_end_instant TIMESTAMPTZ,
    ADD COLUMN scheduled_source_timezone VARCHAR(80),
    ADD COLUMN scheduled_source_start_date DATE,
    ADD COLUMN scheduled_source_start_time TIME,
    ADD COLUMN scheduled_source_end_date DATE,
    ADD COLUMN scheduled_source_end_time TIME;

CREATE INDEX idx_day_tasks_user_project ON day_tasks(user_id, project);
CREATE INDEX idx_day_tasks_user_schedule_start ON day_tasks(user_id, scheduled_start_instant);
CREATE INDEX idx_day_tasks_user_schedule_end ON day_tasks(user_id, scheduled_end_instant);

-- Existing tasks remain floating all-day tasks on their organisational date.
UPDATE day_tasks
SET all_day = TRUE
WHERE all_day IS NULL;
