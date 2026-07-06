-- Быстрые сценарии переработки и корректировка дефолтного начала дневной смены.
-- Если у старого пользователя дневная смена осталась ровно 06:30–17:00,
-- переносим её на 08:30–17:00. Если пользователь уже менял время сам — не трогаем.
UPDATE shift_types
SET start_time = TIME '08:30'
WHERE name = 'Дневная'
  AND start_time = TIME '06:30'
  AND end_time = TIME '17:00';

CREATE TABLE IF NOT EXISTS quick_scenarios (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(80) NOT NULL,
    group_label VARCHAR(40),
    description VARCHAR(300),
    start_mode VARCHAR(30) NOT NULL DEFAULT 'SHIFT_END',
    end_mode VARCHAR(30) NOT NULL DEFAULT 'ADD_MINUTES',
    end_offset_minutes INTEGER NOT NULL DEFAULT 120,
    end_fixed_time TIME,
    end_next_day BOOLEAN NOT NULL DEFAULT FALSE,
    break_mode VARCHAR(30) NOT NULL DEFAULT 'ZERO',
    custom_break_minutes INTEGER NOT NULL DEFAULT 0,
    planned_mode VARCHAR(30) NOT NULL DEFAULT 'ZERO',
    custom_planned_hours DOUBLE PRECISION NOT NULL DEFAULT 0,
    reason_template VARCHAR(300),
    sort_order INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_quick_scenarios_user_order ON quick_scenarios(user_id, sort_order, id);
CREATE INDEX IF NOT EXISTS idx_quick_scenarios_user_name ON quick_scenarios(user_id, name);
