-- DutyLog v27.45.1 Native Workday Closed Loop acceptance hotfix.
-- Explicit factual work owns its unpaid break; overtime derived from that fact is
-- identified separately from user-entered/manual credits so reconciliation is idempotent.

ALTER TABLE actual_work_intervals
    ADD COLUMN break_minutes INTEGER NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_actual_work_break_minutes CHECK (break_minutes BETWEEN 0 AND 1440);

ALTER TABLE overtime_credits
    ADD COLUMN source_kind VARCHAR(30) NOT NULL DEFAULT 'MANUAL',
    ADD CONSTRAINT ck_overtime_credit_source_kind CHECK (source_kind IN ('MANUAL', 'SYSTEM_ACTUAL_WORK'));

CREATE UNIQUE INDEX uq_overtime_credit_system_actual_day
    ON overtime_credits(user_id, work_date)
    WHERE source_kind = 'SYSTEM_ACTUAL_WORK';
