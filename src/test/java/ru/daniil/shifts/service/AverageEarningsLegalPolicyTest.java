package ru.daniil.shifts.service;

import org.junit.jupiter.api.Test;
import ru.daniil.shifts.model.PayrollEarningKind;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static ru.daniil.shifts.service.AverageEarningsLegalPolicy.*;

class AverageEarningsLegalPolicyTest {

    private static final LocalDate EVENT =
            LocalDate.of(
                    2026,
                    8,
                    14
            );

    @Test
    void currentRegimeIsExplicitlyBoundedAndUnsupportedDatesFailClosed() {
        assertEquals(
                LegalRegime.RU_PP_540_2025,
                AverageEarningsLegalPolicy
                        .requireRegime(
                                LocalDate.of(
                                        2025,
                                        9,
                                        1
                                )
                        )
        );

        assertEquals(
                LegalRegime.RU_PP_540_2025,
                AverageEarningsLegalPolicy
                        .requireRegime(
                                LocalDate.of(
                                        2031,
                                        8,
                                        31
                                )
                        )
        );

        assertThrows(
                UnsupportedOperationException.class,
                () ->
                        AverageEarningsLegalPolicy
                                .requireRegime(
                                        LocalDate.of(
                                                2025,
                                                8,
                                                31
                                        )
                                )
        );

        assertThrows(
                UnsupportedOperationException.class,
                () ->
                        AverageEarningsLegalPolicy
                                .requireRegime(
                                        LocalDate.of(
                                                2031,
                                                9,
                                                1
                                        )
                                )
        );
    }

    @Test
    void paidVacationIsPreservedAverageExclusion() {
        AbsenceDecision decision =
                AverageEarningsLegalPolicy
                        .classifyAbsence(
                                EVENT,
                                absence(
                                        "VACATION",
                                        "VACATION_DAYS",
                                        "VACATION_ALLOWANCE"
                                )
                        );

        assertTrue(
                decision.resolved()
        );

        assertEquals(
                AbsenceTreatment.EXCLUDE_PRESERVED_AVERAGE,
                decision.treatment()
        );

        assertEquals(
                LegalBasis.PP_540_P5_A,
                decision.basis()
        );
    }

    @Test
    void sicknessIsTemporaryDisabilityExclusion() {
        AbsenceDecision decision =
                AverageEarningsLegalPolicy
                        .classifyAbsence(
                                EVENT,
                                absence(
                                        "SICK",
                                        "NONE",
                                        "SICK_PAY"
                                )
                        );

        assertTrue(
                decision.resolved()
        );

        assertEquals(
                AbsenceTreatment.EXCLUDE_TEMPORARY_DISABILITY,
                decision.treatment()
        );

        assertEquals(
                LegalBasis.PP_540_P5_B,
                decision.basis()
        );
    }

    @Test
    void unpaidAndOvertimeTimeOffAreOtherReleaseExclusions() {
        for (var fact : List.of(
                absence(
                        "UNPAID",
                        "NONE",
                        "UNPAID"
                ),
                absence(
                        "TIME_OFF",
                        "TIME_OFF_HOURS",
                        "OVERTIME_BANK"
                )
        )) {
            AbsenceDecision decision =
                    AverageEarningsLegalPolicy
                            .classifyAbsence(
                                    EVENT,
                                    fact
                            );

            assertTrue(
                    decision.resolved()
            );

            assertEquals(
                    AbsenceTreatment.EXCLUDE_OTHER_RELEASE_FROM_WORK,
                    decision.treatment()
            );

            assertEquals(
                    LegalBasis.PP_540_P5_E,
                    decision.basis()
            );
        }
    }

    @Test
    void customAbsenceRemainsUnresolvedAndTrustedTupleMismatchFailsClosed() {
        AbsenceDecision custom =
                AverageEarningsLegalPolicy
                        .classifyAbsence(
                                EVENT,
                                absence(
                                        null,
                                        "NONE",
                                        "NONE"
                                )
                        );

        assertFalse(
                custom.resolved()
        );

        assertEquals(
                AbsenceTreatment.UNRESOLVED,
                custom.treatment()
        );

        assertEquals(
                LegalBasis.UNRESOLVED,
                custom.basis()
        );

        assertThrows(
                IllegalStateException.class,
                () ->
                        AverageEarningsLegalPolicy
                                .classifyAbsence(
                                        EVENT,
                                        absence(
                                                "VACATION",
                                                "NONE",
                                                "VACATION_ALLOWANCE"
                                        )
                                )
        );
    }

    @Test
    void ordinaryPayrollKindsRemainRemunerationCandidates() {
        for (PayrollEarningKind kind : List.of(
                PayrollEarningKind.BASE_PAY,
                PayrollEarningKind.HOLIDAY_PAY,
                PayrollEarningKind.NIGHT_PREMIUM,
                PayrollEarningKind.HARMFUL_CONDITIONS,
                PayrollEarningKind.COMBINATION,
                PayrollEarningKind.REGIONAL_COEFFICIENT
        )) {
            EarningDecision decision =
                    AverageEarningsLegalPolicy
                            .classifyEarning(
                                    EVENT,
                                    earning(
                                            kind
                                    )
                            );

            assertTrue(
                    decision.resolved()
            );

            assertEquals(
                    EarningTreatment.ORDINARY_REMUNERATION,
                    decision.treatment(),
                    kind.name()
            );

            assertEquals(
                    LegalBasis.PP_540_P2,
                    decision.basis(),
                    kind.name()
            );
        }
    }

    @Test
    void bonusesRequireSpecialRulesAndVacationPayIsExcluded() {
        for (PayrollEarningKind kind : List.of(
                PayrollEarningKind.MONTHLY_BONUS,
                PayrollEarningKind.ONE_TIME_BONUS
        )) {
            EarningDecision decision =
                    AverageEarningsLegalPolicy
                            .classifyEarning(
                                    EVENT,
                                    earning(
                                            kind
                                    )
                            );

            assertEquals(
                    EarningTreatment.PREMIUM_SPECIAL_RULE,
                    decision.treatment()
            );

            assertEquals(
                    LegalBasis.PP_540_P2_AND_P15,
                    decision.basis()
            );
        }

        EarningDecision vacation =
                AverageEarningsLegalPolicy
                        .classifyEarning(
                                EVENT,
                                earning(
                                        PayrollEarningKind.VACATION_PAY
                                )
                        );

        assertEquals(
                EarningTreatment.EXCLUDE_PRESERVED_AVERAGE,
                vacation.treatment()
        );

        assertEquals(
                LegalBasis.PP_540_P5_A,
                vacation.basis()
        );
    }

    @Test
    void medicalCompensationRemainsExplicitlyUnresolved() {
        EarningDecision decision =
                AverageEarningsLegalPolicy
                        .classifyEarning(
                                EVENT,
                                earning(
                                        PayrollEarningKind.MEDICAL_COMPENSATION
                                )
                        );

        assertFalse(
                decision.resolved()
        );

        assertEquals(
                EarningTreatment.UNRESOLVED,
                decision.treatment()
        );

        assertEquals(
                LegalBasis.UNRESOLVED,
                decision.basis()
        );
    }

    private static AverageEarningsReferenceFactsService.AbsenceFact absence(
            String systemCode,
            String balancePolicy,
            String compensationPolicy
    ) {
        LocalDate date =
                LocalDate.of(
                        2026,
                        1,
                        15
                );

        return new AverageEarningsReferenceFactsService.AbsenceFact(
                1L,
                systemCode,
                balancePolicy,
                compensationPolicy,
                "APPROVED",
                "FULL_DAY",
                date,
                date,
                date,
                date,
                (LocalTime) null,
                (LocalTime) null,
                0,
                0,
                null
        );
    }

    private static PayrollHistoricalSemanticEarningsService.HistoricalEarning earning(
            PayrollEarningKind kind
    ) {
        return new PayrollHistoricalSemanticEarningsService.HistoricalEarning(
                kind,
                kind.phase(),
                100L,
                null,
                null,
                null,
                null,
                null
        );
    }
}
