CREATE TABLE calendar_layer_overrides (
    id BIGSERIAL PRIMARY KEY,
    layer_id BIGINT NOT NULL REFERENCES calendar_layers(id) ON DELETE CASCADE,
    source_date DATE NOT NULL,
    kind VARCHAR(16) NOT NULL,
    reason VARCHAR(24),
    shift_type_id BIGINT REFERENCES shift_types(id),
    start_time TIME,
    end_time TIME,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_calendar_layer_override_date UNIQUE (layer_id, source_date),
    CONSTRAINT ck_calendar_layer_override_kind CHECK (kind IN ('WORK', 'OFF')),
    CONSTRAINT ck_calendar_layer_override_reason CHECK (
        reason IS NULL OR reason IN ('TIME_OFF', 'VACATION', 'SICK', 'OTHER')
    ),
    CONSTRAINT ck_calendar_layer_override_shape CHECK (
        (
            kind = 'OFF'
            AND shift_type_id IS NULL
            AND start_time IS NULL
            AND end_time IS NULL
        )
        OR
        (
            kind = 'WORK'
            AND shift_type_id IS NOT NULL
            AND reason IS NULL
            AND (
                (start_time IS NULL AND end_time IS NULL)
                OR
                (start_time IS NOT NULL AND end_time IS NOT NULL)
            )
        )
    )
);

CREATE INDEX idx_calendar_layer_overrides_layer_date
    ON calendar_layer_overrides(layer_id, source_date);
