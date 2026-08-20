-- DutyLog v27.46.1 Explicit Overtime Settlement Foundation.
--
-- Business meaning lives in overtime_settlements.
-- Canonical bank consumption remains in overtime_usages + overtime_allocations.
-- Pricing and money are intentionally not introduced by this migration.

CREATE TABLE overtime_settlements (
    id BIGSERIAL PRIMARY KEY,

    user_id BIGINT NOT NULL
        REFERENCES users(id)
        ON DELETE CASCADE,

    settlement_date DATE NOT NULL,

    requested_minutes INTEGER NOT NULL,

    reason TEXT,

    created_at TIMESTAMPTZ NOT NULL
        DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMPTZ NOT NULL
        DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_overtime_settlement_minutes
        CHECK (
            requested_minutes BETWEEN 1 AND 6000
        )
);

CREATE INDEX idx_overtime_settlements_owner_date
    ON overtime_settlements(
        user_id,
        settlement_date,
        id
    );

ALTER TABLE overtime_usages
    ADD COLUMN source_settlement_id BIGINT;

ALTER TABLE overtime_usages
    DROP CONSTRAINT ck_overtime_usage_source_kind;

ALTER TABLE overtime_usages
    DROP CONSTRAINT ck_overtime_usage_source_shape;

ALTER TABLE overtime_usages
    ADD CONSTRAINT ck_overtime_usage_source_kind
        CHECK (
            source_kind IN (
                'MANUAL',
                'ABSENCE',
                'SETTLEMENT'
            )
        );

ALTER TABLE overtime_usages
    ADD CONSTRAINT fk_overtime_usage_settlement
        FOREIGN KEY (source_settlement_id)
        REFERENCES overtime_settlements(id);

ALTER TABLE overtime_usages
    ADD CONSTRAINT uq_overtime_usage_source_settlement
        UNIQUE (source_settlement_id);

ALTER TABLE overtime_usages
    ADD CONSTRAINT ck_overtime_usage_source_shape
        CHECK (
            (
                source_kind = 'MANUAL'
                AND source_absence_id IS NULL
                AND source_settlement_id IS NULL
            )
            OR
            (
                source_kind = 'ABSENCE'
                AND source_absence_id IS NOT NULL
                AND source_settlement_id IS NULL
            )
            OR
            (
                source_kind = 'SETTLEMENT'
                AND source_absence_id IS NULL
                AND source_settlement_id IS NOT NULL
            )
        );

CREATE INDEX idx_overtime_usages_settlement_source
    ON overtime_usages(
        user_id,
        source_kind,
        source_settlement_id
    );
