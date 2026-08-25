package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import ru.daniil.shifts.model.CompensationTerm;
import ru.daniil.shifts.service.TimeCompensationService.PayrollSourceDay;
import ru.daniil.shifts.service.TimeCompensationService.PayrollSourceSnapshot;
import ru.daniil.shifts.service.exception.ApiException;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Pure money formula layer. It prices canonical PayrollSourceSnapshot data and never reads Calendar storage. */
@Service
public class CompensationCalculationService {

    public Result calculate(CompensationTerm term, PayrollSourceSnapshot source, int productionNormMinutes) {
        if (term == null) throw new IllegalArgumentException("term is required");
        if ("HOURLY".equals(term.getPayMode())) {
            long configured = effectiveHourlyRateMinor(
                    term,
                    productionNormMinutes
            );
            long basePay = moneyForMinutes(
                    source.hourlyBasePayableMinutes(),
                    configured
            );
            return new Result(
                    "HOURLY",
                    configured,
                    null,
                    configured,
                    Math.max(0, productionNormMinutes),
                    0,
                    basePay
            );
        }
        if (!"SALARY".equals(term.getPayMode())) {
            throw ApiException.badRequest("PAYROLL_MODE_INVALID", "Некорректный способ оплаты");
        }
        long salary = requiredPositive(term.getMonthlySalaryMinor(), "PAYROLL_SALARY_REQUIRED", "Укажи месячный оклад");
        int norm = Math.max(0, productionNormMinutes);
        if (norm <= 0) {
            throw ApiException.conflict("PAYROLL_PRODUCTION_NORM_REQUIRED",
                    "Для оклада нужна положительная расчётная норма месяца");
        }
        int covered = salaryCoveredMinutes(source, norm);
        long effectiveHourly =
                effectiveHourlyRateMinor(
                        term,
                        norm
                );
        long basePay =
                ratioMoney(
                        salary,
                        covered,
                        norm
                );
        return new Result("SALARY", null, salary, effectiveHourly, norm, covered, basePay);
    }

    /**
     * Canonical effective hourly value used by both monthly Payroll and
     * historical settlement pricing.
     *
     * HOURLY returns the configured rate.
     *
     * SALARY derives the hourly value from that compensation term and the
     * production norm of the valuation month:
     *
     * salary * 60 / productionNormMinutes, HALF_UP.
     *
     * This method prices no minutes by itself.
     */
    public long effectiveHourlyRateMinor(
            CompensationTerm term,
            int productionNormMinutes
    ) {
        if (term == null) {
            throw new IllegalArgumentException(
                    "term is required"
            );
        }

        if ("HOURLY".equals(
                term.getPayMode()
        )) {
            return requiredPositive(
                    term.getHourlyRateMinor(),
                    "PAYROLL_RATE_REQUIRED",
                    "Укажи почасовую ставку"
            );
        }

        if (!"SALARY".equals(
                term.getPayMode()
        )) {
            throw ApiException.badRequest(
                    "PAYROLL_MODE_INVALID",
                    "Некорректный способ оплаты"
            );
        }

        long salary =
                requiredPositive(
                        term.getMonthlySalaryMinor(),
                        "PAYROLL_SALARY_REQUIRED",
                        "Укажи месячный оклад"
                );

        int norm =
                Math.max(
                        0,
                        productionNormMinutes
                );

        if (norm <= 0) {
            throw ApiException.conflict(
                    "PAYROLL_PRODUCTION_NORM_REQUIRED",
                    "Для оклада нужна положительная расчётная норма месяца"
            );
        }

        return ratioMoney(
                salary,
                60L,
                norm
        );
    }

    int salaryCoveredMinutes(
            PayrollSourceSnapshot source,
            int productionNormMinutes
    ) {
        long covered = 0;

        for (PayrollSourceDay day : source.days()) {
            /*
             * Real same-role payroll evidence proves that annual vacation does
             * not preserve monthly salary coverage. Salary is prorated to the
             * covered worked quantity and vacation is paid separately through
             * average earnings.
             *
             * OVERTIME_BANK compensated time off remains salary-covered.
             *
             * SICK_PAY deliberately retains its existing behavior until
             * separate real-payroll evidence defines its replacement-payment
             * semantics. Do not generalize the vacation finding to sickness.
             */
            int salaryCoveredAbsence =
                    Math.addExact(
                            Math.max(
                                    0,
                                    day.sickMinutes()
                            ),
                            Math.max(
                                    0,
                                    day.overtimeCompensatedMinutes()
                            )
                    );

            int dayCovered =
                    Math.addExact(
                            Math.max(
                                    0,
                                    day.workedMinutes()
                            ),
                            salaryCoveredAbsence
                    );

            covered =
                    Math.addExact(
                            covered,
                            Math.min(
                                    Math.max(
                                            0,
                                            day.plannedMinutes()
                                    ),
                                    dayCovered
                            )
                    );
        }

        covered =
                Math.addExact(
                        covered,
                        source.timeAdjustmentMinutes()
                );

        return (int) Math.max(
                0L,
                Math.min(
                        (long) Math.max(
                                0,
                                productionNormMinutes
                        ),
                        covered
                )
        );
    }

    private long requiredPositive(Long value, String code, String message) {
        if (value == null || value <= 0) throw ApiException.conflict(code, message);
        return value;
    }

    private long moneyForMinutes(int minutes, long hourlyRateMinor) {
        return ratioMoney(hourlyRateMinor, Math.max(0, minutes), 60L);
    }

    private long ratioMoney(long amount, long numerator, long denominator) {
        if (denominator <= 0) throw ApiException.badRequest("PAYROLL_DIVISOR_INVALID", "Некорректный делитель расчёта");
        try {
            return BigDecimal.valueOf(amount)
                    .multiply(BigDecimal.valueOf(Math.max(0, numerator)))
                    .divide(BigDecimal.valueOf(denominator), 0, RoundingMode.HALF_UP)
                    .longValueExact();
        } catch (ArithmeticException ex) {
            throw ApiException.badRequest("PAYROLL_AMOUNT_OVERFLOW", "Сумма расчёта слишком велика");
        }
    }

    public record Result(String payMode,
                         Long configuredHourlyRateMinor,
                         Long monthlySalaryMinor,
                         long effectiveHourlyRateMinor,
                         int productionNormMinutes,
                         int salaryCoveredMinutes,
                         long basePayMinor) {}
}
