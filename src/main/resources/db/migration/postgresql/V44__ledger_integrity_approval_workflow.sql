-- DutyLog v27.27.0 Ledger Integrity & Approval Workflow.
-- Adds an approval lifecycle, explicit reserved/posted overtime usages,
-- append-only audit entries, closeable accounting periods and factual work intervals.

ALTER TABLE absence_periods DROP CONSTRAINT ck_absence_period_status;
ALTER TABLE absence_periods
    ADD CONSTRAINT ck_absence_period_status CHECK (
        status IN ('DRAFT', 'PLANNED', 'SUBMITTED', 'APPROVED', 'REJECTED', 'CANCELLED', 'COMPLETED')
    );

ALTER TABLE overtime_usages
    ADD COLUMN posting_state VARCHAR(20) NOT NULL DEFAULT 'POSTED',
    ADD CONSTRAINT ck_overtime_usage_posting_state CHECK (posting_state IN ('RESERVED', 'POSTED'));

UPDATE overtime_usages u
SET posting_state = CASE
    WHEN u.source_kind = 'ABSENCE' AND EXISTS (
        SELECT 1 FROM absence_periods p
        WHERE p.id = u.source_absence_id
          AND p.status IN ('PLANNED', 'SUBMITTED')
    ) THEN 'RESERVED'
    ELSE 'POSTED'
END;

CREATE TABLE time_ledger_entries (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    entry_kind VARCHAR(40) NOT NULL,
    source_kind VARCHAR(30) NOT NULL,
    source_id BIGINT,
    effective_date DATE NOT NULL,
    signed_minutes INTEGER NOT NULL,
    posting_state VARCHAR(20) NOT NULL,
    reversal_of_id BIGINT REFERENCES time_ledger_entries(id),
    reason VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_time_ledger_entry_state CHECK (posting_state IN ('RESERVED', 'POSTED', 'REVERSED')),
    CONSTRAINT ck_time_ledger_entry_kind CHECK (entry_kind IN (
        'ABSENCE_RESERVATION', 'ABSENCE_POSTING', 'ABSENCE_RELEASE',
        'ABSENCE_REVERSAL', 'MANUAL_ADJUSTMENT', 'MIGRATED_OPENING'
    ))
);

CREATE INDEX idx_time_ledger_entries_owner_effective
    ON time_ledger_entries(user_id, effective_date, id);
CREATE INDEX idx_time_ledger_entries_source
    ON time_ledger_entries(user_id, source_kind, source_id, id);

-- Seed one audit entry for every already linked usage so the recovered lineage
-- starts with explainable history instead of an empty audit trail.
INSERT INTO time_ledger_entries(
    user_id, entry_kind, source_kind, source_id, effective_date,
    signed_minutes, posting_state, reason
)
SELECT u.user_id,
       CASE WHEN u.posting_state = 'RESERVED' THEN 'ABSENCE_RESERVATION' ELSE 'ABSENCE_POSTING' END,
       'ABSENCE', u.source_absence_id, u.usage_date,
       -COALESCE(u.requested_minutes, 0), u.posting_state,
       COALESCE(NULLIF(u.reason, ''), 'Миграция связанного списания V44')
FROM overtime_usages u
WHERE u.source_kind = 'ABSENCE'
  AND u.source_absence_id IS NOT NULL;

CREATE TABLE time_accounting_periods (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    period_month DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    closed_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_time_accounting_period UNIQUE (user_id, period_month),
    CONSTRAINT ck_time_accounting_period_month CHECK (period_month = date_trunc('month', period_month)::date),
    CONSTRAINT ck_time_accounting_period_status CHECK (status IN ('OPEN', 'CLOSED')),
    CONSTRAINT ck_time_accounting_period_closed_at CHECK (
        (status = 'OPEN' AND closed_at IS NULL) OR (status = 'CLOSED' AND closed_at IS NOT NULL)
    )
);

CREATE INDEX idx_time_accounting_period_owner_month
    ON time_accounting_periods(user_id, period_month);

CREATE TABLE actual_work_intervals (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    work_date DATE NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    worked_minutes INTEGER NOT NULL,
    note VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_actual_work_minutes CHECK (worked_minutes BETWEEN 1 AND 2880)
);

CREATE INDEX idx_actual_work_intervals_owner_date
    ON actual_work_intervals(user_id, work_date, start_time, id);
