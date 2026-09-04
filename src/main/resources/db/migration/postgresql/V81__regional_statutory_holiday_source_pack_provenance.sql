-- P1B2C2: exact reviewed source-pack provenance for regional statutory holiday datasets.
-- Existing P1B2C1 rows are deliberately not backfilled: without exact reviewed
-- source-pack identity they fail closed at the service authority boundary.

ALTER TABLE regional_statutory_holiday_datasets
    ADD COLUMN source_pack_schema VARCHAR(96),
    ADD COLUMN source_pack_sha256 VARCHAR(64),
    ADD COLUMN completeness_evidence VARCHAR(1000);

ALTER TABLE regional_statutory_holiday_datasets
    ADD CONSTRAINT uq_regional_holiday_source_pack_sha256
        UNIQUE (source_pack_sha256);

ALTER TABLE regional_statutory_holiday_datasets
    ADD CONSTRAINT ck_regional_holiday_source_pack_sha256
        CHECK (
            source_pack_sha256 IS NULL
            OR source_pack_sha256 ~ '^[0-9a-f]{64}$'
        );

ALTER TABLE regional_statutory_holiday_datasets
    ADD CONSTRAINT ck_regional_holiday_source_pack_provenance_all_or_none
        CHECK (
            (
                source_pack_schema IS NULL
                AND source_pack_sha256 IS NULL
                AND completeness_evidence IS NULL
            )
            OR
            (
                source_pack_schema IS NOT NULL
                AND source_pack_sha256 IS NOT NULL
                AND completeness_evidence IS NOT NULL
            )
        );

COMMENT ON COLUMN regional_statutory_holiday_datasets.source_pack_schema IS
    'Strict reviewed source-pack schema. P1B2C2 supports DUTYLOG_REGIONAL_STATUTORY_HOLIDAY_SOURCE_PACK_V1.';

COMMENT ON COLUMN regional_statutory_holiday_datasets.source_pack_sha256 IS
    'SHA-256 of the exact reviewed source-pack bytes, distinct from semantic dataset fingerprint.';

COMMENT ON COLUMN regional_statutory_holiday_datasets.completeness_evidence IS
    'Audit evidence supporting the declared regional legal coverage completeness; required for trusted imported datasets.';

-- No source-pack rows are seeded.
-- No existing dataset provenance is invented.
-- No ProductionCalendar data is used as legal source-pack provenance.
