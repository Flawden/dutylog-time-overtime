-- Автоподсчёт переработки по интервалу работы: начало/конец, обед и плановые часы.
ALTER TABLE overtime_credits
    ADD COLUMN start_at timestamp,
    ADD COLUMN end_at timestamp,
    ADD COLUMN break_minutes integer NOT NULL DEFAULT 0,
    ADD COLUMN planned_hours double precision NOT NULL DEFAULT 0,
    ADD COLUMN calculated boolean NOT NULL DEFAULT false;

ALTER TABLE overtime_credits
    ADD CONSTRAINT chk_overtime_credit_break_minutes CHECK (break_minutes >= 0 AND break_minutes <= 1440),
    ADD CONSTRAINT chk_overtime_credit_planned_hours CHECK (planned_hours >= 0 AND planned_hours <= 100);
