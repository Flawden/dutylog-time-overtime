package ru.daniil.shifts.service;

import java.math.BigInteger;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Objects;

/**
 * Paragraph-9 MONEY formula for calendar-day annual paid vacation.
 *
 * <p>K supplies one exact average-daily money rate and L supplies the exact
 * calendar-day quantity subject to payment. M validates their common event
 * identity, multiplies them as an exact rational amount, and performs exactly
 * one final conversion to whole minor currency units.</p>
 *
 * <p>Russian Government Decree No. 540 paragraph 9 is the multiplication rule.
 * {@link #ROUNDING_POLICY} is an explicit DutyLog monetary policy at the final
 * minor-unit boundary; it is not inferred from paragraph 9. There is no
 * intermediate monetary rounding, no inexact primitive arithmetic, and no vacation planner
 * quantity inference in this layer.</p>
 */
public final class VacationPayMoneyFormula {
    public static final String RULE_ID = "PP_540_P9_VACATION_PAY_MONEY";
    public static final String ROUNDING_POLICY = "FINAL_MINOR_UNIT_HALF_UP";
    public static final String DAILY_AUTHORITY_BLOCKED =
            "PP_540_P9_VACATION_PAY_DAILY_AUTHORITY_BLOCKED";
    public static final String PAYABLE_DAYS_AUTHORITY_BLOCKED =
            "PP_540_P9_VACATION_PAY_PAYABLE_DAYS_AUTHORITY_BLOCKED";
    public static final String IDENTITY_MISMATCH =
            "PP_540_P9_VACATION_PAY_IDENTITY_MISMATCH";
    public static final String CURRENCY_REQUIRED =
            "PP_540_P9_VACATION_PAY_CURRENCY_REQUIRED";
    public static final String AMOUNT_OVERFLOW =
            "PP_540_P9_VACATION_PAY_AMOUNT_OVERFLOW";

    private static final BigInteger TWO = BigInteger.valueOf(2L);
    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);

    private VacationPayMoneyFormula() {
    }

    public static Resolution calculate(
            VacationAverageUnifiedDailyResolver.Resolution dailyAuthority,
            VacationPayableDaysFactService.Resolution payableDaysAuthority
    ) {
        Objects.requireNonNull(
                dailyAuthority,
                "Vacation pay money formula requires K daily authority"
        );
        Objects.requireNonNull(
                payableDaysAuthority,
                "Vacation pay money formula requires L payable-days authority"
        );

        LocalDate eventDate = Objects.requireNonNull(
                dailyAuthority.eventDate(),
                "Vacation pay money formula requires event date"
        );
        YearMonth eventMonth = YearMonth.from(eventDate);

        if (!eventMonth.equals(dailyAuthority.eventMonth())
                || !eventDate.equals(payableDaysAuthority.eventDate())
                || !eventMonth.equals(payableDaysAuthority.eventMonth())) {
            return Resolution.blocked(
                    eventDate,
                    dailyAuthority,
                    payableDaysAuthority,
                    BlockingStage.IDENTITY,
                    IDENTITY_MISMATCH,
                    null,
                    "K and L vacation-pay authorities do not share one event identity"
            );
        }

        if (!dailyAuthority.ready()) {
            return Resolution.blocked(
                    eventDate,
                    dailyAuthority,
                    payableDaysAuthority,
                    BlockingStage.DAILY_AUTHORITY,
                    DAILY_AUTHORITY_BLOCKED,
                    normalizedUpstreamReason(dailyAuthority.blockingReason()),
                    "K exact average-daily authority is blocked"
            );
        }
        if (!payableDaysAuthority.ready()) {
            return Resolution.blocked(
                    eventDate,
                    dailyAuthority,
                    payableDaysAuthority,
                    BlockingStage.PAYABLE_DAYS_AUTHORITY,
                    PAYABLE_DAYS_AUTHORITY_BLOCKED,
                    normalizedUpstreamReason(payableDaysAuthority.blockingReason()),
                    "L payable-vacation-days authority is blocked"
            );
        }

        if (!eventDate.equals(payableDaysAuthority.vacationFrom())
                || payableDaysAuthority.absencePeriodId() == null
                || payableDaysAuthority.absencePeriodId() <= 0L
                || !Objects.equals(
                        payableDaysAuthority.absencePeriodId(),
                        payableDaysAuthority.requestedAbsencePeriodId()
                )) {
            return Resolution.blocked(
                    eventDate,
                    dailyAuthority,
                    payableDaysAuthority,
                    BlockingStage.IDENTITY,
                    IDENTITY_MISMATCH,
                    null,
                    "Ready L authority does not expose the exact vacation event identity"
            );
        }

        String currencyCode = dailyAuthority.currencyCode();
        if (!validCurrency(currencyCode)) {
            return Resolution.blocked(
                    eventDate,
                    dailyAuthority,
                    payableDaysAuthority,
                    BlockingStage.CURRENCY,
                    CURRENCY_REQUIRED,
                    null,
                    "Final vacation-pay money requires a three-letter currency identity"
            );
        }

        VacationAverageDailyEarningsFormula.ExactMoneyPerDay averageDaily =
                dailyAuthority.averageDaily();
        if (averageDaily == null) {
            return Resolution.blocked(
                    eventDate,
                    dailyAuthority,
                    payableDaysAuthority,
                    BlockingStage.DAILY_AUTHORITY,
                    DAILY_AUTHORITY_BLOCKED,
                    null,
                    "Ready K authority has no exact average-daily rate"
            );
        }

        int payableCalendarDays = payableDaysAuthority.payableCalendarDays();
        if (payableCalendarDays < 0) {
            return Resolution.blocked(
                    eventDate,
                    dailyAuthority,
                    payableDaysAuthority,
                    BlockingStage.IDENTITY,
                    IDENTITY_MISMATCH,
                    null,
                    "Ready L authority exposes a negative payable-day quantity"
            );
        }

        ExactVacationPay exact = new ExactVacationPay(
                averageDaily.numeratorMinor()
                        .multiply(BigInteger.valueOf(payableCalendarDays)),
                averageDaily.denominatorDays()
        );

        final long roundedMinor;
        try {
            roundedMinor = roundHalfUpToMinor(exact);
        } catch (ArithmeticException ex) {
            return Resolution.blocked(
                    eventDate,
                    dailyAuthority,
                    payableDaysAuthority,
                    BlockingStage.AMOUNT,
                    AMOUNT_OVERFLOW,
                    null,
                    "Final vacation-pay amount does not fit signed 64-bit minor units"
            );
        }

        return Resolution.ready(
                eventDate,
                dailyAuthority,
                payableDaysAuthority,
                currencyCode,
                averageDaily,
                payableCalendarDays,
                exact,
                roundedMinor
        );
    }

    static long roundHalfUpToMinor(ExactVacationPay exact) {
        Objects.requireNonNull(exact, "Exact vacation pay is required");
        BigInteger[] quotientAndRemainder =
                exact.numeratorMinor().divideAndRemainder(exact.denominator());
        BigInteger rounded = quotientAndRemainder[0];
        if (quotientAndRemainder[1].multiply(TWO).compareTo(exact.denominator()) >= 0) {
            rounded = rounded.add(BigInteger.ONE);
        }
        if (rounded.signum() < 0 || rounded.compareTo(LONG_MAX) > 0) {
            throw new ArithmeticException("Vacation pay minor amount overflow");
        }
        return rounded.longValueExact();
    }

    private static boolean validCurrency(String currencyCode) {
        return currencyCode != null && currencyCode.matches("[A-Z]{3}");
    }

    private static String normalizedUpstreamReason(String reason) {
        return reason == null || reason.isBlank() ? null : reason;
    }

    public enum BlockingStage {
        DAILY_AUTHORITY,
        PAYABLE_DAYS_AUTHORITY,
        IDENTITY,
        CURRENCY,
        AMOUNT
    }

    /** Reduced exact minor-currency amount before the single final rounding. */
    public record ExactVacationPay(
            BigInteger numeratorMinor,
            BigInteger denominator
    ) {
        public ExactVacationPay {
            Objects.requireNonNull(numeratorMinor, "Exact vacation-pay numerator is required");
            Objects.requireNonNull(denominator, "Exact vacation-pay denominator is required");
            if (numeratorMinor.signum() < 0) {
                throw new IllegalArgumentException(
                        "Exact vacation-pay numerator must be non-negative"
                );
            }
            if (denominator.signum() <= 0) {
                throw new IllegalArgumentException(
                        "Exact vacation-pay denominator must be positive"
                );
            }
            if (numeratorMinor.signum() == 0) {
                numeratorMinor = BigInteger.ZERO;
                denominator = BigInteger.ONE;
            } else {
                BigInteger gcd = numeratorMinor.gcd(denominator);
                numeratorMinor = numeratorMinor.divide(gcd);
                denominator = denominator.divide(gcd);
            }
        }
    }

    public record Resolution(
            LocalDate eventDate,
            YearMonth eventMonth,
            boolean ready,
            BlockingStage blockingStage,
            String blockingReason,
            String upstreamBlockingReason,
            String blockingMessage,
            VacationAverageUnifiedDailyResolver.Resolution dailyAuthority,
            VacationPayableDaysFactService.Resolution payableDaysAuthority,
            String currencyCode,
            VacationAverageDailyEarningsFormula.ExactMoneyPerDay averageDaily,
            int payableCalendarDays,
            ExactVacationPay exactVacationPay,
            Long vacationPayMinor,
            String roundingPolicy
    ) {
        public Resolution {
            Objects.requireNonNull(eventDate, "Vacation pay money result requires event date");
            Objects.requireNonNull(eventMonth, "Vacation pay money result requires event month");
            Objects.requireNonNull(dailyAuthority, "Vacation pay money result requires K provenance");
            Objects.requireNonNull(payableDaysAuthority, "Vacation pay money result requires L provenance");
            if (!eventMonth.equals(YearMonth.from(eventDate))) {
                throw new IllegalArgumentException("Vacation pay money result event identity is invalid");
            }
            if (ready == (blockingReason != null)) {
                throw new IllegalArgumentException("Vacation pay money result state is invalid");
            }
            if (ready) {
                if (blockingStage != null
                        || blockingMessage != null
                        || upstreamBlockingReason != null
                        || !dailyAuthority.ready()
                        || !payableDaysAuthority.ready()
                        || !eventDate.equals(dailyAuthority.eventDate())
                        || !eventDate.equals(payableDaysAuthority.eventDate())
                        || !eventDate.equals(payableDaysAuthority.vacationFrom())
                        || !validCurrency(currencyCode)
                        || averageDaily == null
                        || payableCalendarDays < 0
                        || exactVacationPay == null
                        || vacationPayMinor == null
                        || vacationPayMinor < 0L
                        || !ROUNDING_POLICY.equals(roundingPolicy)) {
                    throw new IllegalArgumentException(
                            "Ready vacation pay money result is incomplete"
                    );
                }
            } else {
                if (blockingStage == null
                        || blockingMessage == null
                        || blockingMessage.isBlank()
                        || currencyCode != null
                        || averageDaily != null
                        || payableCalendarDays != 0
                        || exactVacationPay != null
                        || vacationPayMinor != null
                        || roundingPolicy != null) {
                    throw new IllegalArgumentException(
                            "Blocked vacation pay money result cannot expose partial money"
                    );
                }
            }
        }

        static Resolution ready(
                LocalDate eventDate,
                VacationAverageUnifiedDailyResolver.Resolution dailyAuthority,
                VacationPayableDaysFactService.Resolution payableDaysAuthority,
                String currencyCode,
                VacationAverageDailyEarningsFormula.ExactMoneyPerDay averageDaily,
                int payableCalendarDays,
                ExactVacationPay exactVacationPay,
                long vacationPayMinor
        ) {
            return new Resolution(
                    eventDate,
                    YearMonth.from(eventDate),
                    true,
                    null,
                    null,
                    null,
                    null,
                    dailyAuthority,
                    payableDaysAuthority,
                    currencyCode,
                    averageDaily,
                    payableCalendarDays,
                    exactVacationPay,
                    vacationPayMinor,
                    ROUNDING_POLICY
            );
        }

        static Resolution blocked(
                LocalDate eventDate,
                VacationAverageUnifiedDailyResolver.Resolution dailyAuthority,
                VacationPayableDaysFactService.Resolution payableDaysAuthority,
                BlockingStage blockingStage,
                String blockingReason,
                String upstreamBlockingReason,
                String blockingMessage
        ) {
            Objects.requireNonNull(blockingStage, "Vacation pay money blocker stage is required");
            if (blockingReason == null || blockingReason.isBlank()) {
                throw new IllegalArgumentException("Vacation pay money blocker reason is required");
            }
            if (blockingMessage == null || blockingMessage.isBlank()) {
                throw new IllegalArgumentException("Vacation pay money blocker message is required");
            }
            return new Resolution(
                    eventDate,
                    YearMonth.from(eventDate),
                    false,
                    blockingStage,
                    blockingReason,
                    upstreamBlockingReason,
                    blockingMessage,
                    dailyAuthority,
                    payableDaysAuthority,
                    null,
                    null,
                    0,
                    null,
                    null,
                    null
            );
        }
    }
}
