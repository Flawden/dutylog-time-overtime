ALTER TABLE shift_types
    ADD COLUMN start_time TIME,
    ADD COLUMN end_time TIME,
    ADD COLUMN break_minutes INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN planned_hours DOUBLE PRECISION;

UPDATE shift_types
SET start_time = TIME '08:30',
    end_time = TIME '17:00',
    break_minutes = 30,
    planned_hours = 8
WHERE name = 'Дневная'
  AND start_time IS NULL
  AND end_time IS NULL;

UPDATE shift_types
SET start_time = TIME '20:00',
    end_time = TIME '08:00',
    break_minutes = 60,
    planned_hours = 11
WHERE name = 'Ночная'
  AND start_time IS NULL
  AND end_time IS NULL;

UPDATE shift_types
SET planned_hours = hours
WHERE planned_hours IS NULL;
