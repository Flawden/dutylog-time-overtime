package ru.daniil.shifts.model;

/**
 * Explicit factual nature of a bonus for PP-540 paragraph-15 routing.
 *
 * <p>This is source/factual identity, not the legal inclusion decision itself.
 * Display names, posting month and award-period length never synthesize it.</p>
 */
public enum PayrollBonusP15Nature {
    MONTHLY,
    WORK_PERIOD,
    ANNUAL_RESULT,
    SERVICE_LENGTH
}
