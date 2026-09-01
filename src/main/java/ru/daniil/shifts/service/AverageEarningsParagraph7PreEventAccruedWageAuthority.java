package ru.daniil.shifts.service;

import ru.daniil.shifts.service.AverageEarningsParagraph7PreEventSemanticWageFactService.SemanticWageFact;
import ru.daniil.shifts.service.AverageEarningsParagraph7PreEventSemanticWageFactService.SourceAuthority;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Objects;

/**
 * Final paragraph-7 authority for money actually accrued in the exact pre-event
 * event-month window {@code [eventMonthStart,eventDate)}.
 *
 * <p>This layer does not rediscover or reprice any earning. It only assembles
 * already-proven authorities:</p>
 * <ul>
 *     <li>J3B2 BASE_PAY;</li>
 *     <li>J3B4 ordinary NIGHT/HOLIDAY premium money;</li>
 *     <li>J3B5 HARMFUL money;</li>
 *     <li>J3B3 explicit COMBINATION and REGIONAL accrued-money facts;</li>
 *     <li>J3B6B3B legally processed paragraph-15 BONUS money.</li>
 * </ul>
 *
 * <p>Raw BONUS source amounts from J3B3 are provenance only and are never
 * added directly, because paragraph 15 owns their inclusion and amount
 * treatment. This prevents double counting and preserves FACT -> POLICY ->
 * FORMULA -> MONEY.</p>
 *
 * <p>This class does not select paragraph 8 and does not decide fallback.
 * Worked-time presence and accrued-wage presence remain separate facts for the
 * later ordered resolver.</p>
 */
public final class AverageEarningsParagraph7PreEventAccruedWageAuthority {
    public static final String AUTHORITY_WINDOW_MISMATCH =
            "PP_540_P7_PRE_EVENT_ACCRUED_WAGE_WINDOW_MISMATCH";
    public static final String PROVENANCE_MISMATCH =
            "PP_540_P7_PRE_EVENT_ACCRUED_WAGE_PROVENANCE_MISMATCH";
    public static final String UPSTREAM_STATE_CONTRADICTION =
            "PP_540_P7_PRE_EVENT_ACCRUED_WAGE_UPSTREAM_STATE_CONTRADICTION";
    public static final String CURRENCY_MISMATCH =
            "PP_540_P7_PRE_EVENT_ACCRUED_WAGE_CURRENCY_MISMATCH";
    public static final String TOTAL_OVERFLOW =
            "PP_540_P7_PRE_EVENT_ACCRUED_WAGE_TOTAL_OVERFLOW";

    private AverageEarningsParagraph7PreEventAccruedWageAuthority() {
    }

    public enum BlockingSource {
        COMPOSITION_AUTHORITY,
        HARMFUL_AUTHORITY,
        BONUS_AUTHORITY,
        AGGREGATE_AUTHORITY
    }

    public static Resolution resolve(
            AverageEarningsParagraph7PreEventHarmfulCompensationService.Resolution harmful,
            AverageEarningsParagraph7PreEventBonusP15Formula.Calculation bonus
    ) {
        Objects.requireNonNull(
                harmful,
                "Paragraph-7 accrued-wage authority requires harmful-compensation authority"
        );
        Objects.requireNonNull(
                bonus,
                "Paragraph-7 accrued-wage authority requires P15 BONUS calculation"
        );

        LocalDate eventDate = Objects.requireNonNull(
                harmful.eventDate(),
                "Paragraph-7 accrued-wage authority requires event date"
        );
        AverageEarningsLegalPolicy.requireRegime(eventDate);
        LocalDate periodFrom = YearMonth.from(eventDate).atDay(1);
        LocalDate cutoffExclusive = eventDate;

        if (!harmful.ready()) {
            return Resolution.blocked(
                    eventDate,
                    periodFrom,
                    harmful,
                    bonus,
                    BlockingSource.HARMFUL_AUTHORITY,
                    requireReason(harmful.blockingReason()),
                    harmful.blockingMessage() == null
                            ? "Paragraph-7 harmful-compensation authority is blocked"
                            : harmful.blockingMessage()
            );
        }

        AverageEarningsParagraph7PreEventOrdinaryPremiumService.Resolution ordinary =
                Objects.requireNonNull(
                        harmful.ordinaryPremium(),
                        "Paragraph-7 accrued-wage authority lost ordinary-premium provenance"
                );
        AverageEarningsParagraph7PreEventSemanticWageFactService.Resolution semantic =
                Objects.requireNonNull(
                        ordinary.semanticFacts(),
                        "Paragraph-7 accrued-wage authority lost semantic-wage provenance"
                );
        AverageEarningsParagraph7PreEventBasePayFormula.Calculation basePay =
                Objects.requireNonNull(
                        semantic.basePay(),
                        "Paragraph-7 accrued-wage authority lost BASE_PAY calculation"
                );
        AverageEarningsParagraph7PreEventBasePayAuthorityService.Resolution baseAuthority =
                Objects.requireNonNull(
                        basePay.authority(),
                        "Paragraph-7 accrued-wage authority lost BASE_PAY authority"
                );

        if (!ordinary.ready() || !semantic.ready() || !baseAuthority.ready()) {
            return Resolution.blocked(
                    eventDate,
                    periodFrom,
                    harmful,
                    bonus,
                    BlockingSource.AGGREGATE_AUTHORITY,
                    UPSTREAM_STATE_CONTRADICTION,
                    "Ready harmful authority cannot contain blocked paragraph-7 provenance"
            );
        }

        if (!sameWindow(eventDate, periodFrom, harmful.eventDate(), harmful.periodFrom(), harmful.cutoffExclusive())
                || !sameWindow(eventDate, periodFrom, ordinary.eventDate(), ordinary.periodFrom(), ordinary.cutoffExclusive())
                || !sameWindow(eventDate, periodFrom, semantic.eventDate(), semantic.periodFrom(), semantic.cutoffExclusive())
                || !sameWindow(eventDate, periodFrom, baseAuthority.eventDate(), baseAuthority.periodFrom(), baseAuthority.cutoffExclusive())
                || !sameWindow(eventDate, periodFrom, bonus.eventDate(), bonus.periodFrom(), bonus.cutoffExclusive())) {
            return Resolution.blocked(
                    eventDate,
                    periodFrom,
                    harmful,
                    bonus,
                    BlockingSource.AGGREGATE_AUTHORITY,
                    AUTHORITY_WINDOW_MISMATCH,
                    "Paragraph-7 authorities do not share the exact pre-event event-month window"
            );
        }

        if (!bonus.ready()) {
            return Resolution.blocked(
                    eventDate,
                    periodFrom,
                    harmful,
                    bonus,
                    BlockingSource.BONUS_AUTHORITY,
                    requireReason(bonus.blockingReason()),
                    "Paragraph-7 P15 BONUS authority is blocked"
            );
        }

        AverageEarningsParagraph7PreEventSemanticWageFactService.Resolution bonusSemantic =
                bonusSemanticProvenance(bonus);
        if (bonusSemantic == null || !semantic.equals(bonusSemantic)) {
            return Resolution.blocked(
                    eventDate,
                    periodFrom,
                    harmful,
                    bonus,
                    BlockingSource.AGGREGATE_AUTHORITY,
                    PROVENANCE_MISMATCH,
                    "P15 BONUS money does not originate from the same paragraph-7 semantic FACT authority"
            );
        }

        String currency = null;
        try {
            currency = mergeCurrency(currency, basePay.currencyCode(), "BASE_PAY");
            currency = mergeCurrency(currency, ordinary.currencyCode(), "ORDINARY_PREMIUM");
            currency = mergeCurrency(currency, harmful.currencyCode(), "HARMFUL_CONDITIONS");

            long combinationMinor = 0L;
            long regionalMinor = 0L;
            for (SemanticWageFact fact : semantic.observedFacts()) {
                Objects.requireNonNull(
                        fact,
                        "Paragraph-7 accrued-wage semantic FACT cannot be null"
                );
                switch (fact.sourceAuthority()) {
                    case COMBINATION_EPISODE -> {
                        currency = mergeCurrency(currency, fact.currencyCode(), "COMBINATION");
                        combinationMinor = Math.addExact(combinationMinor, fact.amountMinor());
                    }
                    case REGIONAL_SOURCE -> {
                        currency = mergeCurrency(currency, fact.currencyCode(), "REGIONAL_COEFFICIENT");
                        regionalMinor = Math.addExact(regionalMinor, fact.amountMinor());
                    }
                    case BONUS_SOURCE -> {
                        // Provenance only. Paragraph 15 owns inclusion and amount treatment.
                    }
                }
            }

            currency = mergeCurrency(currency, bonus.currencyCode(), "BONUS_P15");

            long baseMinor = basePay.basePayAmountMinor();
            long ordinaryMinor = ordinary.ordinaryPremiumAmountMinor();
            long harmfulMinor = harmful.harmfulAmountMinor();
            long bonusMinor = bonus.includedPremiumAmountMinor();

            long total = 0L;
            total = Math.addExact(total, baseMinor);
            total = Math.addExact(total, ordinaryMinor);
            total = Math.addExact(total, harmfulMinor);
            total = Math.addExact(total, combinationMinor);
            total = Math.addExact(total, regionalMinor);
            total = Math.addExact(total, bonusMinor);

            if (total > 0L && currency == null) {
                return Resolution.blocked(
                        eventDate,
                        periodFrom,
                        harmful,
                        bonus,
                        BlockingSource.AGGREGATE_AUTHORITY,
                        CURRENCY_MISMATCH,
                        "Positive paragraph-7 accrued wage has no canonical currency"
                );
            }

            return Resolution.ready(
                    eventDate,
                    periodFrom,
                    harmful,
                    bonus,
                    currency,
                    baseMinor,
                    ordinaryMinor,
                    harmfulMinor,
                    combinationMinor,
                    regionalMinor,
                    bonusMinor,
                    total
            );
        } catch (CurrencyContradiction ex) {
            return Resolution.blocked(
                    eventDate,
                    periodFrom,
                    harmful,
                    bonus,
                    BlockingSource.AGGREGATE_AUTHORITY,
                    CURRENCY_MISMATCH,
                    ex.getMessage()
            );
        } catch (ArithmeticException ex) {
            return Resolution.blocked(
                    eventDate,
                    periodFrom,
                    harmful,
                    bonus,
                    BlockingSource.AGGREGATE_AUTHORITY,
                    TOTAL_OVERFLOW,
                    "Paragraph-7 accrued-wage sum exceeds minor-unit range"
            );
        }
    }

    private static AverageEarningsParagraph7PreEventSemanticWageFactService.Resolution
    bonusSemanticProvenance(
            AverageEarningsParagraph7PreEventBonusP15Formula.Calculation bonus
    ) {
        try {
            return bonus.workTimeAuthority()
                    .policy()
                    .accrualAuthority()
                    .preEventFacts()
                    .semanticFacts();
        } catch (NullPointerException ex) {
            return null;
        }
    }

    private static boolean sameWindow(
            LocalDate expectedEventDate,
            LocalDate expectedFrom,
            LocalDate eventDate,
            LocalDate periodFrom,
            LocalDate cutoffExclusive
    ) {
        return expectedEventDate.equals(eventDate)
                && expectedFrom.equals(periodFrom)
                && expectedEventDate.equals(cutoffExclusive);
    }

    private static String mergeCurrency(
            String current,
            String candidate,
            String source
    ) {
        if (candidate == null) {
            return current;
        }
        if (!candidate.matches("[A-Z]{3}")) {
            throw new CurrencyContradiction(
                    "Invalid paragraph-7 currency from " + source
            );
        }
        if (current != null && !current.equals(candidate)) {
            throw new CurrencyContradiction(
                    "Paragraph-7 currency mismatch at " + source
            );
        }
        return candidate;
    }

    private static String requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return UPSTREAM_STATE_CONTRADICTION;
        }
        return reason;
    }

    private static final class CurrencyContradiction extends RuntimeException {
        private CurrencyContradiction(String message) {
            super(message);
        }
    }

    public record Resolution(
            LocalDate eventDate,
            LocalDate periodFrom,
            LocalDate cutoffExclusive,
            boolean ready,
            String blockingReason,
            String blockingMessage,
            BlockingSource blockingSource,
            AverageEarningsParagraph7PreEventHarmfulCompensationService.Resolution harmfulAuthority,
            AverageEarningsParagraph7PreEventBonusP15Formula.Calculation bonusAuthority,
            String currencyCode,
            long basePayAmountMinor,
            long ordinaryPremiumAmountMinor,
            long harmfulAmountMinor,
            long combinationAmountMinor,
            long regionalCoefficientAmountMinor,
            long bonusP15AmountMinor,
            long totalAccruedWageMinor
    ) {
        public Resolution {
            Objects.requireNonNull(eventDate, "Paragraph-7 accrued-wage event date is required");
            Objects.requireNonNull(periodFrom, "Paragraph-7 accrued-wage period start is required");
            Objects.requireNonNull(cutoffExclusive, "Paragraph-7 accrued-wage cutoff is required");
            if (ready) {
                Objects.requireNonNull(
                        harmfulAuthority,
                        "Ready paragraph-7 accrued-wage harmful provenance is required"
                );
                Objects.requireNonNull(
                        bonusAuthority,
                        "Ready paragraph-7 accrued-wage BONUS provenance is required"
                );
            } else if (blockingSource == BlockingSource.COMPOSITION_AUTHORITY) {
                if (harmfulAuthority != null || bonusAuthority != null) {
                    throw new IllegalArgumentException(
                            "Composition-blocked paragraph-7 authority cannot expose later provenance"
                    );
                }
            } else {
                Objects.requireNonNull(
                        harmfulAuthority,
                        "Blocked paragraph-7 accrued-wage harmful provenance is required"
                );
                if (blockingSource == BlockingSource.AGGREGATE_AUTHORITY) {
                    Objects.requireNonNull(
                            bonusAuthority,
                            "Aggregate-blocked paragraph-7 BONUS provenance is required"
                    );
                }
            }
            if (!periodFrom.equals(YearMonth.from(eventDate).atDay(1))
                    || !cutoffExclusive.equals(eventDate)
                    || basePayAmountMinor < 0L
                    || ordinaryPremiumAmountMinor < 0L
                    || harmfulAmountMinor < 0L
                    || combinationAmountMinor < 0L
                    || regionalCoefficientAmountMinor < 0L
                    || bonusP15AmountMinor < 0L
                    || totalAccruedWageMinor < 0L) {
                throw new IllegalArgumentException(
                        "Paragraph-7 accrued-wage authority window or money is invalid"
                );
            }

            if (ready) {
                if (blockingReason != null
                        || blockingMessage != null
                        || blockingSource != null) {
                    throw new IllegalArgumentException(
                            "Ready paragraph-7 accrued-wage authority cannot contain blocker"
                    );
                }
                if (currencyCode != null && !currencyCode.matches("[A-Z]{3}")) {
                    throw new IllegalArgumentException(
                            "Ready paragraph-7 accrued-wage authority has invalid currency"
                    );
                }
                long recomputed;
                try {
                    recomputed = Math.addExact(basePayAmountMinor, ordinaryPremiumAmountMinor);
                    recomputed = Math.addExact(recomputed, harmfulAmountMinor);
                    recomputed = Math.addExact(recomputed, combinationAmountMinor);
                    recomputed = Math.addExact(recomputed, regionalCoefficientAmountMinor);
                    recomputed = Math.addExact(recomputed, bonusP15AmountMinor);
                } catch (ArithmeticException ex) {
                    throw new IllegalArgumentException(
                            "Ready paragraph-7 accrued-wage authority overflows",
                            ex
                    );
                }
                if (recomputed != totalAccruedWageMinor
                        || (totalAccruedWageMinor > 0L && currencyCode == null)) {
                    throw new IllegalArgumentException(
                            "Paragraph-7 accrued-wage components do not preserve total/currency"
                    );
                }
            } else {
                if (blockingReason == null
                        || blockingReason.isBlank()
                        || blockingMessage == null
                        || blockingMessage.isBlank()
                        || blockingSource == null
                        || currencyCode != null
                        || basePayAmountMinor != 0L
                        || ordinaryPremiumAmountMinor != 0L
                        || harmfulAmountMinor != 0L
                        || combinationAmountMinor != 0L
                        || regionalCoefficientAmountMinor != 0L
                        || bonusP15AmountMinor != 0L
                        || totalAccruedWageMinor != 0L) {
                    throw new IllegalArgumentException(
                            "Blocked paragraph-7 accrued-wage authority cannot expose partial money"
                    );
                }
            }
        }

        static Resolution blockedComposition(
                LocalDate eventDate,
                String reason,
                String message
        ) {
            Objects.requireNonNull(eventDate, "Paragraph-7 composition blocker requires event date");
            return new Resolution(
                    eventDate,
                    YearMonth.from(eventDate).atDay(1),
                    eventDate,
                    false,
                    requireReason(reason),
                    message == null || message.isBlank()
                            ? "Paragraph-7 composed authority is blocked"
                            : message,
                    BlockingSource.COMPOSITION_AUTHORITY,
                    null,
                    null,
                    null,
                    0L,
                    0L,
                    0L,
                    0L,
                    0L,
                    0L,
                    0L
            );
        }

        static Resolution blockedHarmful(
                AverageEarningsParagraph7PreEventHarmfulCompensationService.Resolution harmful
        ) {
            Objects.requireNonNull(harmful, "Paragraph-7 harmful blocker requires authority");
            LocalDate eventDate = Objects.requireNonNull(
                    harmful.eventDate(),
                    "Paragraph-7 harmful blocker requires event date"
            );
            return new Resolution(
                    eventDate,
                    YearMonth.from(eventDate).atDay(1),
                    eventDate,
                    false,
                    requireReason(harmful.blockingReason()),
                    harmful.blockingMessage() == null || harmful.blockingMessage().isBlank()
                            ? "Paragraph-7 harmful-compensation authority is blocked"
                            : harmful.blockingMessage(),
                    BlockingSource.HARMFUL_AUTHORITY,
                    harmful,
                    null,
                    null,
                    0L,
                    0L,
                    0L,
                    0L,
                    0L,
                    0L,
                    0L
            );
        }

        static Resolution blockedBonus(
                LocalDate eventDate,
                AverageEarningsParagraph7PreEventHarmfulCompensationService.Resolution harmful,
                String reason,
                String message
        ) {
            Objects.requireNonNull(eventDate, "Paragraph-7 bonus blocker requires event date");
            Objects.requireNonNull(harmful, "Paragraph-7 bonus blocker requires harmful provenance");
            return new Resolution(
                    eventDate,
                    YearMonth.from(eventDate).atDay(1),
                    eventDate,
                    false,
                    requireReason(reason),
                    message == null || message.isBlank()
                            ? "Paragraph-7 P15 BONUS authority is blocked"
                            : message,
                    BlockingSource.BONUS_AUTHORITY,
                    harmful,
                    null,
                    null,
                    0L,
                    0L,
                    0L,
                    0L,
                    0L,
                    0L,
                    0L
            );
        }

        static Resolution ready(
                LocalDate eventDate,
                LocalDate periodFrom,
                AverageEarningsParagraph7PreEventHarmfulCompensationService.Resolution harmful,
                AverageEarningsParagraph7PreEventBonusP15Formula.Calculation bonus,
                String currency,
                long basePayMinor,
                long ordinaryPremiumMinor,
                long harmfulMinor,
                long combinationMinor,
                long regionalMinor,
                long bonusMinor,
                long totalMinor
        ) {
            return new Resolution(
                    eventDate,
                    periodFrom,
                    eventDate,
                    true,
                    null,
                    null,
                    null,
                    harmful,
                    bonus,
                    currency,
                    basePayMinor,
                    ordinaryPremiumMinor,
                    harmfulMinor,
                    combinationMinor,
                    regionalMinor,
                    bonusMinor,
                    totalMinor
            );
        }

        static Resolution blocked(
                LocalDate eventDate,
                LocalDate periodFrom,
                AverageEarningsParagraph7PreEventHarmfulCompensationService.Resolution harmful,
                AverageEarningsParagraph7PreEventBonusP15Formula.Calculation bonus,
                BlockingSource source,
                String reason,
                String message
        ) {
            return new Resolution(
                    eventDate,
                    periodFrom,
                    eventDate,
                    false,
                    reason,
                    message,
                    source,
                    harmful,
                    bonus,
                    null,
                    0L,
                    0L,
                    0L,
                    0L,
                    0L,
                    0L,
                    0L
            );
        }

        public boolean workedTimePresent() {
            return requireHarmfulProvenance()
                    .ordinaryPremium()
                    .semanticFacts()
                    .basePay()
                    .authority()
                    .workedTimePresent();
        }

        public int workedDayCount() {
            return requireHarmfulProvenance()
                    .ordinaryPremium()
                    .semanticFacts()
                    .basePay()
                    .authority()
                    .workFacts()
                    .workedDayCount();
        }

        public long workedMinutes() {
            return requireHarmfulProvenance()
                    .ordinaryPremium()
                    .semanticFacts()
                    .basePay()
                    .authority()
                    .workFacts()
                    .workedMinutes();
        }

        private AverageEarningsParagraph7PreEventHarmfulCompensationService.Resolution
                requireHarmfulProvenance() {
            if (harmfulAuthority == null) {
                throw new IllegalStateException(
                        "Blocked paragraph-7 composition has no worked-time provenance"
                );
            }
            return harmfulAuthority;
        }

        public boolean accruedWagePresent() {
            return ready && totalAccruedWageMinor > 0L;
        }
    }
}
