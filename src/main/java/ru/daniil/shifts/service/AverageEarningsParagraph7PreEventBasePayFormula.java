package ru.daniil.shifts.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Pure paragraph-7 pre-event BASE_PAY money formula.
 *
 * <p>J3B1 already proves the legal pre-event work window, pricing identity and
 * eligible BASE_PAY quantity. This layer performs no repository lookup, no
 * legal fallback selection and no payroll-source discovery. It only prices the
 * proven quantity using the canonical Native Payroll arithmetic.</p>
 *
 * <p>HOURLY: configured rate * eligible minutes / 60. SALARY: monthly salary *
 * eligible covered minutes / event-month production norm. The final BASE_PAY
 * line is rounded once to minor currency units with HALF_UP.</p>
 */
public final class AverageEarningsParagraph7PreEventBasePayFormula {
    private AverageEarningsParagraph7PreEventBasePayFormula() {
    }

    public static Calculation calculate(
            AverageEarningsParagraph7PreEventBasePayAuthorityService.Resolution authority
    ) {
        Objects.requireNonNull(
                authority,
                "Paragraph-7 BASE_PAY formula requires authority"
        );
        if (!authority.ready()) {
            throw new IllegalArgumentException(
                    "Blocked paragraph-7 BASE_PAY authority cannot reach money formula"
            );
        }
        if (!authority.workedTimePresent()) {
            return new Calculation(
                    authority,
                    0L
            );
        }

        long eligibleMinutes = authority.eligibleBasePayMinutes();
        long amountMinor;
        if ("HOURLY".equals(authority.payMode())) {
            Long configuredRate = authority.configuredHourlyRateMinor();
            if (configuredRate == null || configuredRate <= 0L) {
                throw new IllegalArgumentException(
                        "Ready hourly paragraph-7 authority requires positive configured rate"
                );
            }
            amountMinor = ratioMoney(
                    configuredRate,
                    eligibleMinutes,
                    60L
            );
        } else if ("SALARY".equals(authority.payMode())) {
            Long monthlySalary = authority.monthlySalaryMinor();
            Integer productionNorm = authority.productionNormMinutes();
            if (monthlySalary == null
                    || monthlySalary <= 0L
                    || productionNorm == null
                    || productionNorm <= 0) {
                throw new IllegalArgumentException(
                        "Ready salary paragraph-7 authority requires salary and positive norm"
                );
            }
            amountMinor = ratioMoney(
                    monthlySalary,
                    eligibleMinutes,
                    productionNorm.longValue()
            );
        } else {
            throw new IllegalArgumentException(
                    "Ready paragraph-7 BASE_PAY authority has unsupported pay mode"
            );
        }

        return new Calculation(
                authority,
                amountMinor
        );
    }

    private static long ratioMoney(
            long configuredAmountMinor,
            long eligibleMinutes,
            long denominatorMinutes
    ) {
        if (configuredAmountMinor <= 0L
                || eligibleMinutes < 0L
                || denominatorMinutes <= 0L) {
            throw new IllegalArgumentException(
                    "Paragraph-7 BASE_PAY formula received invalid pricing quantity"
            );
        }
        try {
            return BigDecimal.valueOf(configuredAmountMinor)
                    .multiply(BigDecimal.valueOf(eligibleMinutes))
                    .divide(
                            BigDecimal.valueOf(denominatorMinutes),
                            0,
                            RoundingMode.HALF_UP
                    )
                    .longValueExact();
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException(
                    "Paragraph-7 BASE_PAY amount exceeds minor-unit range",
                    ex
            );
        }
    }

    /**
     * Final pre-event BASE_PAY line produced from one exact J3B1 authority.
     *
     * <p>The authority object is retained verbatim as provenance. A zero amount
     * is valid and deliberately carries no fallback meaning at this layer.</p>
     */
    public record Calculation(
            AverageEarningsParagraph7PreEventBasePayAuthorityService.Resolution authority,
            long basePayAmountMinor
    ) {
        public Calculation {
            Objects.requireNonNull(
                    authority,
                    "Paragraph-7 BASE_PAY calculation requires authority"
            );
            if (!authority.ready()) {
                throw new IllegalArgumentException(
                        "Paragraph-7 BASE_PAY calculation cannot retain blocked authority"
                );
            }
            if (basePayAmountMinor < 0L) {
                throw new IllegalArgumentException(
                        "Paragraph-7 BASE_PAY money must be non-negative"
                );
            }
        }

        public String currencyCode() {
            return authority.currencyCode();
        }
    }
}
