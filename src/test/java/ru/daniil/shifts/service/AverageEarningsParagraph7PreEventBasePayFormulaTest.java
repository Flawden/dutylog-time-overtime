package ru.daniil.shifts.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AverageEarningsParagraph7PreEventBasePayFormulaTest {
    private static final LocalDate EVENT = LocalDate.of(2026, 8, 20);
    private static final LocalDate FROM = LocalDate.of(2026, 8, 1);

    @Test
    void nullAuthorityRejected() {
        assertThrows(
                NullPointerException.class,
                () -> AverageEarningsParagraph7PreEventBasePayFormula.calculate(null)
        );
    }

    @Test
    void blockedAuthorityRejectedBeforePricing() {
        var blocked = AverageEarningsParagraph7PreEventBasePayAuthorityService.Resolution.blocked(
                EVENT,
                FROM,
                work(480),
                "BLOCKED",
                "blocked for test"
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> AverageEarningsParagraph7PreEventBasePayFormula.calculate(blocked)
        );
    }

    @Test
    void noWorkedTimeProducesZeroWithoutPricingIdentity() {
        var authority = AverageEarningsParagraph7PreEventBasePayAuthorityService.Resolution.readyWithoutWorkedTime(
                EVENT,
                FROM,
                work(0)
        );

        var calculation = AverageEarningsParagraph7PreEventBasePayFormula.calculate(authority);

        assertSame(authority, calculation.authority());
        assertEquals(0L, calculation.basePayAmountMinor());
        assertNull(calculation.currencyCode());
    }

    @Test
    void hourlyFullHourPricesConfiguredRateExactly() {
        var calculation = AverageEarningsParagraph7PreEventBasePayFormula.calculate(
                hourly(10_000L, 60L, 60)
        );

        assertEquals(10_000L, calculation.basePayAmountMinor());
        assertEquals("RUB", calculation.currencyCode());
    }

    @Test
    void hourlyHalfMinorRoundsUp() {
        var calculation = AverageEarningsParagraph7PreEventBasePayFormula.calculate(
                hourly(1L, 30L, 30)
        );

        assertEquals(1L, calculation.basePayAmountMinor());
    }

    @Test
    void hourlyBelowHalfRoundsDown() {
        var calculation = AverageEarningsParagraph7PreEventBasePayFormula.calculate(
                hourly(1L, 29L, 29)
        );

        assertEquals(0L, calculation.basePayAmountMinor());
    }

    @Test
    void hourlyLargeProductDoesNotOverflowBeforeDivision() {
        var calculation = AverageEarningsParagraph7PreEventBasePayFormula.calculate(
                hourly(Long.MAX_VALUE, 60L, 60)
        );

        assertEquals(Long.MAX_VALUE, calculation.basePayAmountMinor());
    }

    @Test
    void hourlyFinalAmountOverflowRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> AverageEarningsParagraph7PreEventBasePayFormula.calculate(
                        hourly(Long.MAX_VALUE, 120L, 120)
                )
        );
    }

    @Test
    void salaryHalfMonthPricesExactHalfSalary() {
        var calculation = AverageEarningsParagraph7PreEventBasePayFormula.calculate(
                salary(300_000L, 9_600, 4_800L, 4_800)
        );

        assertEquals(150_000L, calculation.basePayAmountMinor());
        assertEquals("RUB", calculation.currencyCode());
    }

    @Test
    void salaryFullNormPricesFullSalary() {
        var calculation = AverageEarningsParagraph7PreEventBasePayFormula.calculate(
                salary(300_000L, 9_600, 9_600L, 9_600)
        );

        assertEquals(300_000L, calculation.basePayAmountMinor());
    }

    @Test
    void salaryHalfMinorRoundsUp() {
        var calculation = AverageEarningsParagraph7PreEventBasePayFormula.calculate(
                salary(1L, 2, 1L, 1)
        );

        assertEquals(1L, calculation.basePayAmountMinor());
    }

    @Test
    void salaryBelowHalfRoundsDown() {
        var calculation = AverageEarningsParagraph7PreEventBasePayFormula.calculate(
                salary(1L, 3, 1L, 1)
        );

        assertEquals(0L, calculation.basePayAmountMinor());
    }

    @Test
    void workedTimeWithZeroEligibleBaseQuantityRemainsZeroWithoutFallbackMeaning() {
        var calculation = AverageEarningsParagraph7PreEventBasePayFormula.calculate(
                hourly(10_000L, 0L, 480)
        );

        assertTrue(calculation.authority().workedTimePresent());
        assertFalse(calculation.authority().basePayQuantityPresent());
        assertEquals(0L, calculation.basePayAmountMinor());
    }

    @Test
    void calculationRetainsExactAuthorityObjectAsProvenance() {
        var authority = salary(250_000L, 10_000, 5_000L, 5_000);

        var calculation = AverageEarningsParagraph7PreEventBasePayFormula.calculate(authority);

        assertSame(authority, calculation.authority());
        assertEquals("RUB", calculation.currencyCode());
    }

    @Test
    void calculationRecordRejectsNegativeMoney() {
        var authority = hourly(10_000L, 60L, 60);

        assertThrows(
                IllegalArgumentException.class,
                () -> new AverageEarningsParagraph7PreEventBasePayFormula.Calculation(
                        authority,
                        -1L
                )
        );
    }

    private static AverageEarningsParagraph7PreEventBasePayAuthorityService.Resolution hourly(
            long rateMinor,
            long eligibleMinutes,
            int workedMinutes
    ) {
        return AverageEarningsParagraph7PreEventBasePayAuthorityService.Resolution.ready(
                EVENT,
                FROM,
                work(workedMinutes, Math.toIntExact(eligibleMinutes)),
                FROM,
                "HOURLY",
                "RUB",
                rateMinor,
                null,
                null,
                eligibleMinutes
        );
    }

    private static AverageEarningsParagraph7PreEventBasePayAuthorityService.Resolution salary(
            long monthlySalaryMinor,
            int normMinutes,
            long eligibleMinutes,
            int workedMinutes
    ) {
        return AverageEarningsParagraph7PreEventBasePayAuthorityService.Resolution.ready(
                EVENT,
                FROM,
                work(workedMinutes, workedMinutes),
                FROM,
                "SALARY",
                "RUB",
                null,
                monthlySalaryMinor,
                normMinutes,
                eligibleMinutes
        );
    }

    private static AverageEarningsParagraph7PreEventWorkFactService.Resolution work(
            int workedMinutes
    ) {
        return work(workedMinutes, workedMinutes);
    }

    private static AverageEarningsParagraph7PreEventWorkFactService.Resolution work(
            int workedMinutes,
            int hourlyBaseWorkedMinutes
    ) {
        if (workedMinutes == 0) {
            return AverageEarningsParagraph7PreEventWorkFactService.Resolution.ready(
                    EVENT,
                    FROM,
                    EVENT,
                    0,
                    0L,
                    List.of()
            );
        }
        return AverageEarningsParagraph7PreEventWorkFactService.Resolution.ready(
                EVENT,
                FROM,
                EVENT,
                1,
                workedMinutes,
                List.of(
                        new AverageEarningsParagraph7PreEventWorkFactService.WorkedDayFact(
                                LocalDate.of(2026, 8, 5),
                                workedMinutes,
                                workedMinutes,
                                hourlyBaseWorkedMinutes
                        )
                )
        );
    }
}
