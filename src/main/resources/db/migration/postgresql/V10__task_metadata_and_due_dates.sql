ALTER TABLE day_tasks ADD COLUMN IF NOT EXISTS category varchar(80);
ALTER TABLE day_tasks ADD COLUMN IF NOT EXISTS priority varchar(16) NOT NULL DEFAULT 'NORMAL';
ALTER TABLE day_tasks ADD COLUMN IF NOT EXISTS due_date date;
ALTER TABLE day_tasks ADD COLUMN IF NOT EXISTS due_time time;
ALTER TABLE day_tasks ADD COLUMN IF NOT EXISTS reminder_enabled boolean NOT NULL DEFAULT false;
ALTER TABLE day_tasks ADD COLUMN IF NOT EXISTS reminder_minutes_before integer;

CREATE INDEX IF NOT EXISTS idx_day_tasks_user_due_date ON day_tasks(user_id, due_date);
CREATE INDEX IF NOT EXISTS idx_day_tasks_user_category ON day_tasks(user_id, category);
