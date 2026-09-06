package ru.daniil.shifts.service;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Source-locked statutory minimum tariff policy for TK RF Article 153.
 *
 * <p>This class owns only the federal statutory tariff floor for qualifying
 * weekend/non-working-holiday work in calendar year 2026. It does not decide
 * whether a source minute qualifies; P1B3A owns that fact. It also does not
 * resolve a worker's actual election of another rest day, local/collective
 * higher rates, remuneration-system compensation/stimulating components,
 * monthly norm position, or Payroll money.</p>
 *
 * <p>The returned {@code additionalTariffBps} is the minimum tariff amount
 * that must be added by the future HOLIDAY_PAY vertical on top of the existing
 * ordinary/base-pay treatment:</p>
 *
 * <ul>
 *   <li>10_000 = +1.00x hourly tariff;</li>
 *   <li>20_000 = +2.00x hourly tariff;</li>
 *   <li>0 = no additional tariff part beyond the ordinary/base-pay treatment.</li>
 * </ul>
 *
 * <p>This is intentionally not a final payable-rate authority. Article 153
 * permits a higher amount by collective agreement, local normative act or
 * employment contract, and the applicable remuneration system may require
 * separate compensation/stimulating components. Those facts must be resolved
 * by later machine-owned authorities before Payroll activation.</p>
 */
public final class Article153EconomicLegalPolicy {

    public static final String LEGAL_REGIME =
            "RU_TK_RF_ARTICLE_153_CALENDAR_2026_V1";

    public static final String LEGAL_BASIS =
            "TK_RF_ARTICLE_153";

    public static final String SOURCE_REVISION =
            "TK_RF_197_FZ_RED_2026_05_25";

    public static final String SOURCE_REFERENCE =
            "CONSULTANT_TK_RF_ARTICLE_153";

    public static final String REST_DAY_AMENDING_ACT =
            "FEDERAL_LAW_339_FZ_2024_09_30_EFFECTIVE_2025_03_01";

    public static final String CONSTITUTIONAL_AUTHORITY =
            "KS_RF_26_P_2018_06_28";

    public static final LocalDate SUPPORTED_FROM =
            LocalDate.of(2026, 1, 1);

    public static final LocalDate SUPPORTED_TO_EXCLUSIVE =
            LocalDate.of(2027, 1, 1);

    private Article153EconomicLegalPolicy() {
    }

    public static Decision resolve(
            LocalDate sourceDate,
            PayMode payMode,
            NormPosition normPosition,
            CompensationChoice compensationChoice
    ) {
        Objects.requireNonNull(
                sourceDate,
                "Article 153 policy requires source date"
        );

        Objects.requireNonNull(
                payMode,
                "Article 153 policy requires pay mode"
        );

        Objects.requireNonNull(
                normPosition,
                "Article 153 policy requires norm position"
        );

        Objects.requireNonNull(
                compensationChoice,
                "Article 153 policy requires compensation choice"
        );

        if (sourceDate.isBefore(SUPPORTED_FROM)
                || !sourceDate.isBefore(SUPPORTED_TO_EXCLUSIVE)) {
            throw new UnsupportedOperationException(
                    "Article 153 economic policy is not source-locked for "
                            + sourceDate
            );
        }

        validateNormPosition(
                payMode,
                normPosition
        );

        int additionalTariffBps =
                minimumAdditionalTariffBps(
                        payMode,
                        normPosition,
                        compensationChoice
                );

        return new Decision(
                sourceDate,
                LEGAL_REGIME,
                LEGAL_BASIS,
                SOURCE_REVISION,
                SOURCE_REFERENCE,
                REST_DAY_AMENDING_ACT,
                CONSTITUTIONAL_AUTHORITY,
                payMode,
                normPosition,
                compensationChoice,
                additionalTariffBps,
                true,
                true,
                compensationChoice
                        == CompensationChoice.OTHER_REST_DAY
        );
    }

    private static void validateNormPosition(
            PayMode payMode,
            NormPosition normPosition
    ) {
        if (payMode == PayMode.HOURLY) {
            if (normPosition
                    != NormPosition.NOT_APPLICABLE) {
                throw new IllegalArgumentException(
                        "HOURLY Article 153 floor must not invent salary norm position"
                );
            }

            return;
        }

        if (normPosition
                == NormPosition.NOT_APPLICABLE) {
            throw new IllegalArgumentException(
                    "SALARY Article 153 floor requires within/above monthly norm authority"
            );
        }
    }

    private static int minimumAdditionalTariffBps(
            PayMode payMode,
            NormPosition normPosition,
            CompensationChoice compensationChoice
    ) {
        if (compensationChoice
                == CompensationChoice.OTHER_REST_DAY) {

            /*
             * Article 153 part 4: work is paid in single amount when another
             * rest day is elected.
             *
             * DutyLog's existing ordinary/base-pay path already owns that
             * single tariff treatment for HOURLY and salary-within-norm work.
             * Salary-above-norm work is not covered by the monthly salary, so
             * one hourly tariff remains additional to salary.
             */
            if (payMode == PayMode.SALARY
                    && normPosition
                    == NormPosition.ABOVE_MONTHLY_NORM) {
                return 10_000;
            }

            return 0;
        }

        if (payMode == PayMode.HOURLY) {
            /*
             * Hourly tariff worker: not less than double hourly tariff total.
             * Ordinary base already carries 1.00x -> HOLIDAY_PAY floor +1.00x.
             */
            return 10_000;
        }

        if (normPosition
                == NormPosition.WITHIN_MONTHLY_NORM) {
            /*
             * Salary worker within monthly norm:
             * at least one hourly/day part of salary above salary.
             */
            return 10_000;
        }

        /*
         * Salary worker above monthly norm:
         * at least two hourly/day parts of salary above salary.
         */
        return 20_000;
    }

    public enum PayMode {
        HOURLY,
        SALARY
    }

    public enum NormPosition {
        NOT_APPLICABLE,
        WITHIN_MONTHLY_NORM,
        ABOVE_MONTHLY_NORM
    }

    public enum CompensationChoice {
        ENHANCED_PAY,
        OTHER_REST_DAY
    }

    public record Decision(
            LocalDate sourceDate,
            String legalRegime,
            String legalBasis,
            String sourceRevision,
            String sourceReference,
            String restDayAmendingAct,
            String constitutionalAuthority,
            PayMode payMode,
            NormPosition normPosition,
            CompensationChoice compensationChoice,
            int additionalTariffBps,
            boolean statutoryFloorOnly,
            boolean localHigherRateAuthorityRequiredForFinalPricing,
            boolean employeeRestDayElectionAuthorityRequired
    ) {
        public Decision {
            Objects.requireNonNull(
                    sourceDate,
                    "Article 153 decision requires source date"
            );

            requireText(
                    legalRegime,
                    "Article 153 legal regime is required"
            );

            requireText(
                    legalBasis,
                    "Article 153 legal basis is required"
            );

            requireText(
                    sourceRevision,
                    "Article 153 source revision is required"
            );

            requireText(
                    sourceReference,
                    "Article 153 source reference is required"
            );

            requireText(
                    restDayAmendingAct,
                    "Article 153 rest-day amending act is required"
            );

            requireText(
                    constitutionalAuthority,
                    "Article 153 constitutional authority is required"
            );

            Objects.requireNonNull(
                    payMode,
                    "Article 153 decision requires pay mode"
            );

            Objects.requireNonNull(
                    normPosition,
                    "Article 153 decision requires norm position"
            );

            Objects.requireNonNull(
                    compensationChoice,
                    "Article 153 decision requires compensation choice"
            );

            if (additionalTariffBps < 0) {
                throw new IllegalArgumentException(
                        "Article 153 additional tariff floor cannot be negative"
                );
            }

            if (!statutoryFloorOnly) {
                throw new IllegalArgumentException(
                        "P1B3B1 decision must remain statutory-floor-only"
                );
            }

            if (!localHigherRateAuthorityRequiredForFinalPricing) {
                throw new IllegalArgumentException(
                        "Final Article 153 pricing must retain local-higher-rate authority boundary"
                );
            }

            if (employeeRestDayElectionAuthorityRequired
                    != (compensationChoice
                    == CompensationChoice.OTHER_REST_DAY)) {
                throw new IllegalArgumentException(
                        "Article 153 rest-day election authority flag is inconsistent"
                );
            }
        }

        private static void requireText(
                String value,
                String message
        ) {
            if (value == null
                    || value.isBlank()) {
                throw new IllegalArgumentException(
                        message
                );
            }
        }
    }
}
