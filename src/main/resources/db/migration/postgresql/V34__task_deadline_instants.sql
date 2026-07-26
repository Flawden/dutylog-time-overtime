ALTER TABLE day_tasks
    ADD COLUMN IF NOT EXISTS due_instant TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS due_source_timezone VARCHAR(80),
    ADD COLUMN IF NOT EXISTS due_source_date DATE,
    ADD COLUMN IF NOT EXISTS due_source_time TIME;

CREATE INDEX IF NOT EXISTS idx_day_tasks_user_due_instant
    ON day_tasks (user_id, due_instant);
