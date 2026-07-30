ALTER TABLE important_days
    ADD COLUMN event_type VARCHAR(24) NOT NULL DEFAULT 'IMPORTANT_DATE',
    ADD COLUMN end_date DATE,
    ADD COLUMN all_day BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN start_time TIME,
    ADD COLUMN end_time TIME,
    ADD COLUMN start_instant TIMESTAMPTZ,
    ADD COLUMN end_instant TIMESTAMPTZ,
    ADD COLUMN source_timezone VARCHAR(80),
    ADD COLUMN place VARCHAR(240),
    ADD COLUMN description TEXT,
    ADD COLUMN icon VARCHAR(32),
    ADD COLUMN category VARCHAR(80),
    ADD COLUMN reminder_offsets VARCHAR(240);

CREATE INDEX idx_important_days_user_end_date
    ON important_days(user_id, end_date);

CREATE INDEX idx_important_days_user_start_instant
    ON important_days(user_id, start_instant);

-- Historical rows remain floating single-day important dates.
UPDATE important_days
SET event_type = 'IMPORTANT_DATE',
    all_day = TRUE,
    end_date = NULL,
    start_time = NULL,
    end_time = NULL,
    start_instant = NULL,
    end_instant = NULL,
    source_timezone = NULL
WHERE event_type IS NULL OR event_type = 'IMPORTANT_DATE';
