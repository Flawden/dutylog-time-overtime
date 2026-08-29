-- DutyLog v27.48.0 8A4F3F2 — immutable paragraph-15 scheduled-work FACT freeze.
--
-- New Payroll revisions freeze explicit effective-dated accounting-mode identity
-- together with schedule/fact relation. Legacy Payroll snapshots intentionally
-- receive NO backfill: aggregate planned/worked minutes cannot prove
-- PLANNED_AND_WORKED because off-schedule/overtime work can erase an absence.
--
-- worked_outside_plan_minutes is preserved independently and must never be
-- merged into paragraph-15 worked-inside-schedule time. This migration does not
-- implement P15 policy/formula/money and must not be reused as the paragraph-13
-- actual-work-hour denominator.

CREATE TABLE payroll_snapshot_p15_work_time_manifests (
    snapshot_id BIGINT PRIMARY KEY
        REFERENCES payroll_snapshots(id) ON DELETE CASCADE,
    complete BOOLEAN NOT NULL,
    candidate_day_count INTEGER NOT NULL,
    fact_count INTEGER NOT NULL,
    exact_fact_count INTEGER NOT NULL,
    fingerprint VARCHAR(64) NOT NULL,

    CONSTRAINT ck_payroll_snapshot_p15_work_manifest_counts
        CHECK (
            candidate_day_count >= 0
            AND fact_count >= 0
            AND exact_fact_count >= 0
            AND fact_count <= candidate_day_count
            AND exact_fact_count <= fact_count
            AND complete = (
                candidate_day_count = fact_count
                AND fact_count = exact_fact_count
            )
        ),

    CONSTRAINT ck_payroll_snapshot_p15_work_manifest_fingerprint
        CHECK (fingerprint ~ '^[0-9a-f]{64}$')
);

CREATE TABLE payroll_snapshot_p15_scheduled_work_facts (
    id BIGSERIAL PRIMARY KEY,
    snapshot_id BIGINT NOT NULL
        REFERENCES payroll_snapshots(id) ON DELETE CASCADE,
    fact_index INTEGER NOT NULL,
    source_date DATE NOT NULL,

    -- Scalar immutable copy of F3F1 authority; deliberately not a FK to the
    -- mutable effective-dated configuration row.
    work_time_accounting_term_id BIGINT NOT NULL,
    work_time_accounting_effective_from DATE NOT NULL,
    accounting_mode VARCHAR(16) NOT NULL,

    source_kind VARCHAR(24) NOT NULL,

    -- Canonical Payroll-source context is frozen for explainability only.
    payroll_planned_minutes INTEGER NOT NULL,
    payroll_worked_minutes INTEGER NOT NULL,
    payroll_hourly_base_worked_minutes INTEGER NOT NULL,

    -- P15 schedule/fact relation. Outside-plan work remains separate by design.
    schedule_minutes INTEGER NOT NULL,
    planned_and_worked_minutes INTEGER NOT NULL,
    planned_not_worked_minutes INTEGER NOT NULL,
    worked_outside_plan_minutes INTEGER NOT NULL,

    source_identity_exact BOOLEAN NOT NULL,
    planned_day_entry_ids TEXT NOT NULL,
    actual_work_interval_ids TEXT NOT NULL,
    source_fingerprint VARCHAR(64) NOT NULL,

    CONSTRAINT uq_payroll_snapshot_p15_work_fact_order
        UNIQUE (snapshot_id, fact_index),

    CONSTRAINT uq_payroll_snapshot_p15_work_fact_date
        UNIQUE (snapshot_id, source_date),

    CONSTRAINT ck_payroll_snapshot_p15_work_fact_index
        CHECK (fact_index >= 0),

    CONSTRAINT ck_payroll_snapshot_p15_work_mode_identity
        CHECK (
            work_time_accounting_term_id > 0
            AND work_time_accounting_effective_from <= source_date
            AND accounting_mode IN ('DAILY', 'SUMMARIZED')
        ),

    CONSTRAINT ck_payroll_snapshot_p15_work_source_kind
        CHECK (source_kind IN ('PLAN_DERIVED', 'EXPLICIT_ACTUAL')),

    CONSTRAINT ck_payroll_snapshot_p15_work_payroll_minutes
        CHECK (
            payroll_planned_minutes >= 0
            AND payroll_worked_minutes >= 0
            AND payroll_hourly_base_worked_minutes >= 0
            AND payroll_hourly_base_worked_minutes <= payroll_worked_minutes
        ),

    CONSTRAINT ck_payroll_snapshot_p15_work_relation_minutes
        CHECK (
            schedule_minutes >= 0
            AND planned_and_worked_minutes >= 0
            AND planned_not_worked_minutes >= 0
            AND worked_outside_plan_minutes >= 0
            AND schedule_minutes =
                planned_and_worked_minutes + planned_not_worked_minutes
        ),

    CONSTRAINT ck_payroll_snapshot_p15_work_planned_ids
        CHECK (
            planned_day_entry_ids = ''
            OR planned_day_entry_ids ~ '^[1-9][0-9]*(,[1-9][0-9]*)*$'
        ),

    CONSTRAINT ck_payroll_snapshot_p15_work_actual_ids
        CHECK (
            actual_work_interval_ids = ''
            OR actual_work_interval_ids ~ '^[1-9][0-9]*(,[1-9][0-9]*)*$'
        ),

    CONSTRAINT ck_payroll_snapshot_p15_work_source_fingerprint
        CHECK (source_fingerprint ~ '^[0-9a-f]{64}$')
);

CREATE INDEX idx_payroll_snapshot_p15_work_facts_snapshot
    ON payroll_snapshot_p15_scheduled_work_facts(snapshot_id, fact_index);
