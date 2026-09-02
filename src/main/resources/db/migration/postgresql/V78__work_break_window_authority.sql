-- DutyLog 8A4F3U1A
-- Explicit unpaid-break authority foundation.
--
-- Existing rows intentionally keep LEGACY_EARLY_TOTAL semantics. Their scalar
-- break minute totals do not contain enough evidence to reconstruct historical
-- break placement safely.
--
-- New explicit windows will be wired in later U stages and can coexist with
-- legacy rows without inventing historical timestamps.

ALTER TABLE shift_types
    ADD COLUMN break_authority VARCHAR(32) NOT NULL DEFAULT 'LEGACY_EARLY_TOTAL';

ALTER TABLE shift_types
    ADD CONSTRAINT ck_shift_type_break_authority
    CHECK (break_authority IN ('LEGACY_EARLY_TOTAL', 'EXPLICIT_WINDOWS'));

ALTER TABLE day_entries
    ADD COLUMN shift_break_authority VARCHAR(32) NOT NULL DEFAULT 'LEGACY_EARLY_TOTAL';

ALTER TABLE day_entries
    ADD CONSTRAINT ck_day_entry_shift_break_authority
    CHECK (shift_break_authority IN ('LEGACY_EARLY_TOTAL', 'EXPLICIT_WINDOWS'));

ALTER TABLE actual_work_intervals
    ADD COLUMN break_authority VARCHAR(32) NOT NULL DEFAULT 'LEGACY_EARLY_TOTAL';

ALTER TABLE actual_work_intervals
    ADD CONSTRAINT ck_actual_work_break_authority
    CHECK (break_authority IN ('LEGACY_EARLY_TOTAL', 'EXPLICIT_WINDOWS'));

CREATE TABLE shift_type_break_windows (
    id BIGSERIAL PRIMARY KEY,
    shift_type_id BIGINT NOT NULL REFERENCES shift_types(id) ON DELETE CASCADE,
    position INTEGER NOT NULL,
    start_offset_minutes INTEGER NOT NULL,
    duration_minutes INTEGER NOT NULL,
    CONSTRAINT uq_shift_type_break_window_position
        UNIQUE (shift_type_id, position),
    CONSTRAINT ck_shift_type_break_window_position
        CHECK (position >= 0),
    CONSTRAINT ck_shift_type_break_window_offset
        CHECK (start_offset_minutes >= 0 AND start_offset_minutes < 1440),
    CONSTRAINT ck_shift_type_break_window_duration
        CHECK (duration_minutes > 0 AND duration_minutes <= 1440),
    CONSTRAINT ck_shift_type_break_window_bounds
        CHECK (start_offset_minutes + duration_minutes <= 1440)
);

CREATE INDEX idx_shift_type_break_windows_shift
    ON shift_type_break_windows(shift_type_id, position);

CREATE TABLE day_entry_shift_break_windows (
    id BIGSERIAL PRIMARY KEY,
    day_entry_id BIGINT NOT NULL REFERENCES day_entries(id) ON DELETE CASCADE,
    position INTEGER NOT NULL,
    source_start_local TIMESTAMP NOT NULL,
    source_end_local TIMESTAMP NOT NULL,
    start_instant TIMESTAMPTZ NOT NULL,
    end_instant TIMESTAMPTZ NOT NULL,
    source_timezone VARCHAR(80) NOT NULL,
    CONSTRAINT uq_day_entry_shift_break_window_position
        UNIQUE (day_entry_id, position),
    CONSTRAINT ck_day_entry_shift_break_window_position
        CHECK (position >= 0),
    CONSTRAINT ck_day_entry_shift_break_window_local
        CHECK (source_end_local > source_start_local),
    CONSTRAINT ck_day_entry_shift_break_window_instant
        CHECK (end_instant > start_instant),
    CONSTRAINT ck_day_entry_shift_break_window_timezone
        CHECK (BTRIM(source_timezone) <> '')
);

CREATE INDEX idx_day_entry_shift_break_windows_entry
    ON day_entry_shift_break_windows(day_entry_id, position);

CREATE TABLE actual_work_break_windows (
    id BIGSERIAL PRIMARY KEY,
    actual_work_interval_id BIGINT NOT NULL
        REFERENCES actual_work_intervals(id) ON DELETE CASCADE,
    position INTEGER NOT NULL,
    source_start_local TIMESTAMP NOT NULL,
    source_end_local TIMESTAMP NOT NULL,
    start_instant TIMESTAMPTZ NOT NULL,
    end_instant TIMESTAMPTZ NOT NULL,
    source_timezone VARCHAR(80) NOT NULL,
    CONSTRAINT uq_actual_work_break_window_position
        UNIQUE (actual_work_interval_id, position),
    CONSTRAINT ck_actual_work_break_window_position
        CHECK (position >= 0),
    CONSTRAINT ck_actual_work_break_window_local
        CHECK (source_end_local > source_start_local),
    CONSTRAINT ck_actual_work_break_window_instant
        CHECK (end_instant > start_instant),
    CONSTRAINT ck_actual_work_break_window_timezone
        CHECK (BTRIM(source_timezone) <> '')
);

CREATE INDEX idx_actual_work_break_windows_interval
    ON actual_work_break_windows(actual_work_interval_id, position);
