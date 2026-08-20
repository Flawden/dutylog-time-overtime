-- DutyLog v27.46.1 Ordinary Work Premium Payroll Snapshot.
--
-- 6G3D1 established deterministic deep source/pricing identity.
-- V61 gives immutable Payroll revisions dedicated storage for:
--   * canonical ordinary-work minutes considered by premium pricing,
--   * reference ordinary base money used only for explainability,
--   * additive NIGHT / HOLIDAY premium delta,
--   * deterministic deep ordinary-pricing fingerprint.
--
-- Historical revisions predate this immutable component and therefore retain
-- their original total_pay_minor while receiving a neutral 0 / NULL backfill.

ALTER TABLE payroll_snapshots
    ADD COLUMN ordinary_premium_minutes INTEGER,
    ADD COLUMN ordinary_premium_reference_base_pay_minor BIGINT,
    ADD COLUMN ordinary_premium_pay_minor BIGINT,
    ADD COLUMN ordinary_premium_pricing_fingerprint VARCHAR(64);

UPDATE payroll_snapshots
SET ordinary_premium_minutes = 0,
    ordinary_premium_reference_base_pay_minor = 0,
    ordinary_premium_pay_minor = 0,
    ordinary_premium_pricing_fingerprint = NULL;

ALTER TABLE payroll_snapshots
    ALTER COLUMN ordinary_premium_minutes SET NOT NULL,
    ALTER COLUMN ordinary_premium_reference_base_pay_minor SET NOT NULL,
    ALTER COLUMN ordinary_premium_pay_minor SET NOT NULL,

    ADD CONSTRAINT ck_payroll_snapshot_ordinary_premium_values CHECK (
        ordinary_premium_minutes >= 0
        AND ordinary_premium_reference_base_pay_minor >= 0
        AND ordinary_premium_pay_minor >= 0
    ),

    ADD CONSTRAINT ck_payroll_snapshot_ordinary_premium_empty CHECK (
        ordinary_premium_minutes > 0
        OR (
            ordinary_premium_reference_base_pay_minor = 0
            AND ordinary_premium_pay_minor = 0
            AND ordinary_premium_pricing_fingerprint IS NULL
        )
    ),

    ADD CONSTRAINT ck_payroll_snapshot_ordinary_premium_fingerprint CHECK (
        ordinary_premium_pricing_fingerprint IS NULL
        OR ordinary_premium_pricing_fingerprint ~ '^[0-9a-f]{64}$'
    ),

    ADD CONSTRAINT ck_payroll_snapshot_ordinary_premium_money_identity CHECK (
        ordinary_premium_pay_minor = 0
        OR ordinary_premium_pricing_fingerprint IS NOT NULL
    );
