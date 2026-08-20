-- DutyLog v27.46.1 Payroll Settlement Pricing Snapshot Foundation.
--
-- Freeze bank-first ordinary payable minutes and explicit settlement money
-- inside each immutable Payroll revision.
--
-- Released migrations remain immutable; V59 only extends payroll_snapshots.

ALTER TABLE payroll_snapshots
    ADD COLUMN hourly_base_payable_minutes INTEGER,
    ADD COLUMN settlement_count INTEGER,
    ADD COLUMN settlement_minutes INTEGER,
    ADD COLUMN settlement_base_pay_minor BIGINT,
    ADD COLUMN settlement_premium_pay_minor BIGINT,
    ADD COLUMN settlement_pay_minor BIGINT;

-- Historical revisions were produced before HOURLY bank-first separation.
-- Their canonical ordinary payable source was exactly payable_minutes.
UPDATE payroll_snapshots
SET hourly_base_payable_minutes = payable_minutes,
    settlement_count = 0,
    settlement_minutes = 0,
    settlement_base_pay_minor = 0,
    settlement_premium_pay_minor = 0,
    settlement_pay_minor = 0;

ALTER TABLE payroll_snapshots
    ALTER COLUMN hourly_base_payable_minutes SET NOT NULL,
    ALTER COLUMN settlement_count SET NOT NULL,
    ALTER COLUMN settlement_minutes SET NOT NULL,
    ALTER COLUMN settlement_base_pay_minor SET NOT NULL,
    ALTER COLUMN settlement_premium_pay_minor SET NOT NULL,
    ALTER COLUMN settlement_pay_minor SET NOT NULL,

    ADD CONSTRAINT ck_payroll_snapshot_hourly_base_minutes CHECK (
        hourly_base_payable_minutes >= 0
        AND hourly_base_payable_minutes <= payable_minutes
    ),

    ADD CONSTRAINT ck_payroll_snapshot_settlement_counts CHECK (
        settlement_count >= 0
        AND settlement_minutes >= 0
        AND (
            (settlement_count = 0 AND settlement_minutes = 0)
            OR
            (settlement_count > 0 AND settlement_minutes > 0)
        )
    ),

    ADD CONSTRAINT ck_payroll_snapshot_settlement_money CHECK (
        settlement_base_pay_minor >= 0
        AND settlement_premium_pay_minor >= 0
        AND settlement_pay_minor >= 0
        AND settlement_pay_minor =
            settlement_base_pay_minor + settlement_premium_pay_minor
        AND (
            settlement_count > 0
            OR (
                settlement_base_pay_minor = 0
                AND settlement_premium_pay_minor = 0
                AND settlement_pay_minor = 0
            )
        )
    );
