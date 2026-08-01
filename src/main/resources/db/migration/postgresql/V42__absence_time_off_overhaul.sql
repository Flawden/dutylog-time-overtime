ALTER TABLE vacation_settings
    ADD COLUMN time_off_balance_minutes INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN default_time_off_day_minutes INTEGER NOT NULL DEFAULT 480,
    ADD CONSTRAINT ck_time_off_balance_minutes CHECK (time_off_balance_minutes BETWEEN 0 AND 600000),
    ADD CONSTRAINT ck_default_time_off_day_minutes CHECK (default_time_off_day_minutes BETWEEN 15 AND 1440);

ALTER TABLE absence_types
    ADD COLUMN balance_policy VARCHAR(30) NOT NULL DEFAULT 'NONE',
    ADD COLUMN full_day_replaces_shift BOOLEAN NOT NULL DEFAULT TRUE,
    ADD CONSTRAINT ck_absence_type_balance_policy CHECK (balance_policy IN ('VACATION_DAYS', 'TIME_OFF_HOURS', 'NONE'));

UPDATE absence_types
SET balance_policy = CASE
    WHEN system_code = 'VACATION' OR counts_against_allowance THEN 'VACATION_DAYS'
    ELSE 'NONE'
END;

INSERT INTO absence_types(user_id, name, color, counts_against_allowance, system_preset, system_code,
                          sort_order, balance_policy, full_day_replaces_shift)
SELECT u.id, 'Отгул', '#4A90E2', FALSE, TRUE, 'TIME_OFF', 20, 'TIME_OFF_HOURS', TRUE
FROM users u
WHERE NOT EXISTS (
    SELECT 1 FROM absence_types t WHERE t.user_id = u.id AND t.system_code = 'TIME_OFF'
);

UPDATE absence_types SET sort_order = 30 WHERE system_code = 'SICK';
UPDATE absence_types SET sort_order = 40 WHERE system_code = 'UNPAID';
UPDATE absence_types SET sort_order = 50 WHERE system_code = 'OTHER';

ALTER TABLE absence_periods
    ADD COLUMN coverage VARCHAR(20) NOT NULL DEFAULT 'FULL_DAY',
    ADD COLUMN start_time TIME,
    ADD COLUMN end_time TIME,
    ADD COLUMN charged_minutes INTEGER NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_absence_period_coverage CHECK (coverage IN ('FULL_DAY', 'PARTIAL')),
    ADD CONSTRAINT ck_absence_period_charged_minutes CHECK (charged_minutes BETWEEN 0 AND 600000),
    ADD CONSTRAINT ck_absence_period_partial_shape CHECK (
        (coverage = 'FULL_DAY' AND start_time IS NULL AND end_time IS NULL)
        OR
        (coverage = 'PARTIAL' AND start_date = end_date AND start_time IS NOT NULL AND end_time IS NOT NULL AND end_time > start_time)
    );

CREATE INDEX idx_absence_periods_owner_coverage
    ON absence_periods(user_id, coverage, start_date, end_date);
