-- DutyLog v27.46.1 Payroll Settlement Pricing Fingerprint.
--
-- V59 froze settlement aggregate money/time.
-- V60 freezes the deterministic deep pricing/source identity used to produce
-- that aggregate, without rewriting released migrations.

ALTER TABLE payroll_snapshots
    ADD COLUMN settlement_pricing_fingerprint VARCHAR(64);

ALTER TABLE payroll_snapshots
    ADD CONSTRAINT ck_payroll_snapshot_settlement_pricing_fingerprint CHECK (
        (
            settlement_count = 0
            AND settlement_pricing_fingerprint IS NULL
        )
        OR
        (
            settlement_count > 0
            AND settlement_pricing_fingerprint ~ '^[0-9a-f]{64}$'
        )
    );
