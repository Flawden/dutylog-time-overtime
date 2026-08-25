ALTER TABLE payroll_snapshot_earning_manifests
    ADD COLUMN unclassified_amount_minor BIGINT NOT NULL DEFAULT 0;

ALTER TABLE payroll_snapshot_earning_manifests
    ADD CONSTRAINT ck_payroll_snapshot_earning_manifest_unclassified_amount
        CHECK (
            unclassified_amount_minor >= 0
        );

ALTER TABLE payroll_snapshot_earning_manifests
    ADD CONSTRAINT ck_payroll_snapshot_earning_manifest_complete_unclassified
        CHECK (
            NOT complete
            OR unclassified_amount_minor = 0
        );

ALTER TABLE payroll_snapshot_earning_manifests
    ALTER COLUMN unclassified_amount_minor DROP DEFAULT;
