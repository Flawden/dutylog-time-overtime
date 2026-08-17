-- DutyLog v27.45.0 Production Calendar Foundation.
-- Separates month work norm from factual absences and money rules.
-- BASE rows are reserved for official/imported sources; LOCAL_OVERRIDE wins per date.

CREATE TABLE production_calendar_days (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    calendar_date DATE NOT NULL,
    layer VARCHAR(20) NOT NULL,
    day_kind VARCHAR(32) NOT NULL,
    schedule_effect VARCHAR(24) NOT NULL,
    norm_minutes_override INTEGER,
    payroll_effect VARCHAR(24) NOT NULL,
    label VARCHAR(120),
    source_type VARCHAR(24) NOT NULL,
    source_ref VARCHAR(240),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_production_calendar_day UNIQUE (user_id, calendar_date, layer),
    CONSTRAINT ck_production_calendar_layer CHECK (layer IN ('BASE', 'LOCAL_OVERRIDE')),
    CONSTRAINT ck_production_calendar_kind CHECK (day_kind IN ('NORMAL', 'HOLIDAY', 'TRANSFERRED_DAY_OFF', 'TRANSFERRED_WORKDAY', 'SHORTENED_DAY')),
    CONSTRAINT ck_production_calendar_schedule_effect CHECK (schedule_effect IN ('NONE', 'NORM_OVERRIDE')),
    CONSTRAINT ck_production_calendar_norm_override CHECK (
        (schedule_effect = 'NONE' AND norm_minutes_override IS NULL)
        OR (schedule_effect = 'NORM_OVERRIDE' AND norm_minutes_override BETWEEN 0 AND 1440)
    ),
    CONSTRAINT ck_production_calendar_payroll_effect CHECK (payroll_effect IN ('NONE', 'HOLIDAY')),
    CONSTRAINT ck_production_calendar_source_type CHECK (source_type IN ('CUSTOM', 'OFFICIAL', 'IMPORTED'))
);

CREATE INDEX idx_production_calendar_days_owner_date
    ON production_calendar_days(user_id, calendar_date, layer);
