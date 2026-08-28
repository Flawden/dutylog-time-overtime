package ru.daniil.shifts.service;

import org.junit.jupiter.api.Test;
import ru.daniil.shifts.model.PayrollEarningKind;
import ru.daniil.shifts.model.PayrollQuantityUnit;
import ru.daniil.shifts.service.TimeCompensationService.PayrollSourceDay;
import ru.daniil.shifts.service.TimeCompensationService.PayrollSourceSnapshot;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PayrollBasePaySemanticProvenanceTest {

    private final PayrollBasePaySemanticProvenance provenance =
            new PayrollBasePaySemanticProvenance(
                    new CompensationCalculationService()
            );

    @Test
    void hourlyAllocationUsesSameCumulativeHalfUpKernelAndTelescopesExactly() {
        LocalDate first = LocalDate.of(2026, 8, 1);
        LocalDate second = first.plusDays(1);

        PayrollSourceSnapshot source =
                source(
                        60,
                        60,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        60,
                        60,
                        List.of(
                                day(first, 31, 31, 0, 0, 0, 0, 31),
                                day(second, 29, 29, 0, 0, 0, 0, 29)
                        )
                );

        List<PayrollSemanticFreezeProjection.SemanticLine> lines =
                provenance.lines(
                        source,
                        "HOURLY",
                        10_000L,
                        null,
                        0,
                        0,
                        10_000L
                );

        assertNotNull(lines);
        assertEquals(2, lines.size());
        assertEquals(5_167L, lines.get(0).amountMinor());
        assertEquals(4_833L, lines.get(1).amountMinor());
        assertEquals(10_000L, lines.stream().mapToLong(PayrollSemanticFreezeProjection.SemanticLine::amountMinor).sum());

        assertEquals(PayrollEarningKind.BASE_PAY, lines.get(0).earningKind());
        assertEquals(PayrollQuantityUnit.MINUTES, lines.get(0).qualifiedQuantity().unit());
        assertEquals(31L, lines.get(0).qualifiedQuantity().value());
        assertEquals(first, lines.get(0).earningPeriodFrom());
        assertEquals(first, lines.get(0).earningPeriodTo());
        assertNull(lines.get(0).coverageFrom());
        assertNull(lines.get(0).coverageTo());
    }

    @Test
    void hourlyAllocationUsesClassifierDerivedOrdinaryMinutesNotAllWorkedMinutes() {
        LocalDate date = LocalDate.of(2026, 8, 3);

        PayrollSourceSnapshot source =
                source(
                        120,
                        120,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        120,
                        60,
                        List.of(
                                day(date, 120, 120, 0, 0, 0, 0, 60)
                        )
                );

        List<PayrollSemanticFreezeProjection.SemanticLine> lines =
                provenance.lines(
                        source,
                        "HOURLY",
                        10_000L,
                        null,
                        0,
                        0,
                        10_000L
                );

        assertNotNull(lines);
        assertEquals(1, lines.size());
        assertEquals(60L, lines.get(0).qualifiedQuantity().value());
        assertEquals(10_000L, lines.get(0).amountMinor());
    }

    @Test
    void salaryAllocationOmitsVacationAndPreservesSickCoveredDateExactly() {
        LocalDate worked = LocalDate.of(2026, 8, 4);
        LocalDate sick = worked.plusDays(1);
        LocalDate vacation = worked.plusDays(2);

        PayrollSourceSnapshot source =
                source(
                        1_440,
                        480,
                        480,
                        480,
                        0,
                        0,
                        0,
                        960,
                        1_440,
                        0,
                        List.of(
                                day(worked, 480, 480, 0, 0, 0, 0, 480),
                                day(sick, 480, 0, 0, 480, 0, 0, 0),
                                day(vacation, 480, 0, 480, 0, 0, 0, 0)
                        )
                );

        List<PayrollSemanticFreezeProjection.SemanticLine> lines =
                provenance.lines(
                        source,
                        "SALARY",
                        null,
                        10_000_000L,
                        960,
                        960,
                        10_000_000L
                );

        assertNotNull(lines);
        assertEquals(2, lines.size());
        assertEquals(worked, lines.get(0).earningPeriodFrom());
        assertEquals(sick, lines.get(1).earningPeriodFrom());
        assertEquals(480L, lines.get(0).qualifiedQuantity().value());
        assertEquals(480L, lines.get(1).qualifiedQuantity().value());
        assertEquals(5_000_000L, lines.get(0).amountMinor());
        assertEquals(5_000_000L, lines.get(1).amountMinor());
        assertTrue(lines.stream().noneMatch(line -> vacation.equals(line.earningPeriodFrom())));
    }

    @Test
    void aggregateManualTimeAdjustmentKeepsBasePayAggregateOnly() {
        LocalDate date = LocalDate.of(2026, 8, 7);

        PayrollSourceSnapshot source =
                source(
                        60,
                        60,
                        0,
                        0,
                        0,
                        0,
                        60,
                        0,
                        120,
                        120,
                        List.of(
                                day(date, 60, 60, 0, 0, 0, 0, 60)
                        )
                );

        assertNull(
                provenance.lines(
                        source,
                        "HOURLY",
                        10_000L,
                        null,
                        0,
                        0,
                        20_000L
                )
        );
    }

    @Test
    void salaryCoverageClampWithoutDateAuthorityKeepsBasePayAggregateOnly() {
        LocalDate first = LocalDate.of(2026, 8, 8);
        LocalDate second = first.plusDays(1);

        PayrollSourceSnapshot source =
                source(
                        960,
                        960,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        960,
                        0,
                        List.of(
                                day(first, 480, 480, 0, 0, 0, 0, 480),
                                day(second, 480, 480, 0, 0, 0, 0, 480)
                        )
                );

        assertNull(
                provenance.lines(
                        source,
                        "SALARY",
                        null,
                        10_000_000L,
                        480,
                        480,
                        10_000_000L
                )
        );
    }

    private static PayrollSourceDay day(
            LocalDate date,
            int planned,
            int worked,
            int vacation,
            int sick,
            int overtimeCompensated,
            int unpaid,
            int hourlyBaseWorked
    ) {
        return new PayrollSourceDay(
                date,
                planned,
                worked,
                vacation,
                sick,
                overtimeCompensated,
                unpaid,
                hourlyBaseWorked
        );
    }

    private static PayrollSourceSnapshot source(
            int planned,
            int worked,
            int vacation,
            int sick,
            int overtimeCompensated,
            int unpaid,
            int adjustment,
            int paidAbsence,
            int payable,
            int hourlyBasePayable,
            List<PayrollSourceDay> days
    ) {
        LocalDate from = LocalDate.of(2026, 8, 1);
        LocalDate to = LocalDate.of(2026, 8, 31);

        return new PayrollSourceSnapshot(
                from,
                to,
                planned,
                worked,
                vacation,
                sick,
                overtimeCompensated,
                unpaid,
                adjustment,
                paidAbsence,
                payable,
                hourlyBasePayable,
                days
        );
    }
}
