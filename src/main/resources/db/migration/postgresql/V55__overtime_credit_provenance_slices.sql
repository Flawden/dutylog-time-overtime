-- DutyLog v27.46.1 Overtime Provenance Slices.
--
-- Stores factual/classification provenance below SYSTEM_ACTUAL_WORK credits.
-- No pricing, multiplier or settlement policy belongs here.
--
-- Existing credits are intentionally NOT backfilled: historical source slices
-- cannot be reconstructed reliably from a daily aggregate alone.

CREATE TABLE overtime_credit_slices (
    id BIGSERIAL PRIMARY KEY,

    credit_id BIGINT NOT NULL
        REFERENCES overtime_credits(id)
        ON DELETE CASCADE,

    source_actual_work_interval_id BIGINT NOT NULL
        REFERENCES actual_work_intervals(id)
        ON DELETE CASCADE,

    offset_start_minutes INTEGER NOT NULL,
    minutes INTEGER NOT NULL,

    source_date DATE NOT NULL,
    source_start_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    source_end_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,

    source_start_instant TIMESTAMPTZ,
    source_end_instant TIMESTAMPTZ,
    source_timezone VARCHAR(80),

    night BOOLEAN NOT NULL,
    holiday BOOLEAN NOT NULL,

    overtime_ordinal_start_minutes INTEGER NOT NULL,

    created_at TIMESTAMPTZ NOT NULL
        DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_overtime_credit_slice_offset
        UNIQUE (credit_id, offset_start_minutes),

    CONSTRAINT ck_overtime_credit_slice_offset
        CHECK (offset_start_minutes >= 0),

    CONSTRAINT ck_overtime_credit_slice_minutes
        CHECK (minutes > 0),

    CONSTRAINT ck_overtime_credit_slice_overtime_ordinal
        CHECK (overtime_ordinal_start_minutes >= 0),

    CONSTRAINT ck_overtime_credit_slice_exact_identity
        CHECK (
            (
                source_start_instant IS NULL
                AND source_end_instant IS NULL
                AND source_timezone IS NULL
            )
            OR
            (
                source_start_instant IS NOT NULL
                AND source_end_instant IS NOT NULL
                AND source_end_instant > source_start_instant
                AND source_timezone IS NOT NULL
                AND BTRIM(source_timezone) <> ''
            )
        )
);

CREATE INDEX idx_overtime_credit_slices_credit_offset
    ON overtime_credit_slices(
        credit_id,
        offset_start_minutes,
        id
    );

CREATE INDEX idx_overtime_credit_slices_source_actual
    ON overtime_credit_slices(
        source_actual_work_interval_id,
        id
    );
