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
            long configured = requiredPositive(term.getHourlyRateMinor(), "PAYROLL_RATE_REQUIRED", "Укажи почасовую ставку");
            long basePay = moneyForMinutes(source.payableMinutes(), configured);
            return new Result("HOURLY", configured, null, configured,
                    Math.max(0, productionNormMinutes), 0, basePay);
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
        long effectiveHourly = ratioMoney(salary, 60L, norm);
        long basePay = ratioMoney(salary, covered, norm);
        return new Result("SALARY", null, salary, effectiveHourly, norm, covered, basePay);
    }

    int salaryCoveredMinutes(PayrollSourceSnapshot source, int productionNormMinutes) {
        long covered = 0;
        for (PayrollSourceDay day : source.days()) {
            int paidAbsence = day.vacationMinutes() + day.sickMinutes() + day.overtimeCompensatedMinutes();
            int dayCovered = Math.max(0, day.workedMinutes()) + Math.max(0, paidAbsence);
            covered += Math.min(Math.max(0, day.plannedMinutes()), dayCovered);
        }
        covered += source.timeAdjustmentMinutes();
        return (int) Math.max(0L, Math.min((long) Math.max(0, productionNormMinutes), covered));
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
