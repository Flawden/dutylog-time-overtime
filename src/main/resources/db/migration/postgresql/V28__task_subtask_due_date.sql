ALTER TABLE task_subtasks
    ADD COLUMN due_date DATE;

CREATE INDEX idx_task_subtasks_due_date
    ON task_subtasks(task_id, due_date)
    WHERE due_date IS NOT NULL;
