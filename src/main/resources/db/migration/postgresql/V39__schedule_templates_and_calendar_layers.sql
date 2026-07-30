CREATE TABLE schedule_templates (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(400),
    alignment_mode VARCHAR(20) NOT NULL DEFAULT 'CYCLE_START',
    system_preset BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_schedule_templates_user_name UNIQUE (user_id, name),
    CONSTRAINT ck_schedule_templates_alignment CHECK (alignment_mode IN ('CYCLE_START', 'WEEKDAY'))
);

CREATE TABLE schedule_template_steps (
    id BIGSERIAL PRIMARY KEY,
    template_id BIGINT NOT NULL REFERENCES schedule_templates(id) ON DELETE CASCADE,
    position INTEGER NOT NULL,
    shift_type_id BIGINT NOT NULL REFERENCES shift_types(id),
    CONSTRAINT uk_schedule_template_step_position UNIQUE (template_id, position),
    CONSTRAINT ck_schedule_template_step_position CHECK (position >= 0 AND position < 64)
);

CREATE TABLE calendar_layers (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(80) NOT NULL,
    color VARCHAR(7) NOT NULL DEFAULT '#7AB8FF',
    timezone VARCHAR(80) NOT NULL,
    visible BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    template_id BIGINT NOT NULL REFERENCES schedule_templates(id),
    anchor_date DATE NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_calendar_layers_user_name UNIQUE (user_id, name),
    CONSTRAINT ck_calendar_layers_color CHECK (color ~ '^#[0-9A-Fa-f]{6}$'),
    CONSTRAINT ck_calendar_layers_dates CHECK (end_date IS NULL OR end_date >= start_date)
);

CREATE INDEX idx_schedule_templates_owner_sort
    ON schedule_templates(user_id, sort_order, id);

CREATE INDEX idx_schedule_template_steps_template_position
    ON schedule_template_steps(template_id, position);

CREATE INDEX idx_calendar_layers_owner_visible_sort
    ON calendar_layers(user_id, visible, sort_order, id);

CREATE INDEX idx_calendar_layers_template
    ON calendar_layers(template_id);
