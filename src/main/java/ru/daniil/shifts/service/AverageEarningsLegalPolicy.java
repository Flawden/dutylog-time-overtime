package ru.daniil.shifts.service;

import ru.daniil.shifts.model.PayrollEarningKind;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Effective-dated legal classification boundary for average earnings.
 *
 * <p>This layer classifies machine-owned historical facts only. It does not
 * calculate included amounts, excluded calendar-day quantities, premium
 * allocation, average daily earnings or vacation pay.</p>
 *
 * <p>The currently supported legal regime is Government Resolution
 * No. 540 of 24 April 2025, effective from 1 September 2025 and limited
 * to the period before 1 September 2031. Dates outside that legal window
 * fail closed rather than silently reusing the wrong law.</p>
 */
public final class AverageEarningsLegalPolicy {

    private static final LocalDate PP_540_FROM =
            LocalDate.of(
                    2025,
                    9,
                    1
            );

    private static final LocalDate PP_540_TO_EXCLUSIVE =
            LocalDate.of(
                    2031,
                    9,
                    1
            );

    private AverageEarningsLegalPolicy() {
    }

    public enum LegalRegime {
        RU_PP_540_2025
    }

    public enum LegalBasis {
        PP_540_P2,
        PP_540_P2_AND_P15,
        PP_540_P5_A,
        PP_540_P5_B,
        PP_540_P5_E,
        UNRESOLVED
    }

    public enum AbsenceTreatment {
        EXCLUDE_PRESERVED_AVERAGE,
        EXCLUDE_TEMPORARY_DISABILITY,
        EXCLUDE_OTHER_RELEASE_FROM_WORK,
        UNRESOLVED
    }

    public enum EarningTreatment {
        ORDINARY_REMUNERATION,
        PREMIUM_SPECIAL_RULE,
        EXCLUDE_PRESERVED_AVERAGE,
        UNRESOLVED
    }

    public static LegalRegime requireRegime(
            LocalDate eventDate
    ) {
        Objects.requireNonNull(
                eventDate,
                "Average earnings legal policy requires event date"
        );

        if (eventDate.isBefore(
                PP_540_FROM
        ) || !eventDate.isBefore(
                PP_540_TO_EXCLUSIVE
        )) {
            throw new UnsupportedOperationException(
                    "Average earnings legal regime is not implemented for "
                            + eventDate
            );
        }

        return LegalRegime.RU_PP_540_2025;
    }

    public static AbsenceDecision classifyAbsence(
            LocalDate eventDate,
            AverageEarningsReferenceFactsService.AbsenceFact fact
    ) {
        LegalRegime regime =
                requireRegime(
                        eventDate
                );

        Objects.requireNonNull(
                fact,
                "Average earnings absence fact is required"
        );

        String systemCode =
                fact.systemCode();

        if (systemCode == null
                || systemCode.isBlank()) {
            return unresolvedAbsence(
                    regime
            );
        }

        return switch (systemCode) {
            case "VACATION" -> {
                requireMachineTuple(
                        fact,
                        "VACATION_DAYS",
                        "VACATION_ALLOWANCE"
                );

                yield new AbsenceDecision(
                        regime,
                        AbsenceTreatment.EXCLUDE_PRESERVED_AVERAGE,
                        LegalBasis.PP_540_P5_A
                );
            }

            case "SICK" -> {
                requireMachineTuple(
                        fact,
                        "NONE",
                        "SICK_PAY"
                );

                yield new AbsenceDecision(
                        regime,
                        AbsenceTreatment.EXCLUDE_TEMPORARY_DISABILITY,
                        LegalBasis.PP_540_P5_B
                );
            }

            case "UNPAID" -> {
                requireMachineTuple(
                        fact,
                        "NONE",
                        "UNPAID"
                );

                yield new AbsenceDecision(
                        regime,
                        AbsenceTreatment.EXCLUDE_OTHER_RELEASE_FROM_WORK,
                        LegalBasis.PP_540_P5_E
                );
            }

            case "TIME_OFF" -> {
                requireMachineTuple(
                        fact,
                        "TIME_OFF_HOURS",
                        "OVERTIME_BANK"
                );

                yield new AbsenceDecision(
                        regime,
                        AbsenceTreatment.EXCLUDE_OTHER_RELEASE_FROM_WORK,
                        LegalBasis.PP_540_P5_E
                );
            }

            case "OTHER" ->
                    unresolvedAbsence(
                            regime
                    );

            default ->
                    unresolvedAbsence(
                            regime
                    );
        };
    }

    public static EarningDecision classifyEarning(
            LocalDate eventDate,
            PayrollHistoricalSemanticEarningsService.HistoricalEarning earning
    ) {
        LegalRegime regime =
                requireRegime(
                        eventDate
                );

        Objects.requireNonNull(
                earning,
                "Historical earning is required"
        );

        PayrollEarningKind kind =
                Objects.requireNonNull(
                        earning.kind(),
                        "Historical earning kind is required"
                );

        if (earning.phase()
                != kind.phase()) {
            throw new IllegalStateException(
                    "Historical earning kind/phase provenance mismatch"
            );
        }

        return switch (kind) {
            case BASE_PAY,
                    HOLIDAY_PAY,
                    NIGHT_PREMIUM,
                    HARMFUL_CONDITIONS,
                    COMBINATION,
                    REGIONAL_COEFFICIENT ->
                    new EarningDecision(
                            regime,
                            EarningTreatment.ORDINARY_REMUNERATION,
                            LegalBasis.PP_540_P2
                    );

            case MONTHLY_BONUS,
                    ONE_TIME_BONUS ->
                    new EarningDecision(
                            regime,
                            EarningTreatment.PREMIUM_SPECIAL_RULE,
                            LegalBasis.PP_540_P2_AND_P15
                    );

            case VACATION_PAY ->
                    new EarningDecision(
                            regime,
                            EarningTreatment.EXCLUDE_PRESERVED_AVERAGE,
                            LegalBasis.PP_540_P5_A
                    );

            case MEDICAL_COMPENSATION ->
                    new EarningDecision(
                            regime,
                            EarningTreatment.UNRESOLVED,
                            LegalBasis.UNRESOLVED
                    );
        };
    }

    private static AbsenceDecision unresolvedAbsence(
            LegalRegime regime
    ) {
        return new AbsenceDecision(
                regime,
                AbsenceTreatment.UNRESOLVED,
                LegalBasis.UNRESOLVED
        );
    }

    private static void requireMachineTuple(
            AverageEarningsReferenceFactsService.AbsenceFact fact,
            String expectedBalancePolicy,
            String expectedCompensationPolicy
    ) {
        if (!expectedBalancePolicy.equals(
                fact.balancePolicy()
        ) || !expectedCompensationPolicy.equals(
                fact.compensationPolicy()
        )) {
            throw new IllegalStateException(
                    "Average earnings trusted absence machine tuple is inconsistent: "
                            + fact.systemCode()
            );
        }
    }

    public record AbsenceDecision(
            LegalRegime regime,
            AbsenceTreatment treatment,
            LegalBasis basis
    ) {
        public AbsenceDecision {
            Objects.requireNonNull(
                    regime,
                    "Legal regime is required"
            );

            Objects.requireNonNull(
                    treatment,
                    "Absence treatment is required"
            );

            Objects.requireNonNull(
                    basis,
                    "Absence legal basis is required"
            );
        }

        public boolean resolved() {
            return treatment
                    != AbsenceTreatment.UNRESOLVED;
        }
    }

    public record EarningDecision(
            LegalRegime regime,
            EarningTreatment treatment,
            LegalBasis basis
    ) {
        public EarningDecision {
            Objects.requireNonNull(
                    regime,
                    "Legal regime is required"
            );

            Objects.requireNonNull(
                    treatment,
                    "Earning treatment is required"
            );

            Objects.requireNonNull(
                    basis,
                    "Earning legal basis is required"
            );
        }

        public boolean resolved() {
            return treatment
                    != EarningTreatment.UNRESOLVED;
        }
    }
}
