-- P1B3C2: reviewed classification of exact remuneration-system component versions.
-- No inferred backfill. No HOLIDAY_PAY activation or payroll money change.
CREATE TABLE article153_component_authorities (
    id BIGSERIAL PRIMARY KEY,
    component_version_id BIGINT NOT NULL REFERENCES compensation_component_versions(id),
    decision VARCHAR(16) NOT NULL,
    source_kind VARCHAR(40) NOT NULL,
    source_reference VARCHAR(500) NOT NULL,
    source_revision VARCHAR(160) NOT NULL,
    classification_basis VARCHAR(2000) NOT NULL,
    component_fingerprint VARCHAR(64) NOT NULL,
    certified_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_article153_component_version UNIQUE (component_version_id),
    CONSTRAINT ck_article153_component_decision CHECK (decision IN ('INCLUDE', 'EXCLUDE', 'UNCLASSIFIED')),
    CONSTRAINT ck_article153_component_source CHECK (source_kind IN
        ('COLLECTIVE_AGREEMENT', 'LOCAL_NORMATIVE_ACT', 'EMPLOYMENT_CONTRACT')),
    CONSTRAINT ck_article153_component_reference CHECK (length(trim(source_reference)) > 0),
    CONSTRAINT ck_article153_component_revision CHECK (length(trim(source_revision)) > 0),
    CONSTRAINT ck_article153_component_basis CHECK (length(trim(classification_basis)) > 0),
    CONSTRAINT ck_article153_component_fingerprint CHECK (component_fingerprint ~ '^[0-9a-f]{64}$')
);
