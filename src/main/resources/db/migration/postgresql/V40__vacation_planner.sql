CREATE TABLE vacation_settings (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    annual_allowance_days INTEGER NOT NULL DEFAULT 28,
    carryover_days INTEGER NOT NULL DEFAULT 0,
    count_mode VARCHAR(20) NOT NULL DEFAULT 'CALENDAR_DAYS',
    work_year_start_month INTEGER NOT NULL DEFAULT 1,
    work_year_start_day INTEGER NOT NULL DEFAULT 1,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_vacation_settings_user UNIQUE (user_id),
    CONSTRAINT ck_vacation_allowance CHECK (annual_allowance_days BETWEEN 0 AND 366),
    CONSTRAINT ck_vacation_carryover CHECK (carryover_days BETWEEN 0 AND 366),
    CONSTRAINT ck_vacation_count_mode CHECK (count_mode IN ('CALENDAR_DAYS', 'WEEKDAYS')),
    CONSTRAINT ck_vacation_work_year_month CHECK (work_year_start_month BETWEEN 1 AND 12),
    CONSTRAINT ck_vacation_work_year_day CHECK (work_year_start_day BETWEEN 1 AND 28)
);

-- Existing users receive the serialization/allowance row during migration.
-- New users are initialized lazily on their first Vacation Planner request.
INSERT INTO vacation_settings(user_id)
SELECT id FROM users
ON CONFLICT (user_id) DO NOTHING;

CREATE TABLE absence_types (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(80) NOT NULL,
    color VARCHAR(7) NOT NULL DEFAULT '#4FA3A5',
    counts_against_allowance BOOLEAN NOT NULL DEFAULT FALSE,
    system_preset BOOLEAN NOT NULL DEFAULT FALSE,
    system_code VARCHAR(30),
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_absence_types_user_name UNIQUE (user_id, name),
    CONSTRAINT uk_absence_types_user_system_code UNIQUE (user_id, system_code),
    CONSTRAINT ck_absence_type_color CHECK (color ~ '^#[0-9A-Fa-f]{6}$')
);

CREATE TABLE absence_periods (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    absence_type_id BIGINT NOT NULL REFERENCES absence_types(id),
    title VARCHAR(120),
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PLANNED',
    note VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_absence_period_dates CHECK (end_date >= start_date),
    CONSTRAINT ck_absence_period_status CHECK (status IN ('PLANNED', 'APPROVED'))
);

CREATE INDEX idx_absence_types_owner_sort
    ON absence_types(user_id, sort_order, id);

CREATE INDEX idx_absence_periods_owner_range
    ON absence_periods(user_id, start_date, end_date);

CREATE INDEX idx_absence_periods_type
    ON absence_periods(absence_type_id);
