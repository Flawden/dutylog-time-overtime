-- Append-only vacation-pay ledger.
-- Each row in vacation_pay_receipts is one actual incoming bank transfer.
-- Calculation evidence is stored separately and can be referenced by many transfers.
-- VOID is an accounting cancellation; REFUND is a real outgoing cash transfer.
ALTER TABLE absence_periods
    ADD CONSTRAINT uq_absence_periods_id_user_for_vacation_receipt
    UNIQUE (id, user_id);

CREATE TABLE vacation_pay_calculation_snapshots (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    absence_period_id BIGINT NOT NULL,

    absence_status VARCHAR(20) NOT NULL
        CHECK (absence_status IN ('APPROVED', 'COMPLETED')),
    absence_type_system_code VARCHAR(30) NOT NULL
        CHECK (absence_type_system_code = 'VACATION'),
    absence_type_balance_policy VARCHAR(30) NOT NULL
        CHECK (absence_type_balance_policy = 'VACATION_DAYS'),
    absence_coverage VARCHAR(20) NOT NULL
        CHECK (absence_coverage = 'FULL_DAY'),
    absence_compensation_policy VARCHAR(30) NOT NULL
        CHECK (absence_compensation_policy = 'VACATION_ALLOWANCE'),
    calculation_event_date DATE NOT NULL,
    vacation_from DATE NOT NULL,
    vacation_to DATE NOT NULL,

    calculation_status VARCHAR(20) NOT NULL
        CHECK (calculation_status IN ('READY', 'BLOCKED')),
    calculated_gross_amount_minor BIGINT,
    payable_calendar_days INTEGER,
    calculation_schema_version VARCHAR(60) NOT NULL,
    calculation_currency_code VARCHAR(3)
        CHECK (calculation_currency_code IS NULL OR calculation_currency_code ~ '^[A-Z]{3}$'),
    calculation_payload_json TEXT NOT NULL,
    calculation_blocking_reason VARCHAR(160),

    snapshot_fingerprint VARCHAR(64) NOT NULL
        CHECK (snapshot_fingerprint ~ '^[0-9a-f]{64}$'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_vacation_pay_calculation_snapshot_owner_absence
        UNIQUE (id, user_id, absence_period_id),
    CONSTRAINT fk_vacation_pay_calculation_snapshot_owner
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_vacation_pay_calculation_snapshot_absence
        FOREIGN KEY (absence_period_id) REFERENCES absence_periods (id) ON DELETE RESTRICT,
    CONSTRAINT fk_vacation_pay_calculation_snapshot_absence_owner
        FOREIGN KEY (absence_period_id, user_id)
        REFERENCES absence_periods (id, user_id) ON DELETE RESTRICT,
    CONSTRAINT ck_vacation_pay_calculation_snapshot_span
        CHECK (vacation_to >= vacation_from),
    CONSTRAINT ck_vacation_pay_calculation_snapshot_event
        CHECK (calculation_event_date = vacation_from),
    CONSTRAINT ck_vacation_pay_calculation_snapshot_state
        CHECK (
            (
                calculation_status = 'BLOCKED'
                AND calculated_gross_amount_minor IS NULL
                AND payable_calendar_days IS NULL
                AND calculation_currency_code IS NULL
                AND length(trim(calculation_payload_json)) > 0
                AND calculation_blocking_reason IS NOT NULL
                AND length(trim(calculation_blocking_reason)) > 0
            )
            OR
            (
                calculation_status = 'READY'
                AND calculated_gross_amount_minor IS NOT NULL
                AND calculated_gross_amount_minor >= 0
                AND payable_calendar_days IS NOT NULL
                AND payable_calendar_days >= 0
                AND calculation_currency_code IS NOT NULL
                AND length(trim(calculation_payload_json)) > 0
                AND calculation_blocking_reason IS NULL
            )
        )
);

CREATE INDEX ix_vacation_pay_calculation_snapshots_owner_absence
    ON vacation_pay_calculation_snapshots (user_id, absence_period_id, id DESC);

CREATE TABLE vacation_pay_receipts (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    absence_period_id BIGINT NOT NULL,
    calculation_snapshot_id BIGINT,
    received_date DATE NOT NULL,
    actual_received_amount_minor BIGINT NOT NULL
        CHECK (actual_received_amount_minor > 0),
    -- Net currency of the actual amount credited to the user's account.
    currency_code VARCHAR(3) NOT NULL
        CHECK (currency_code ~ '^[A-Z]{3}$'),

    -- Canonical absence identity frozen for this individual bank transfer.
    absence_status VARCHAR(20) NOT NULL
        CHECK (absence_status IN ('APPROVED', 'COMPLETED')),
    absence_type_system_code VARCHAR(30) NOT NULL
        CHECK (absence_type_system_code = 'VACATION'),
    absence_type_balance_policy VARCHAR(30) NOT NULL
        CHECK (absence_type_balance_policy = 'VACATION_DAYS'),
    absence_coverage VARCHAR(20) NOT NULL
        CHECK (absence_coverage = 'FULL_DAY'),
    absence_compensation_policy VARCHAR(30) NOT NULL
        CHECK (absence_compensation_policy = 'VACATION_ALLOWANCE'),
    calculation_event_date DATE NOT NULL,
    vacation_from DATE NOT NULL,
    vacation_to DATE NOT NULL,

    receipt_fingerprint VARCHAR(64) NOT NULL
        CHECK (receipt_fingerprint ~ '^[0-9a-f]{64}$'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_vacation_pay_receipt_id_owner
        UNIQUE (id, user_id),
    CONSTRAINT fk_vacation_pay_receipt_owner
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_vacation_pay_receipt_absence
        FOREIGN KEY (absence_period_id) REFERENCES absence_periods (id) ON DELETE RESTRICT,
    CONSTRAINT fk_vacation_pay_receipt_absence_owner
        FOREIGN KEY (absence_period_id, user_id)
        REFERENCES absence_periods (id, user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_vacation_pay_receipt_calculation_snapshot
        FOREIGN KEY (calculation_snapshot_id, user_id, absence_period_id)
        REFERENCES vacation_pay_calculation_snapshots (id, user_id, absence_period_id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_vacation_pay_receipt_span
        CHECK (vacation_to >= vacation_from),
    CONSTRAINT ck_vacation_pay_receipt_event
        CHECK (calculation_event_date = vacation_from)
);

CREATE INDEX ix_vacation_pay_receipts_owner_received
    ON vacation_pay_receipts (user_id, received_date DESC, id DESC);
CREATE INDEX ix_vacation_pay_receipts_owner_absence
    ON vacation_pay_receipts (user_id, absence_period_id, id DESC);

CREATE TABLE vacation_pay_receipt_adjustments (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    receipt_id BIGINT NOT NULL,
    adjustment_type VARCHAR(12) NOT NULL
        CHECK (adjustment_type IN ('VOID', 'REFUND')),
    amount_minor BIGINT,
    currency_code VARCHAR(3)
        CHECK (currency_code IS NULL OR currency_code ~ '^[A-Z]{3}$'),
    event_date DATE,
    reason VARCHAR(500) NOT NULL
        CHECK (length(trim(reason)) > 0),
    replacement_receipt_id BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_vacation_pay_receipt_adjustment_owner
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_vacation_pay_receipt_adjustment_receipt_owner
        FOREIGN KEY (receipt_id, user_id)
        REFERENCES vacation_pay_receipts (id, user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_vacation_pay_receipt_adjustment_replacement_owner
        FOREIGN KEY (replacement_receipt_id, user_id)
        REFERENCES vacation_pay_receipts (id, user_id) ON DELETE RESTRICT,
    CONSTRAINT ck_vacation_pay_receipt_adjustment_replacement_order
        CHECK (replacement_receipt_id IS NULL OR replacement_receipt_id > receipt_id),
    CONSTRAINT ck_vacation_pay_receipt_adjustment_shape
        CHECK (
            (
                adjustment_type = 'VOID'
                AND amount_minor IS NULL
                AND currency_code IS NULL
                AND event_date IS NULL
            )
            OR
            (
                adjustment_type = 'REFUND'
                AND amount_minor IS NOT NULL
                AND amount_minor > 0
                AND currency_code IS NOT NULL
                AND event_date IS NOT NULL
                AND replacement_receipt_id IS NULL
            )
        )
);

CREATE INDEX ix_vacation_pay_receipt_adjustments_receipt
    ON vacation_pay_receipt_adjustments (receipt_id, created_at, id);
CREATE UNIQUE INDEX uq_vacation_pay_receipt_void
    ON vacation_pay_receipt_adjustments (receipt_id)
    WHERE adjustment_type = 'VOID';
CREATE UNIQUE INDEX uq_vacation_pay_receipt_replacement
    ON vacation_pay_receipt_adjustments (replacement_receipt_id)
    WHERE replacement_receipt_id IS NOT NULL;

COMMENT ON TABLE vacation_pay_calculation_snapshots IS
    'Append-only versioned gross vacation-pay calculation evidence, separate from actual bank transfers.';
COMMENT ON TABLE vacation_pay_receipts IS
    'Append-only ledger of individual actual net vacation-pay bank transfers.';
COMMENT ON TABLE vacation_pay_receipt_adjustments IS
    'Append-only VOID accounting cancellations and actual REFUND cash outflows for vacation-pay receipts.';
COMMENT ON COLUMN vacation_pay_receipts.actual_received_amount_minor IS
    'Net amount actually credited to the user after withholding, in minor units of currency_code.';
COMMENT ON COLUMN vacation_pay_calculation_snapshots.calculated_gross_amount_minor IS
    'Canonical gross vacation-pay calculation before withholding, in minor units of calculation_currency_code.';
COMMENT ON COLUMN vacation_pay_receipt_adjustments.replacement_receipt_id IS
    'Optional replacement transfer created atomically when an erroneous receipt is corrected.';

-- Serialize all adjustment decisions on the original receipt row. This protects
-- against concurrent duplicate voids and concurrent partial refunds exceeding the receipt.
CREATE FUNCTION dutylog_validate_vacation_pay_receipt_adjustment()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    original_amount BIGINT;
    original_currency VARCHAR(3);
    original_absence_id BIGINT;
    existing_refunds NUMERIC(30, 0);
    replacement_absence_id BIGINT;
BEGIN
    SELECT actual_received_amount_minor, currency_code, absence_period_id
      INTO original_amount, original_currency, original_absence_id
      FROM vacation_pay_receipts
     WHERE id = NEW.receipt_id
       AND user_id = NEW.user_id
     FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Vacation-pay receipt is not owned by adjustment user'
            USING ERRCODE = '23503';
    END IF;

    IF NEW.adjustment_type = 'VOID' THEN
        IF EXISTS (
            SELECT 1
              FROM vacation_pay_receipt_adjustments
             WHERE receipt_id = NEW.receipt_id
        ) THEN
            RAISE EXCEPTION 'Receipt with existing adjustments cannot be voided'
                USING ERRCODE = '23505';
        END IF;

        IF NEW.replacement_receipt_id IS NOT NULL THEN
            SELECT absence_period_id
              INTO replacement_absence_id
              FROM vacation_pay_receipts
             WHERE id = NEW.replacement_receipt_id
               AND user_id = NEW.user_id;

            IF NOT FOUND OR replacement_absence_id <> original_absence_id THEN
                RAISE EXCEPTION 'Replacement receipt must belong to the same owner and vacation'
                    USING ERRCODE = '23514';
            END IF;

            IF EXISTS (
                SELECT 1
                  FROM vacation_pay_receipt_adjustments
                 WHERE receipt_id = NEW.replacement_receipt_id
            ) THEN
                RAISE EXCEPTION 'Replacement receipt must be a new unadjusted receipt'
                    USING ERRCODE = '23514';
            END IF;
        END IF;
    ELSIF NEW.adjustment_type = 'REFUND' THEN
        IF EXISTS (
            SELECT 1
              FROM vacation_pay_receipt_adjustments
             WHERE receipt_id = NEW.receipt_id
               AND adjustment_type = 'VOID'
        ) THEN
            RAISE EXCEPTION 'Voided receipt cannot receive a refund event'
                USING ERRCODE = '23514';
        END IF;

        IF NEW.currency_code <> original_currency THEN
            RAISE EXCEPTION 'Refund currency must match original receipt currency'
                USING ERRCODE = '23514';
        END IF;

        SELECT COALESCE(SUM(amount_minor), 0)
          INTO existing_refunds
          FROM vacation_pay_receipt_adjustments
         WHERE receipt_id = NEW.receipt_id
           AND adjustment_type = 'REFUND';

        IF existing_refunds + NEW.amount_minor > original_amount THEN
            RAISE EXCEPTION 'Refund total exceeds original receipt amount'
                USING ERRCODE = '23514';
        END IF;
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_vacation_pay_receipt_adjustment_validate
    BEFORE INSERT ON vacation_pay_receipt_adjustments
    FOR EACH ROW
    EXECUTE FUNCTION dutylog_validate_vacation_pay_receipt_adjustment();

-- JPA updatable=false is an application hint, not a database integrity boundary.
-- Protect receipts, calculation snapshots, and adjustment events at the database layer.
CREATE FUNCTION dutylog_reject_vacation_pay_ledger_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION '% is append-only; % is forbidden', TG_TABLE_NAME, TG_OP
        USING ERRCODE = '55000';
    RETURN NULL;
END;
$$;

CREATE TRIGGER trg_vacation_pay_calculation_snapshots_immutable
    BEFORE UPDATE OR DELETE ON vacation_pay_calculation_snapshots
    FOR EACH ROW
    EXECUTE FUNCTION dutylog_reject_vacation_pay_ledger_mutation();
CREATE TRIGGER trg_vacation_pay_calculation_snapshots_no_truncate
    BEFORE TRUNCATE ON vacation_pay_calculation_snapshots
    FOR EACH STATEMENT
    EXECUTE FUNCTION dutylog_reject_vacation_pay_ledger_mutation();

CREATE TRIGGER trg_vacation_pay_receipts_immutable
    BEFORE UPDATE OR DELETE ON vacation_pay_receipts
    FOR EACH ROW
    EXECUTE FUNCTION dutylog_reject_vacation_pay_ledger_mutation();
CREATE TRIGGER trg_vacation_pay_receipts_no_truncate
    BEFORE TRUNCATE ON vacation_pay_receipts
    FOR EACH STATEMENT
    EXECUTE FUNCTION dutylog_reject_vacation_pay_ledger_mutation();

CREATE TRIGGER trg_vacation_pay_receipt_adjustments_immutable
    BEFORE UPDATE OR DELETE ON vacation_pay_receipt_adjustments
    FOR EACH ROW
    EXECUTE FUNCTION dutylog_reject_vacation_pay_ledger_mutation();
CREATE TRIGGER trg_vacation_pay_receipt_adjustments_no_truncate
    BEFORE TRUNCATE ON vacation_pay_receipt_adjustments
    FOR EACH STATEMENT
    EXECUTE FUNCTION dutylog_reject_vacation_pay_ledger_mutation();
