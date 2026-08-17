package ru.daniil.shifts.service;

import org.junit.jupiter.api.Test;
import ru.daniil.shifts.model.CompensationTerm;
import ru.daniil.shifts.service.TimeCompensationService.PayrollSourceDay;
import ru.daniil.shifts.service.TimeCompensationService.PayrollSourceSnapshot;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CompensationCalculationServiceTest {
    private final CompensationCalculationService service = new CompensationCalculationService();

    @Test
    void hourlyModePreservesCanonicalPayableMinutePricing() {
        var result = service.calculate(term("HOURLY", 100_000L, null), source(480, List.of(day(480, 480, 0, 0, 0))), 480);
        assertEquals("HOURLY", result.payMode()); assertEquals(100_000L, result.effectiveHourlyRateMinor());
        assertEquals(800_000L, result.basePayMinor()); assertEquals(0, result.salaryCoveredMinutes());
    }

    @Test
    void salaryModePaysExactMonthlySalaryAtFullProductionNormAndDerivesHourlyExplanation() {
        var result = service.calculate(term("SALARY", null, 8_000_000L), source(10_020, List.of(day(10_020, 10_020, 0, 0, 0))), 10_020);
        assertEquals(8_000_000L, result.basePayMinor()); assertEquals(10_020, result.salaryCoveredMinutes());
        assertEquals(47_904L, result.effectiveHourlyRateMinor());
    }

    @Test
    void salaryBaseDoesNotGrowWhenActualWorkExceedsRequiredMinutes() {
        var result = service.calculate(term("SALARY", null, 8_000_000L), source(540, List.of(day(480, 540, 0, 0, 0))), 480);
        assertEquals(480, result.salaryCoveredMinutes()); assertEquals(8_000_000L, result.basePayMinor());
    }

    @Test
    void paidAbsenceCoversSalaryNormWhileUncoveredMinutesProrateBase() {
        var paid = service.calculate(term("SALARY", null, 8_000_000L), source(480, List.of(day(480, 240, 240, 0, 0))), 480);
        assertEquals(8_000_000L, paid.basePayMinor());
        var uncovered = service.calculate(term("SALARY", null, 8_000_000L), source(420, List.of(day(480, 420, 0, 0, 0))), 480);
        assertEquals(420, uncovered.salaryCoveredMinutes()); assertEquals(7_000_000L, uncovered.basePayMinor());
    }

    private CompensationTerm term(String mode, Long hourly, Long salary) {
        CompensationTerm term = new CompensationTerm(null, LocalDate.of(2026, 8, 1)); term.update(mode, "RUB", hourly, salary); return term;
    }
    private PayrollSourceDay day(int planned, int worked, int vacation, int sick, int overtimeCompensated) {
        return new PayrollSourceDay(LocalDate.of(2026, 8, 1), planned, worked, vacation, sick, overtimeCompensated, 0);
    }
    private PayrollSourceSnapshot source(int payable, List<PayrollSourceDay> days) {
        int planned = days.stream().mapToInt(PayrollSourceDay::plannedMinutes).sum();
        int worked = days.stream().mapToInt(PayrollSourceDay::workedMinutes).sum();
        int vacation = days.stream().mapToInt(PayrollSourceDay::vacationMinutes).sum();
        int sick = days.stream().mapToInt(PayrollSourceDay::sickMinutes).sum();
        int overtime = days.stream().mapToInt(PayrollSourceDay::overtimeCompensatedMinutes).sum();
        return new PayrollSourceSnapshot(LocalDate.of(2026,8,1), LocalDate.of(2026,8,31), planned, worked,
                vacation, sick, overtime, 0, 0, vacation+sick+overtime, payable, days);
    }
}
