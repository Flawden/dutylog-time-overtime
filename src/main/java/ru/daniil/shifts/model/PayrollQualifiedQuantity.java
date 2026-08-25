package ru.daniil.shifts.model;

import java.util.Objects;

/**
 * Unit-aware quantity participating in an earning formula.
 *
 * A quantity has no monetary meaning by itself. Pricing and calculation-base
 * semantics remain separate contracts.
 *
 * No implicit unit conversion is allowed. In particular:
 * MINUTES and CALENDAR_DAYS are not interchangeable even when both happen
 * to describe the same calendar interval.
 */
public record PayrollQualifiedQuantity(
        long value,
        PayrollQuantityUnit unit
) {

    public PayrollQualifiedQuantity {
        if (value < 0L) {
            throw new IllegalArgumentException(
                    "Payroll qualified quantity cannot be negative"
            );
        }

        Objects.requireNonNull(
                unit,
                "Payroll qualified quantity unit is required"
        );
    }

    public static PayrollQualifiedQuantity minutes(
            long value
    ) {
        return new PayrollQualifiedQuantity(
                value,
                PayrollQuantityUnit.MINUTES
        );
    }

    public static PayrollQualifiedQuantity calendarDays(
            long value
    ) {
        return new PayrollQualifiedQuantity(
                value,
                PayrollQuantityUnit.CALENDAR_DAYS
        );
    }

    /**
     * Exact aggregation is legal only inside the same unit.
     *
     * Cross-unit aggregation would destroy payroll meaning and therefore
     * fails closed instead of converting or silently summing raw numbers.
     */
    public PayrollQualifiedQuantity plus(
            PayrollQualifiedQuantity other
    ) {
        Objects.requireNonNull(
                other,
                "Payroll qualified quantity to add is required"
        );

        if (unit != other.unit) {
            throw new IllegalArgumentException(
                    "Cannot add payroll quantities with different units: "
                            + unit
                            + " and "
                            + other.unit
            );
        }

        return new PayrollQualifiedQuantity(
                Math.addExact(
                        value,
                        other.value
                ),
                unit
        );
    }
}
