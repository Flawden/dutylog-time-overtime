package ru.daniil.shifts.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class PayrollBonusP15NatureFactTest {

    private final AppUser owner = mock(AppUser.class);

    @Test
    void monthlyNatureRequiresMonthlyBonusAndSingleMonthAwardPeriod() {
        var fact = new PayrollBonusP15NatureFact(
                owner, 10, 20, 30,
                PayrollEarningKind.MONTHLY_BONUS,
                PayrollBonusP15Nature.MONTHLY,
                "KPI",
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 31),
                null
        );
        assertEquals(PayrollBonusP15Nature.MONTHLY, fact.getP15Nature());
    }

    @Test
    void monthlyNatureRejectsCrossMonthAwardPeriod() {
        assertThrows(IllegalArgumentException.class, () ->
                new PayrollBonusP15NatureFact(
                        owner, 10, 20, 30,
                        PayrollEarningKind.MONTHLY_BONUS,
                        PayrollBonusP15Nature.MONTHLY,
                        "KPI",
                        LocalDate.of(2026, 2, 15),
                        LocalDate.of(2026, 3, 14),
                        null
                )
        );
    }

    @Test
    void workPeriodRequiresOneTimeBonusLongerThanOneMonth() {
        var fact = new PayrollBonusP15NatureFact(
                owner, 10, 20, 30,
                PayrollEarningKind.ONE_TIME_BONUS,
                PayrollBonusP15Nature.WORK_PERIOD,
                "QUARTER",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 3, 31),
                false
        );
        assertEquals(PayrollBonusP15Nature.WORK_PERIOD, fact.getP15Nature());
    }

    @Test
    void workPeriodRejectsOneMonthOrLess() {
        assertThrows(IllegalArgumentException.class, () ->
                new PayrollBonusP15NatureFact(
                        owner, 10, 20, 30,
                        PayrollEarningKind.ONE_TIME_BONUS,
                        PayrollBonusP15Nature.WORK_PERIOD,
                        "SPECIAL",
                        LocalDate.of(2026, 1, 15),
                        LocalDate.of(2026, 2, 14),
                        false
                )
        );
    }

    @Test
    void annualNatureRequiresExplicitAnnualResult() {
        var fact = new PayrollBonusP15NatureFact(
                owner, 10, 20, 30,
                PayrollEarningKind.ONE_TIME_BONUS,
                PayrollBonusP15Nature.ANNUAL_RESULT,
                "YEAR_RESULT",
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 12, 31),
                true
        );
        assertEquals(PayrollBonusP15Nature.ANNUAL_RESULT, fact.getP15Nature());
    }

    @Test
    void serviceLengthIsDistinctFromAnnualResult() {
        var fact = new PayrollBonusP15NatureFact(
                owner, 10, 20, 30,
                PayrollEarningKind.ONE_TIME_BONUS,
                PayrollBonusP15Nature.SERVICE_LENGTH,
                "SERVICE_LENGTH",
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 12, 31),
                false
        );
        assertEquals(PayrollBonusP15Nature.SERVICE_LENGTH, fact.getP15Nature());
    }

    @Test
    void serviceLengthCannotMasqueradeAsMonthlyBonus() {
        assertThrows(IllegalArgumentException.class, () ->
                new PayrollBonusP15NatureFact(
                        owner, 10, 20, 30,
                        PayrollEarningKind.MONTHLY_BONUS,
                        PayrollBonusP15Nature.SERVICE_LENGTH,
                        "SERVICE_LENGTH",
                        LocalDate.of(2025, 1, 1),
                        LocalDate.of(2025, 12, 31),
                        false
                )
        );
    }
}
