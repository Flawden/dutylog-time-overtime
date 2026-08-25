package ru.daniil.shifts.model;

/**
 * Unit of a payroll earning's qualified quantity.
 *
 * This type describes quantity only. It does not define:
 * - earning eligibility;
 * - calculation-base membership;
 * - pricing;
 * - source-period ownership;
 * - conversion between units.
 *
 * Units are intentionally evidence-driven. Add another unit only when a real
 * payroll formula requires it.
 */
public enum PayrollQuantityUnit {

    /**
     * Qualified duration expressed as exact integral minutes.
     *
     * Examples currently evidenced by DutyLog payroll sources include
     * classified work quantities such as NIGHT / HOLIDAY work.
     */
    MINUTES,

    /**
     * Qualified count of payable calendar days.
     *
     * This must not be interpreted as:
     * - scheduled workdays;
     * - scheduled work minutes;
     * - raw inclusive date-span length.
     *
     * Annual vacation evidence proves those concepts can differ.
     */
    CALENDAR_DAYS
}
