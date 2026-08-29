-- DutyLog v27.48.0 8A4F3F1 — Work-Time Accounting Regime FACT Authority.
--
-- Explicit effective-dated historical authority for the legal mode used by
-- paragraph-15 reference worked-time proportionality.
--
-- IMPORTANT: there is intentionally NO compatibility/default INSERT here.
-- DAILY/SUMMARIZED must never be inferred from SALARY/HOURLY pay mode,
-- schedule shape, TimeAccountingPeriod, account age or data availability.
-- Missing history remains UNKNOWN and downstream calculation must fail closed.

CREATE TABLE work_time_accounting_terms (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    effective_from DATE NOT NULL,
    accounting_mode VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_work_time_accounting_term_owner_effective
        UNIQUE (user_id, effective_from),

    CONSTRAINT ck_work_time_accounting_term_mode
        CHECK (accounting_mode IN ('DAILY', 'SUMMARIZED'))
);

CREATE INDEX idx_work_time_accounting_terms_owner_effective
    ON work_time_accounting_terms(user_id, effective_from DESC);
