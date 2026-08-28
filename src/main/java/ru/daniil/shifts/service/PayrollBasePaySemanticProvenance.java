package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import ru.daniil.shifts.model.PayrollEarningKind;
import ru.daniil.shifts.model.PayrollQualifiedQuantity;
import ru.daniil.shifts.service.TimeCompensationService.PayrollSourceDay;
import ru.daniil.shifts.service.TimeCompensationService.PayrollSourceSnapshot;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Formula-owned BASE_PAY source-line attribution.
 *
 * <p>This boundary does not pretend that Payroll stores employer-originated
 * daily money lines. Instead it can derive exact deterministic line money only
 * when the already-canonical monthly BASE_PAY formula is a linear ratio over
 * exact dated source quantities and no un-attributed adjustment/cap destroys
 * that identity.</p>
 *
 * <p>Allocation uses the same HALF_UP ratio kernel as monthly Payroll, applied
 * to chronological cumulative quantities. Consecutive differences telescope
 * back to the exact frozen monthly amount, so no cent is created or lost.</p>
 *
 * <p>Fail-closed cases deliberately return {@code null}; Payroll then keeps
 * the pre-existing aggregate BASE_PAY line with null quantity/period
 * provenance. In particular this happens for manual time adjustments and for
 * salary coverage whose monthly clamp cannot be assigned to specific dates
 * from the current source model.</p>
 */
@Service
public class PayrollBasePaySemanticProvenance {

    private final CompensationCalculationService calculation;

    public PayrollBasePaySemanticProvenance(
            CompensationCalculationService calculation
    ) {
        this.calculation =
                Objects.requireNonNull(
                        calculation,
                        "Compensation calculation service is required"
                );
    }

    /**
     * @return exact detailed BASE_PAY lines, or {@code null} when the current
     * source cannot prove a lossless dated allocation.
     */
    public List<PayrollSemanticFreezeProjection.SemanticLine> lines(
            PayrollSourceSnapshot source,
            String payMode,
            Long configuredHourlyRateMinor,
            Long monthlySalaryMinor,
            int productionNormMinutes,
            int salaryCoveredMinutes,
            long basePayMinor
    ) {
        Objects.requireNonNull(
                source,
                "Payroll source snapshot is required"
        );

        Objects.requireNonNull(
                payMode,
                "BASE_PAY mode is required"
        );

        if (basePayMinor < 0L) {
            throw new IllegalArgumentException(
                    "BASE_PAY amount cannot be negative"
            );
        }

        if (basePayMinor == 0L) {
            return null;
        }

        /*
         * The current PayrollSourceSnapshot preserves only the aggregate
         * manual time adjustment, not its dated contribution to BASE_PAY.
         * Never assign that money to a posting-month day by inference.
         */
        if (source.timeAdjustmentMinutes() != 0) {
            return null;
        }

        List<DateQuantity> quantities =
                switch (payMode) {
                    case "HOURLY" -> hourlyQuantities(source);
                    case "SALARY" -> salaryQuantities(
                            source,
                            salaryCoveredMinutes
                    );
                    default -> throw new IllegalArgumentException(
                            "Unsupported BASE_PAY mode: " + payMode
                    );
                };

        if (quantities == null || quantities.isEmpty()) {
            return null;
        }

        long pricingAmount;
        long denominator;

        if ("HOURLY".equals(payMode)) {
            if (configuredHourlyRateMinor == null
                    || configuredHourlyRateMinor <= 0L) {
                throw new IllegalStateException(
                        "HOURLY BASE_PAY provenance requires configured hourly rate"
                );
            }

            pricingAmount = configuredHourlyRateMinor;
            denominator = 60L;
        } else {
            if (monthlySalaryMinor == null
                    || monthlySalaryMinor <= 0L
                    || productionNormMinutes <= 0) {
                throw new IllegalStateException(
                        "SALARY BASE_PAY provenance requires salary and positive norm"
                );
            }

            pricingAmount = monthlySalaryMinor;
            denominator = productionNormMinutes;
        }

        long totalQuantity = quantities.stream()
                .mapToLong(DateQuantity::minutes)
                .sum();

        long expectedMonthly =
                calculation.ratioMoney(
                        pricingAmount,
                        totalQuantity,
                        denominator
                );

        if (expectedMonthly != basePayMinor) {
            throw new IllegalStateException(
                    "BASE_PAY provenance formula disagrees with Payroll aggregate"
            );
        }

        List<PayrollSemanticFreezeProjection.SemanticLine> result =
                new ArrayList<>();

        long cumulativeQuantity = 0L;
        long cumulativeMoney = 0L;

        for (DateQuantity quantity : quantities) {
            cumulativeQuantity =
                    Math.addExact(
                            cumulativeQuantity,
                            quantity.minutes()
                    );

            long pricedCumulative =
                    calculation.ratioMoney(
                            pricingAmount,
                            cumulativeQuantity,
                            denominator
                    );

            long lineAmount =
                    Math.subtractExact(
                            pricedCumulative,
                            cumulativeMoney
                    );

            /*
             * Frozen semantic earning lines require positive money. If a very
             * small configured rate creates a zero-money dated fragment, the
             * current schema cannot represent that quantity without merging
             * dates. Merging would harm p5 attribution, so keep aggregate-only.
             */
            if (lineAmount <= 0L) {
                return null;
            }

            result.add(
                    new PayrollSemanticFreezeProjection.SemanticLine(
                            PayrollEarningKind.BASE_PAY,
                            lineAmount,
                            PayrollQualifiedQuantity.minutes(
                                    quantity.minutes()
                            ),
                            quantity.date(),
                            quantity.date(),
                            null,
                            null
                    )
            );

            cumulativeMoney = pricedCumulative;
        }

        if (cumulativeMoney != basePayMinor) {
            throw new IllegalStateException(
                    "BASE_PAY source-line allocation lost Payroll money"
            );
        }

        return List.copyOf(result);
    }

    private List<DateQuantity> hourlyQuantities(
            PayrollSourceSnapshot source
    ) {
        List<DateQuantity> result = new ArrayList<>();
        long aggregate = 0L;

        LocalDate previous = null;

        for (PayrollSourceDay day : source.days()) {
            validateDay(source, day, previous);
            previous = day.date();

            long minutes = day.hourlyBaseWorkedMinutes();
            minutes = Math.addExact(minutes, day.vacationMinutes());
            minutes = Math.addExact(minutes, day.sickMinutes());
            minutes = Math.addExact(minutes, day.overtimeCompensatedMinutes());

            if (minutes < 0L) {
                throw new IllegalStateException(
                        "HOURLY BASE_PAY dated quantity cannot be negative"
                );
            }

            aggregate = Math.addExact(aggregate, minutes);

            if (minutes > 0L) {
                result.add(
                        new DateQuantity(
                                day.date(),
                                minutes
                        )
                );
            }
        }

        if (aggregate != source.hourlyBasePayableMinutes()) {
            throw new IllegalStateException(
                    "Dated HOURLY BASE_PAY quantity disagrees with Payroll source aggregate"
            );
        }

        return result;
    }

    private List<DateQuantity> salaryQuantities(
            PayrollSourceSnapshot source,
            int salaryCoveredMinutes
    ) {
        if (salaryCoveredMinutes < 0) {
            throw new IllegalArgumentException(
                    "Salary covered minutes cannot be negative"
            );
        }

        List<DateQuantity> result = new ArrayList<>();
        long aggregate = 0L;
        LocalDate previous = null;

        for (PayrollSourceDay day : source.days()) {
            validateDay(source, day, previous);
            previous = day.date();

            long coveredWithoutVacation = day.workedMinutes();
            coveredWithoutVacation =
                    Math.addExact(
                            coveredWithoutVacation,
                            day.sickMinutes()
                    );
            coveredWithoutVacation =
                    Math.addExact(
                            coveredWithoutVacation,
                            day.overtimeCompensatedMinutes()
                    );

            if (coveredWithoutVacation < 0L
                    || day.plannedMinutes() < 0) {
                throw new IllegalStateException(
                        "SALARY BASE_PAY dated quantity cannot be negative"
                );
            }

            long minutes =
                    Math.min(
                            day.plannedMinutes(),
                            coveredWithoutVacation
                    );

            aggregate = Math.addExact(aggregate, minutes);

            if (minutes > 0L) {
                result.add(
                        new DateQuantity(
                                day.date(),
                                minutes
                        )
                );
            }
        }

        /*
         * CompensationCalculationService clamps SALARY covered minutes to the
         * production norm after adding the aggregate adjustment. If the sum of
         * exact dated quantities differs from that final aggregate, the current
         * model cannot prove which dates own the removed/added minutes.
         */
        if (aggregate != salaryCoveredMinutes) {
            return null;
        }

        return result;
    }

    private void validateDay(
            PayrollSourceSnapshot source,
            PayrollSourceDay day,
            LocalDate previous
    ) {
        Objects.requireNonNull(
                day,
                "Payroll source day is required"
        );

        Objects.requireNonNull(
                day.date(),
                "Payroll source day date is required"
        );

        if (day.date().isBefore(source.from())
                || day.date().isAfter(source.to())) {
            throw new IllegalStateException(
                    "Payroll source day exceeds source boundary"
            );
        }

        if (previous != null
                && !day.date().isAfter(previous)) {
            throw new IllegalStateException(
                    "Payroll source days must be strictly chronological"
            );
        }

        if (day.plannedMinutes() < 0
                || day.workedMinutes() < 0
                || day.vacationMinutes() < 0
                || day.sickMinutes() < 0
                || day.overtimeCompensatedMinutes() < 0
                || day.unpaidMinutes() < 0
                || day.hourlyBaseWorkedMinutes() < 0) {
            throw new IllegalStateException(
                    "Payroll source day contains negative minutes"
            );
        }
    }

    private record DateQuantity(
            LocalDate date,
            long minutes
    ) {
        private DateQuantity {
            Objects.requireNonNull(
                    date,
                    "BASE_PAY quantity date is required"
            );

            if (minutes <= 0L) {
                throw new IllegalArgumentException(
                        "BASE_PAY dated quantity must be positive"
                );
            }
        }
    }
}
