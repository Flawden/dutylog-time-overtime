package ru.daniil.shifts.model;

/**
 * Explicit legal/work-context mode for accounting working time.
 *
 * DAILY preserves working-day semantics for paragraph-15 proportionality.
 * SUMMARIZED preserves working-time quantity semantics and is later adapted
 * to WORKING_MINUTES by the reference worked-time FACT resolver.
 *
 * This enum is deliberately independent from Payroll pay mode (SALARY/HOURLY)
 * and from schedule shape. Neither is authority for the legal accounting mode.
 */
public enum WorkTimeAccountingMode {
    DAILY,
    SUMMARIZED
}
