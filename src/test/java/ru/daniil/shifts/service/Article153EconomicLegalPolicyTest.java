package ru.daniil.shifts.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class Article153EconomicLegalPolicyTest {

    private static final LocalDate DATE =
            LocalDate.of(2026, 5, 9);

    @Test
    void hourlyEnhancedPayAddsOneTariffOnTopOfOrdinaryBase() {
        var decision =
                Article153EconomicLegalPolicy.resolve(
                        DATE,
                        Article153EconomicLegalPolicy.PayMode.HOURLY,
                        Article153EconomicLegalPolicy.NormPosition.NOT_APPLICABLE,
                        Article153EconomicLegalPolicy.CompensationChoice.ENHANCED_PAY
                );

        assertEquals(10_000, decision.additionalTariffBps());
        assertFalse(decision.employeeRestDayElectionAuthorityRequired());
    }

    @Test
    void salaryWithinNormEnhancedPayAddsOneTariffAboveSalary() {
        var decision =
                Article153EconomicLegalPolicy.resolve(
                        DATE,
                        Article153EconomicLegalPolicy.PayMode.SALARY,
                        Article153EconomicLegalPolicy.NormPosition.WITHIN_MONTHLY_NORM,
                        Article153EconomicLegalPolicy.CompensationChoice.ENHANCED_PAY
                );

        assertEquals(10_000, decision.additionalTariffBps());
    }

    @Test
    void salaryAboveNormEnhancedPayAddsTwoTariffsAboveSalary() {
        var decision =
                Article153EconomicLegalPolicy.resolve(
                        DATE,
                        Article153EconomicLegalPolicy.PayMode.SALARY,
                        Article153EconomicLegalPolicy.NormPosition.ABOVE_MONTHLY_NORM,
                        Article153EconomicLegalPolicy.CompensationChoice.ENHANCED_PAY
                );

        assertEquals(20_000, decision.additionalTariffBps());
    }

    @Test
    void hourlyOtherRestDayAddsNoSecondTariff() {
        var decision =
                Article153EconomicLegalPolicy.resolve(
                        DATE,
                        Article153EconomicLegalPolicy.PayMode.HOURLY,
                        Article153EconomicLegalPolicy.NormPosition.NOT_APPLICABLE,
                        Article153EconomicLegalPolicy.CompensationChoice.OTHER_REST_DAY
                );

        assertEquals(0, decision.additionalTariffBps());
        assertTrue(decision.employeeRestDayElectionAuthorityRequired());
    }

    @Test
    void salaryWithinNormOtherRestDayAddsNothingAboveSalary() {
        var decision =
                Article153EconomicLegalPolicy.resolve(
                        DATE,
                        Article153EconomicLegalPolicy.PayMode.SALARY,
                        Article153EconomicLegalPolicy.NormPosition.WITHIN_MONTHLY_NORM,
                        Article153EconomicLegalPolicy.CompensationChoice.OTHER_REST_DAY
                );

        assertEquals(0, decision.additionalTariffBps());
        assertTrue(decision.employeeRestDayElectionAuthorityRequired());
    }

    @Test
    void salaryAboveNormOtherRestDayAddsOneTariffAboveSalary() {
        var decision =
                Article153EconomicLegalPolicy.resolve(
                        DATE,
                        Article153EconomicLegalPolicy.PayMode.SALARY,
                        Article153EconomicLegalPolicy.NormPosition.ABOVE_MONTHLY_NORM,
                        Article153EconomicLegalPolicy.CompensationChoice.OTHER_REST_DAY
                );

        assertEquals(10_000, decision.additionalTariffBps());
        assertTrue(decision.employeeRestDayElectionAuthorityRequired());
    }

    @Test
    void hourlyRejectsInventedSalaryNormPosition() {
        assertThrows(
                IllegalArgumentException.class,
                () -> Article153EconomicLegalPolicy.resolve(
                        DATE,
                        Article153EconomicLegalPolicy.PayMode.HOURLY,
                        Article153EconomicLegalPolicy.NormPosition.WITHIN_MONTHLY_NORM,
                        Article153EconomicLegalPolicy.CompensationChoice.ENHANCED_PAY
                )
        );
    }

    @Test
    void salaryRejectsMissingNormPosition() {
        assertThrows(
                IllegalArgumentException.class,
                () -> Article153EconomicLegalPolicy.resolve(
                        DATE,
                        Article153EconomicLegalPolicy.PayMode.SALARY,
                        Article153EconomicLegalPolicy.NormPosition.NOT_APPLICABLE,
                        Article153EconomicLegalPolicy.CompensationChoice.ENHANCED_PAY
                )
        );
    }

    @Test
    void dateBeforeSourceLockedWindowFailsClosed() {
        assertThrows(
                UnsupportedOperationException.class,
                () -> Article153EconomicLegalPolicy.resolve(
                        LocalDate.of(2025, 12, 31),
                        Article153EconomicLegalPolicy.PayMode.HOURLY,
                        Article153EconomicLegalPolicy.NormPosition.NOT_APPLICABLE,
                        Article153EconomicLegalPolicy.CompensationChoice.ENHANCED_PAY
                )
        );
    }

    @Test
    void dateAfterSourceLockedWindowFailsClosed() {
        assertThrows(
                UnsupportedOperationException.class,
                () -> Article153EconomicLegalPolicy.resolve(
                        LocalDate.of(2027, 1, 1),
                        Article153EconomicLegalPolicy.PayMode.HOURLY,
                        Article153EconomicLegalPolicy.NormPosition.NOT_APPLICABLE,
                        Article153EconomicLegalPolicy.CompensationChoice.ENHANCED_PAY
                )
        );
    }

    @Test
    void decisionFreezesLegalSourceIdentity() {
        var decision =
                Article153EconomicLegalPolicy.resolve(
                        DATE,
                        Article153EconomicLegalPolicy.PayMode.HOURLY,
                        Article153EconomicLegalPolicy.NormPosition.NOT_APPLICABLE,
                        Article153EconomicLegalPolicy.CompensationChoice.ENHANCED_PAY
                );

        assertEquals(
                "RU_TK_RF_ARTICLE_153_CALENDAR_2026_V1",
                decision.legalRegime()
        );
        assertEquals("TK_RF_ARTICLE_153", decision.legalBasis());
        assertEquals("TK_RF_197_FZ_RED_2026_05_25", decision.sourceRevision());
        assertEquals(
                "FEDERAL_LAW_339_FZ_2024_09_30_EFFECTIVE_2025_03_01",
                decision.restDayAmendingAct()
        );
        assertEquals(
                "KS_RF_26_P_2018_06_28",
                decision.constitutionalAuthority()
        );
    }

    @Test
    void decisionIsExplicitlyStatutoryFloorOnly() {
        var decision =
                Article153EconomicLegalPolicy.resolve(
                        DATE,
                        Article153EconomicLegalPolicy.PayMode.SALARY,
                        Article153EconomicLegalPolicy.NormPosition.ABOVE_MONTHLY_NORM,
                        Article153EconomicLegalPolicy.CompensationChoice.ENHANCED_PAY
                );

        assertTrue(decision.statutoryFloorOnly());
        assertTrue(decision.localHigherRateAuthorityRequiredForFinalPricing());
    }
}
