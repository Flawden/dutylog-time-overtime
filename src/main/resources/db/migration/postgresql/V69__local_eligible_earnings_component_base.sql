-- DutyLog v27.48.0 — Local Eligible Earnings Component Base.
--
-- Adds one explicit calculation-base identity for semantic components whose
-- reference base is defined by PayrollEarningBaseEligibility.
--
-- B3C1 production wiring is intentionally limited to REGIONAL_COEFFICIENT.
-- No historical row is backfilled or reinterpreted. Existing
-- NOMINAL_SALARY / EARNED_BASE_PAY meanings are unchanged.

ALTER TABLE compensation_component_versions
    DROP CONSTRAINT ck_compensation_component_calculation_base;

ALTER TABLE compensation_component_versions
    ADD CONSTRAINT ck_compensation_component_calculation_base
        CHECK (
            calculation_base IS NULL
            OR calculation_base IN (
                'NOMINAL_SALARY',
                'EARNED_BASE_PAY',
                'LOCAL_ELIGIBLE_EARNINGS'
            )
        );

ALTER TABLE payroll_snapshot_compensation_component_lines
    DROP CONSTRAINT ck_payroll_snapshot_component_line_shape;

ALTER TABLE payroll_snapshot_compensation_component_lines
    ADD CONSTRAINT ck_payroll_snapshot_component_line_shape
        CHECK (
            (
                calculation_type = 'FIXED_AMOUNT'
                AND calculation_base IS NULL
                AND rate_bps IS NULL
                AND configured_amount_minor
                    BETWEEN 1 AND 1000000000000
                AND configured_currency_code
                    ~ '^[A-Z]{3}$'
                AND reference_base_minor = 0
                AND amount_minor = configured_amount_minor
            )
            OR
            (
                calculation_type = 'PERCENT_OF_BASE'
                AND calculation_base IN (
                    'NOMINAL_SALARY',
                    'EARNED_BASE_PAY',
                    'LOCAL_ELIGIBLE_EARNINGS'
                )
                AND rate_bps BETWEEN 1 AND 10000000
                AND configured_amount_minor IS NULL
                AND configured_currency_code IS NULL
            )
        );
