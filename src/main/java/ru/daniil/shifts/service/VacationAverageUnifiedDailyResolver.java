package ru.daniil.shifts.service;

import java.math.BigInteger;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Unified exact average-daily resolver for calendar-day vacation after the
 * ordered PRIMARY -> P6 -> P7 -> P8 legal basis has already been selected.
 *
 * <p>This layer does not choose a fallback branch. J5 owns that policy.
 * It only converts the selected branch into one exact minor-currency-units per
 * calendar-day rate without intermediate monetary rounding.</p>
 *
 * <p>Reference-period branches reuse the already-proven F3I calculation.
 * Paragraph 7 requires an explicit paragraph-10 calendar basis because
 * actually worked days are not interchangeable with calendar days attributable
 * to worked time. Paragraph 8 requires an explicit formula-basis authority;
 * this resolver never invents a tariff/salary conversion policy.</p>
 *
 * <p>No payable vacation-day fact and no final vacation-pay money are produced
 * here. Those remain later F3L/F3M responsibilities.</p>
 */
public final class VacationAverageUnifiedDailyResolver {

    public static final String ORDERED_FALLBACK_BLOCKED =
            "PP_540_VACATION_DAILY_ORDERED_FALLBACK_BLOCKED";
    public static final String REFERENCE_CALCULATION_BLOCKED =
            "PP_540_VACATION_DAILY_REFERENCE_CALCULATION_BLOCKED";
    public static final String REFERENCE_IDENTITY_MISMATCH =
            "PP_540_VACATION_DAILY_REFERENCE_IDENTITY_MISMATCH";
    public static final String PARAGRAPH_7_CALENDAR_BASIS_REQUIRED =
            "PP_540_VACATION_DAILY_P7_CALENDAR_BASIS_REQUIRED";
    public static final String PARAGRAPH_7_CALENDAR_IDENTITY_MISMATCH =
            "PP_540_VACATION_DAILY_P7_CALENDAR_IDENTITY_MISMATCH";
    public static final String PARAGRAPH_8_FORMULA_BASIS_REQUIRED =
            "PP_540_VACATION_DAILY_P8_FORMULA_BASIS_REQUIRED";
    public static final String PARAGRAPH_8_FORMULA_IDENTITY_MISMATCH =
            "PP_540_VACATION_DAILY_P8_FORMULA_IDENTITY_MISMATCH";

    private VacationAverageUnifiedDailyResolver() {
    }

    public static Resolution resolve(
            AverageEarningsOrderedFallbackResolver.Resolution orderedFallback,
            Supplier<VacationAveragePrimaryCalculationService.Resolution>
                    referenceCalculationSupplier,
            Supplier<Paragraph7CalendarBasis> paragraph7CalendarBasisSupplier,
            Supplier<Paragraph8FormulaBasis> paragraph8FormulaBasisSupplier
    ) {
        Objects.requireNonNull(
                orderedFallback,
                "Unified vacation daily resolver requires ordered fallback resolution"
        );
        Objects.requireNonNull(
                referenceCalculationSupplier,
                "Unified vacation daily resolver requires reference calculation supplier"
        );
        Objects.requireNonNull(
                paragraph7CalendarBasisSupplier,
                "Unified vacation daily resolver requires paragraph-7 calendar supplier"
        );
        Objects.requireNonNull(
                paragraph8FormulaBasisSupplier,
                "Unified vacation daily resolver requires paragraph-8 formula supplier"
        );

        LocalDate eventDate = Objects.requireNonNull(
                orderedFallback.eventDate(),
                "Unified vacation daily resolver requires event date"
        );
        YearMonth eventMonth = YearMonth.from(eventDate);

        AverageEarningsLegalPolicy.requireRegime(eventDate);

        if (!orderedFallback.eventMonth().equals(eventMonth)) {
            return Resolution.blocked(
                    eventDate,
                    orderedFallback,
                    BlockingStage.ORDERED_FALLBACK,
                    REFERENCE_IDENTITY_MISMATCH,
                    "Ordered fallback event-month identity does not match event date"
            );
        }

        if (!orderedFallback.ready()) {
            return Resolution.blocked(
                    eventDate,
                    orderedFallback,
                    BlockingStage.ORDERED_FALLBACK,
                    requireReason(
                            orderedFallback.blockingReason(),
                            ORDERED_FALLBACK_BLOCKED
                    ),
                    "Ordered fallback selection is blocked"
            );
        }

        if (orderedFallback.selection() == null) {
            return Resolution.blocked(
                    eventDate,
                    orderedFallback,
                    BlockingStage.ORDERED_FALLBACK,
                    ORDERED_FALLBACK_BLOCKED,
                    "Ready ordered fallback has no selected legal branch"
            );
        }

        return switch (orderedFallback.selection()) {
        case PRIMARY_REFERENCE_PERIOD,
                PARAGRAPH_6_PRECEDING_REFERENCE_PERIOD ->
                resolveReference(
                        eventDate,
                        orderedFallback,
                        referenceCalculationSupplier
                );
        case PARAGRAPH_7_PRE_EVENT_ACCRUED_WAGE ->
                resolveParagraph7(
                        eventDate,
                        orderedFallback,
                        paragraph7CalendarBasisSupplier
                );
        case PARAGRAPH_8_ESTABLISHED_TARIFF_OR_SALARY ->
                resolveParagraph8(
                        eventDate,
                        orderedFallback,
                        paragraph8FormulaBasisSupplier
                );
        };
    }

    private static Resolution resolveReference(
            LocalDate eventDate,
            AverageEarningsOrderedFallbackResolver.Resolution orderedFallback,
            Supplier<VacationAveragePrimaryCalculationService.Resolution> supplier
    ) {
        AverageEarningsReferenceWindow selected =
                orderedFallback.selectedReferenceWindow();

        if (selected == null) {
            return Resolution.blocked(
                    eventDate,
                    orderedFallback,
                    BlockingStage.REFERENCE_CALCULATION,
                    REFERENCE_IDENTITY_MISMATCH,
                    "Selected reference-period branch has no reference window"
            );
        }

        VacationAveragePrimaryCalculationService.Resolution calculation =
                supplier.get();

        if (calculation == null) {
            return Resolution.blocked(
                    eventDate,
                    orderedFallback,
                    BlockingStage.REFERENCE_CALCULATION,
                    REFERENCE_CALCULATION_BLOCKED,
                    "Selected reference-period calculation returned no authority"
            );
        }

        if (!referenceIdentityMatches(
                calculation,
                eventDate,
                selected
        )) {
            return Resolution.blocked(
                    eventDate,
                    orderedFallback,
                    BlockingStage.REFERENCE_CALCULATION,
                    REFERENCE_IDENTITY_MISMATCH,
                    "Selected reference-period calculation identity does not match J5"
            );
        }

        if (!calculation.ready()) {
            return Resolution.blocked(
                    eventDate,
                    orderedFallback,
                    BlockingStage.REFERENCE_CALCULATION,
                    requireReason(
                            calculation.blockingReason(),
                            REFERENCE_CALCULATION_BLOCKED
                    ),
                    "Selected reference-period average-daily calculation is blocked"
            );
        }

        VacationAverageDailyEarningsFormula.ExactMoneyPerDay daily =
                calculation.averageDaily();

        if (daily == null
                || (daily.numeratorMinor().signum() > 0
                        && !validCurrency(calculation.currencyCode()))) {
            return Resolution.blocked(
                    eventDate,
                    orderedFallback,
                    BlockingStage.REFERENCE_CALCULATION,
                    REFERENCE_IDENTITY_MISMATCH,
                    "Ready reference-period calculation has incomplete money identity"
            );
        }

        return Resolution.readyReference(
                eventDate,
                orderedFallback,
                calculation.currencyCode(),
                daily,
                calculation
        );
    }

    private static Resolution resolveParagraph7(
            LocalDate eventDate,
            AverageEarningsOrderedFallbackResolver.Resolution orderedFallback,
            Supplier<Paragraph7CalendarBasis> supplier
    ) {
        AverageEarningsParagraph7PreEventAccruedWageAuthority.Resolution paragraph7 =
                orderedFallback.paragraph7Authority();

        // J5 already proved p7 readiness, event identity, accrued wage and worked
        // time. K consumes that selected authority rather than re-deciding J5.
        if (paragraph7 == null
                || !validCurrency(paragraph7.currencyCode())) {
            return Resolution.blocked(
                    eventDate,
                    orderedFallback,
                    BlockingStage.PARAGRAPH_7_CALENDAR_BASIS,
                    PARAGRAPH_7_CALENDAR_IDENTITY_MISMATCH,
                    "Paragraph-7 selected authority has incomplete money identity"
            );
        }

        Paragraph7CalendarBasis basis =
                supplier.get();

        if (basis == null) {
            return Resolution.blocked(
                    eventDate,
                    orderedFallback,
                    BlockingStage.PARAGRAPH_7_CALENDAR_BASIS,
                    PARAGRAPH_7_CALENDAR_BASIS_REQUIRED,
                    "Paragraph-7 vacation calculation requires explicit paragraph-10 calendar basis"
            );
        }

        if (!basis.eventDate().equals(eventDate)
                || !basis.eventMonth().equals(YearMonth.from(eventDate))
                || !basis.periodFrom().equals(YearMonth.from(eventDate).atDay(1))
                || !basis.cutoffExclusive().equals(eventDate)) {
            return Resolution.blocked(
                    eventDate,
                    orderedFallback,
                    BlockingStage.PARAGRAPH_7_CALENDAR_BASIS,
                    PARAGRAPH_7_CALENDAR_IDENTITY_MISMATCH,
                    "Paragraph-7 calendar basis identity does not match selected J5 event"
            );
        }

        VacationAverageDailyEarningsFormula.ExactMoneyPerDay daily =
                VacationAverageDailyEarningsFormula.calculate(
                        paragraph7.totalAccruedWageMinor(),
                        basis.denominatorDays()
                );

        return Resolution.readyParagraph7(
                eventDate,
                orderedFallback,
                paragraph7.currencyCode(),
                daily,
                basis
        );
    }

    private static Resolution resolveParagraph8(
            LocalDate eventDate,
            AverageEarningsOrderedFallbackResolver.Resolution orderedFallback,
            Supplier<Paragraph8FormulaBasis> supplier
    ) {
        AverageEarningsParagraph8TariffSalaryAuthorityService.Resolution paragraph8 =
                orderedFallback.paragraph8Authority();

        // J5 already proved p8 readiness and exact J4 event identity. K only
        // requires the selected monetary identity before applying explicit
        // formula-basis authority.
        if (paragraph8 == null
                || paragraph8.establishedAmountMinor() == null
                || !validCurrency(paragraph8.currencyCode())) {
            return Resolution.blocked(
                    eventDate,
                    orderedFallback,
                    BlockingStage.PARAGRAPH_8_FORMULA_BASIS,
                    PARAGRAPH_8_FORMULA_IDENTITY_MISMATCH,
                    "Paragraph-8 selected authority has incomplete compensation identity"
            );
        }

        Paragraph8FormulaBasis basis =
                supplier.get();

        if (basis == null) {
            return Resolution.blocked(
                    eventDate,
                    orderedFallback,
                    BlockingStage.PARAGRAPH_8_FORMULA_BASIS,
                    PARAGRAPH_8_FORMULA_BASIS_REQUIRED,
                    "Paragraph-8 vacation calculation requires explicit formula-basis authority"
            );
        }

        if (!paragraph8BasisMatches(
                basis,
                paragraph8,
                eventDate
        )) {
            return Resolution.blocked(
                    eventDate,
                    orderedFallback,
                    BlockingStage.PARAGRAPH_8_FORMULA_BASIS,
                    PARAGRAPH_8_FORMULA_IDENTITY_MISMATCH,
                    "Paragraph-8 formula basis does not match established tariff/salary authority"
            );
        }

        VacationAverageDailyEarningsFormula.ExactMoneyPerDay daily =
                calculateParagraph8(
                        paragraph8,
                        basis
                );

        return Resolution.readyParagraph8(
                eventDate,
                orderedFallback,
                paragraph8.currencyCode(),
                daily,
                basis
        );
    }

    private static boolean referenceIdentityMatches(
            VacationAveragePrimaryCalculationService.Resolution calculation,
            LocalDate eventDate,
            AverageEarningsReferenceWindow selected
    ) {
        return calculation.eventDate().equals(eventDate)
                && calculation.eventMonth().equals(selected.eventMonth())
                && calculation.referenceFrom().equals(selected.referenceFrom())
                && calculation.referenceTo().equals(selected.referenceTo());
    }

    private static boolean paragraph8BasisMatches(
            Paragraph8FormulaBasis basis,
            AverageEarningsParagraph8TariffSalaryAuthorityService.Resolution paragraph8,
            LocalDate eventDate
    ) {
        if (!basis.eventDate().equals(eventDate)
                || !basis.eventMonth().equals(YearMonth.from(eventDate))
                || basis.establishedBasis() != paragraph8.establishedBasis()
                || !basis.currencyCode().equals(paragraph8.currencyCode())) {
            return false;
        }

        return switch (paragraph8.establishedBasis()) {
        case MONTHLY_OFFICIAL_SALARY ->
                basis.policy()
                        == Paragraph8FormulaPolicy.MONTHLY_OFFICIAL_SALARY_DIV_29_3
                        && basis.annualNormMinutes() == null;
        case HOURLY_TARIFF_RATE ->
                basis.policy()
                        == Paragraph8FormulaPolicy.HOURLY_TARIFF_AVERAGE_MONTHLY_NORM_DIV_29_3
                        && basis.annualNormMinutes() != null
                        && basis.annualNormMinutes() > 0L;
        };
    }

    private static VacationAverageDailyEarningsFormula.ExactMoneyPerDay
            calculateParagraph8(
                    AverageEarningsParagraph8TariffSalaryAuthorityService.Resolution paragraph8,
                    Paragraph8FormulaBasis basis
            ) {
        BigInteger amount =
                BigInteger.valueOf(
                        paragraph8.establishedAmountMinor()
                );

        return switch (basis.policy()) {
        case MONTHLY_OFFICIAL_SALARY_DIV_29_3 ->
                new VacationAverageDailyEarningsFormula.ExactMoneyPerDay(
                        amount.multiply(BigInteger.TEN),
                        BigInteger.valueOf(293L)
                );
        case HOURLY_TARIFF_AVERAGE_MONTHLY_NORM_DIV_29_3 ->
                new VacationAverageDailyEarningsFormula.ExactMoneyPerDay(
                        amount
                                .multiply(BigInteger.valueOf(
                                        basis.annualNormMinutes()
                                ))
                                .multiply(BigInteger.TEN),
                        BigInteger
                                .valueOf(60L)
                                .multiply(BigInteger.valueOf(12L))
                                .multiply(BigInteger.valueOf(293L))
                );
        };
    }

    private static String requireReason(
            String reason,
            String fallback
    ) {
        return reason == null || reason.isBlank()
                ? fallback
                : reason;
    }

    private static boolean validCurrency(String currencyCode) {
        return currencyCode != null
                && currencyCode.matches("[A-Z]{3}");
    }

    public enum BlockingStage {
        ORDERED_FALLBACK,
        REFERENCE_CALCULATION,
        PARAGRAPH_7_CALENDAR_BASIS,
        PARAGRAPH_8_FORMULA_BASIS
    }

    /**
     * Explicit paragraph-10 partial-month calendar fact for the p7 event month.
     *
     * <p>The count is calendar days attributable to worked time, not the number
     * of distinct payroll/work rows and not {@code workedDayCount()}.</p>
     */
    public record Paragraph7CalendarBasis(
            LocalDate eventDate,
            YearMonth eventMonth,
            LocalDate periodFrom,
            LocalDate cutoffExclusive,
            int countableCalendarDays,
            String authorityCode
    ) {
        public Paragraph7CalendarBasis {
            Objects.requireNonNull(
                    eventDate,
                    "Paragraph-7 calendar basis requires event date"
            );
            Objects.requireNonNull(
                    eventMonth,
                    "Paragraph-7 calendar basis requires event month"
            );
            Objects.requireNonNull(
                    periodFrom,
                    "Paragraph-7 calendar basis requires period start"
            );
            Objects.requireNonNull(
                    cutoffExclusive,
                    "Paragraph-7 calendar basis requires exclusive cutoff"
            );
            if (!eventMonth.equals(YearMonth.from(eventDate))
                    || !periodFrom.equals(eventMonth.atDay(1))
                    || !cutoffExclusive.equals(eventDate)) {
                throw new IllegalArgumentException(
                        "Paragraph-7 calendar basis event identity is invalid"
                );
            }
            int availableCalendarDays =
                    eventDate.getDayOfMonth() - 1;
            if (countableCalendarDays <= 0
                    || countableCalendarDays > availableCalendarDays) {
                throw new IllegalArgumentException(
                        "Paragraph-7 countable calendar days must be within pre-event month range"
                );
            }
            if (authorityCode == null || authorityCode.isBlank()) {
                throw new IllegalArgumentException(
                        "Paragraph-7 calendar basis authority code is required"
                );
            }
        }

        public static Paragraph7CalendarBasis of(
                LocalDate eventDate,
                int countableCalendarDays,
                String authorityCode
        ) {
            YearMonth month = YearMonth.from(eventDate);
            return new Paragraph7CalendarBasis(
                    eventDate,
                    month,
                    month.atDay(1),
                    eventDate,
                    countableCalendarDays,
                    authorityCode
            );
        }

        public VacationAverageCalendarDenominator.ExactDays denominatorDays() {
            return VacationAverageCalendarDenominator.ExactDays.of(
                    BigInteger
                            .valueOf(293L)
                            .multiply(BigInteger.valueOf(countableCalendarDays)),
                    BigInteger
                            .valueOf(10L)
                            .multiply(BigInteger.valueOf(eventMonth.lengthOfMonth()))
            );
        }
    }

    public enum Paragraph8FormulaPolicy {
        MONTHLY_OFFICIAL_SALARY_DIV_29_3,
        HOURLY_TARIFF_AVERAGE_MONTHLY_NORM_DIV_29_3
    }

    /**
     * Explicit policy authority required before p8 can become average-daily
     * vacation money. K validates and applies it but never selects it.
     */
    public record Paragraph8FormulaBasis(
            LocalDate eventDate,
            YearMonth eventMonth,
            AverageEarningsParagraph8TariffSalaryAuthorityService.EstablishedBasis
                    establishedBasis,
            String currencyCode,
            Paragraph8FormulaPolicy policy,
            String authorityCode,
            Long annualNormMinutes
    ) {
        public Paragraph8FormulaBasis {
            Objects.requireNonNull(
                    eventDate,
                    "Paragraph-8 formula basis requires event date"
            );
            Objects.requireNonNull(
                    eventMonth,
                    "Paragraph-8 formula basis requires event month"
            );
            Objects.requireNonNull(
                    establishedBasis,
                    "Paragraph-8 formula basis requires established basis"
            );
            Objects.requireNonNull(
                    policy,
                    "Paragraph-8 formula basis requires explicit policy"
            );
            if (!eventMonth.equals(YearMonth.from(eventDate))) {
                throw new IllegalArgumentException(
                        "Paragraph-8 formula basis event identity is invalid"
                );
            }
            if (!validCurrency(currencyCode)) {
                throw new IllegalArgumentException(
                        "Paragraph-8 formula basis requires ISO-like currency code"
                );
            }
            if (authorityCode == null || authorityCode.isBlank()) {
                throw new IllegalArgumentException(
                        "Paragraph-8 formula basis authority code is required"
                );
            }

            if (establishedBasis
                    == AverageEarningsParagraph8TariffSalaryAuthorityService
                            .EstablishedBasis.MONTHLY_OFFICIAL_SALARY) {
                if (policy
                                != Paragraph8FormulaPolicy
                                        .MONTHLY_OFFICIAL_SALARY_DIV_29_3
                        || annualNormMinutes != null) {
                    throw new IllegalArgumentException(
                            "Monthly official salary requires explicit salary/29.3 policy"
                    );
                }
            } else if (establishedBasis
                    == AverageEarningsParagraph8TariffSalaryAuthorityService
                            .EstablishedBasis.HOURLY_TARIFF_RATE) {
                if (policy
                                != Paragraph8FormulaPolicy
                                        .HOURLY_TARIFF_AVERAGE_MONTHLY_NORM_DIV_29_3
                        || annualNormMinutes == null
                        || annualNormMinutes <= 0L) {
                    throw new IllegalArgumentException(
                            "Hourly tariff requires explicit annual-norm formula basis"
                    );
                }
            } else {
                throw new IllegalArgumentException(
                        "Paragraph-8 formula basis has unsupported established basis"
                );
            }
        }

        public static Paragraph8FormulaBasis monthlySalary(
                LocalDate eventDate,
                String currencyCode,
                String authorityCode
        ) {
            return new Paragraph8FormulaBasis(
                    eventDate,
                    YearMonth.from(eventDate),
                    AverageEarningsParagraph8TariffSalaryAuthorityService
                            .EstablishedBasis.MONTHLY_OFFICIAL_SALARY,
                    currencyCode,
                    Paragraph8FormulaPolicy.MONTHLY_OFFICIAL_SALARY_DIV_29_3,
                    authorityCode,
                    null
            );
        }

        public static Paragraph8FormulaBasis hourlyTariff(
                LocalDate eventDate,
                String currencyCode,
                String authorityCode,
                long annualNormMinutes
        ) {
            return new Paragraph8FormulaBasis(
                    eventDate,
                    YearMonth.from(eventDate),
                    AverageEarningsParagraph8TariffSalaryAuthorityService
                            .EstablishedBasis.HOURLY_TARIFF_RATE,
                    currencyCode,
                    Paragraph8FormulaPolicy
                            .HOURLY_TARIFF_AVERAGE_MONTHLY_NORM_DIV_29_3,
                    authorityCode,
                    annualNormMinutes
            );
        }
    }

    public record Resolution(
            LocalDate eventDate,
            YearMonth eventMonth,
            boolean ready,
            BlockingStage blockingStage,
            String blockingReason,
            String blockingMessage,
            AverageEarningsOrderedFallbackResolver.Resolution orderedFallback,
            String currencyCode,
            VacationAverageDailyEarningsFormula.ExactMoneyPerDay averageDaily,
            VacationAveragePrimaryCalculationService.Resolution referenceCalculation,
            Paragraph7CalendarBasis paragraph7CalendarBasis,
            Paragraph8FormulaBasis paragraph8FormulaBasis
    ) {
        public Resolution {
            Objects.requireNonNull(
                    eventDate,
                    "Unified vacation daily result requires event date"
            );
            Objects.requireNonNull(
                    eventMonth,
                    "Unified vacation daily result requires event month"
            );
            Objects.requireNonNull(
                    orderedFallback,
                    "Unified vacation daily result requires ordered fallback provenance"
            );

            boolean orderedFallbackIdentityMatches =
                    eventMonth.equals(YearMonth.from(eventDate))
                            && orderedFallback.eventDate().equals(eventDate)
                            && orderedFallback.eventMonth().equals(eventMonth);
            boolean exactOrderedFallbackEventMonthBlock =
                    !ready
                            && blockingStage == BlockingStage.ORDERED_FALLBACK
                            && REFERENCE_IDENTITY_MISMATCH.equals(blockingReason)
                            && eventMonth.equals(YearMonth.from(eventDate))
                            && orderedFallback.eventDate().equals(eventDate)
                            && !orderedFallback.eventMonth().equals(eventMonth);
            if (!orderedFallbackIdentityMatches
                    && !exactOrderedFallbackEventMonthBlock) {
                throw new IllegalArgumentException(
                        "Unified vacation daily result event identity is invalid"
                );
            }

            if (ready == (blockingReason != null)) {
                throw new IllegalArgumentException(
                        "Unified vacation daily result state is invalid"
                );
            }

            if (ready) {
                if (!orderedFallback.ready()
                        || orderedFallback.selection() == null
                        || blockingStage != null
                        || blockingMessage != null
                        || averageDaily == null
                        || (averageDaily.numeratorMinor().signum() > 0
                                && !validCurrency(currencyCode))) {
                    throw new IllegalArgumentException(
                            "Ready unified vacation daily result is incomplete"
                    );
                }

                switch (orderedFallback.selection()) {
                case PRIMARY_REFERENCE_PERIOD,
                        PARAGRAPH_6_PRECEDING_REFERENCE_PERIOD -> {
                    if (referenceCalculation == null
                            || !referenceCalculation.ready()
                            || paragraph7CalendarBasis != null
                            || paragraph8FormulaBasis != null) {
                        throw new IllegalArgumentException(
                                "Ready reference unified result has invalid provenance"
                        );
                    }
                }
                case PARAGRAPH_7_PRE_EVENT_ACCRUED_WAGE -> {
                    if (referenceCalculation != null
                            || paragraph7CalendarBasis == null
                            || paragraph8FormulaBasis != null) {
                        throw new IllegalArgumentException(
                                "Ready paragraph-7 unified result has invalid provenance"
                        );
                    }
                }
                case PARAGRAPH_8_ESTABLISHED_TARIFF_OR_SALARY -> {
                    if (referenceCalculation != null
                            || paragraph7CalendarBasis != null
                            || paragraph8FormulaBasis == null) {
                        throw new IllegalArgumentException(
                                "Ready paragraph-8 unified result has invalid provenance"
                        );
                    }
                }
                }
            } else {
                if (blockingStage == null
                        || blockingMessage == null
                        || blockingMessage.isBlank()
                        || currencyCode != null
                        || averageDaily != null
                        || referenceCalculation != null
                        || paragraph7CalendarBasis != null
                        || paragraph8FormulaBasis != null) {
                    throw new IllegalArgumentException(
                            "Blocked unified vacation daily result cannot expose partial money"
                    );
                }
            }
        }

        static Resolution readyReference(
                LocalDate eventDate,
                AverageEarningsOrderedFallbackResolver.Resolution orderedFallback,
                String currencyCode,
                VacationAverageDailyEarningsFormula.ExactMoneyPerDay averageDaily,
                VacationAveragePrimaryCalculationService.Resolution referenceCalculation
        ) {
            return new Resolution(
                    eventDate,
                    YearMonth.from(eventDate),
                    true,
                    null,
                    null,
                    null,
                    orderedFallback,
                    currencyCode,
                    averageDaily,
                    referenceCalculation,
                    null,
                    null
            );
        }

        static Resolution readyParagraph7(
                LocalDate eventDate,
                AverageEarningsOrderedFallbackResolver.Resolution orderedFallback,
                String currencyCode,
                VacationAverageDailyEarningsFormula.ExactMoneyPerDay averageDaily,
                Paragraph7CalendarBasis basis
        ) {
            return new Resolution(
                    eventDate,
                    YearMonth.from(eventDate),
                    true,
                    null,
                    null,
                    null,
                    orderedFallback,
                    currencyCode,
                    averageDaily,
                    null,
                    basis,
                    null
            );
        }

        static Resolution readyParagraph8(
                LocalDate eventDate,
                AverageEarningsOrderedFallbackResolver.Resolution orderedFallback,
                String currencyCode,
                VacationAverageDailyEarningsFormula.ExactMoneyPerDay averageDaily,
                Paragraph8FormulaBasis basis
        ) {
            return new Resolution(
                    eventDate,
                    YearMonth.from(eventDate),
                    true,
                    null,
                    null,
                    null,
                    orderedFallback,
                    currencyCode,
                    averageDaily,
                    null,
                    null,
                    basis
            );
        }

        static Resolution blocked(
                LocalDate eventDate,
                AverageEarningsOrderedFallbackResolver.Resolution orderedFallback,
                BlockingStage stage,
                String reason,
                String message
        ) {
            Objects.requireNonNull(stage, "Unified vacation daily blocker stage is required");
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException(
                        "Unified vacation daily blocker reason is required"
                );
            }
            if (message == null || message.isBlank()) {
                throw new IllegalArgumentException(
                        "Unified vacation daily blocker message is required"
                );
            }
            return new Resolution(
                    eventDate,
                    YearMonth.from(eventDate),
                    false,
                    stage,
                    reason,
                    message,
                    orderedFallback,
                    null,
                    null,
                    null,
                    null,
                    null
            );
        }
    }
}
