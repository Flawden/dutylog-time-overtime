-- DutyLog v27.26.0 Unified Time & Compensation Ledger.
-- Existing overtime accounting remains authoritative. Absences may now create
-- reversible, source-linked FIFO usages instead of consuming a parallel balance.

ALTER TABLE absence_periods
    ADD COLUMN compensation_policy VARCHAR(30) NOT NULL DEFAULT 'NONE',
    ADD COLUMN compensated_minutes INTEGER NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_absence_compensation_policy CHECK (
        compensation_policy IN ('VACATION_ALLOWANCE', 'OVERTIME_BANK', 'SICK_PAY', 'UNPAID', 'NONE')
    ),
    ADD CONSTRAINT ck_absence_compensated_minutes CHECK (compensated_minutes BETWEEN 0 AND 600000);

UPDATE absence_periods p
SET compensation_policy = CASE
    WHEN t.balance_policy = 'VACATION_DAYS' THEN 'VACATION_ALLOWANCE'
    WHEN t.balance_policy = 'TIME_OFF_HOURS' THEN 'OVERTIME_BANK'
    WHEN t.system_code = 'SICK' THEN 'SICK_PAY'
    WHEN t.system_code = 'UNPAID' THEN 'UNPAID'
    ELSE 'NONE'
END,
compensated_minutes = CASE
    WHEN t.balance_policy = 'TIME_OFF_HOURS' THEN p.charged_minutes
    ELSE 0
END
FROM absence_types t
WHERE t.id = p.absence_type_id;

ALTER TABLE overtime_usages
    ADD COLUMN source_kind VARCHAR(30) NOT NULL DEFAULT 'MANUAL',
    ADD COLUMN source_absence_id BIGINT,
    ADD CONSTRAINT ck_overtime_usage_source_kind CHECK (source_kind IN ('MANUAL', 'ABSENCE')),
    ADD CONSTRAINT fk_overtime_usage_absence
        FOREIGN KEY (source_absence_id) REFERENCES absence_periods(id) ON DELETE CASCADE,
    ADD CONSTRAINT uq_overtime_usage_source_absence UNIQUE (source_absence_id),
    ADD CONSTRAINT ck_overtime_usage_source_shape CHECK (
        (source_kind = 'MANUAL' AND source_absence_id IS NULL)
        OR
        (source_kind = 'ABSENCE' AND source_absence_id IS NOT NULL)
    );

CREATE INDEX idx_overtime_usages_source
    ON overtime_usages(user_id, source_kind, source_absence_id);

-- V42 kept a standalone opening time-off balance. Convert it once into the
-- oldest FIFO credit so no already entered hour disappears during unification.
INSERT INTO overtime_credits(
    user_id, work_date, time_range, hours, reason,
    break_minutes, planned_hours, calculated,
    credited_minutes, credited_start_at_instant, credited_end_at_instant,
    source_timezone, migrated_from_legacy
)
SELECT s.user_id,
       DATE '1970-01-01',
       'Начальный баланс',
       s.time_off_balance_minutes / 60.0,
       'Начальный баланс отгулов — перенос в единый банк V43',
       0, 0, FALSE,
       s.time_off_balance_minutes,
       TIMESTAMPTZ '1970-01-01 00:00:00+00',
       TIMESTAMPTZ '1970-01-01 00:00:00+00' + (s.time_off_balance_minutes * INTERVAL '1 minute'),
       'UTC',
       TRUE
FROM vacation_settings s
WHERE s.time_off_balance_minutes > 0;

-- Existing V42 time-off absences become source-linked usages. Allocation rows
-- are rebuilt transactionally by OvertimeService on the first account read.
INSERT INTO overtime_usages(
    user_id, usage_date, hours, requested_minutes, reason,
    source_kind, source_absence_id
)
SELECT p.user_id,
       p.start_date,
       p.charged_minutes / 60.0,
       p.charged_minutes,
       COALESCE(NULLIF(p.title, ''), 'Отгул за ранее отработанное время'),
       'ABSENCE',
       p.id
FROM absence_periods p
WHERE p.compensation_policy = 'OVERTIME_BANK'
  AND p.charged_minutes > 0
  AND NOT EXISTS (
      SELECT 1 FROM overtime_usages u WHERE u.source_absence_id = p.id
  );

UPDATE vacation_settings SET time_off_balance_minutes = 0;
