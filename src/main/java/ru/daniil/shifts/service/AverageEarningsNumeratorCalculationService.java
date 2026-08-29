package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.model.AppUser;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Final money assembly for the primary average-earnings numerator.
 *
 * <p>Order is explicit: immutable numerator FACTS -> paragraph-15 pipeline ->
 * cross-authority premium reconciliation -> paragraph-5 ordinary-money POLICY
 * -> final numerator MONEY. Raw premium-special money from numerator facts is
 * never added directly; it is replaced by the legally calculated paragraph-15
 * amount.</p>
 *
 * <p>This service still stops before average daily/hourly earnings, denominator
 * formula, fallback reference periods and vacation-pay money.</p>
 */
@Service
public class AverageEarningsNumeratorCalculationService {

    public static final String AUTHORITY_WINDOW_MISMATCH =
            "AVERAGE_EARNINGS_NUMERATOR_AUTHORITY_WINDOW_MISMATCH";
    public static final String NO_PAYROLL_EMPLOYMENT_CONTRADICTION =
            "AVERAGE_EARNINGS_NO_PAYROLL_PROOF_CONTRADICTS_EMPLOYMENT";
    public static final String RAW_PREMIUM_RECONCILIATION_MISMATCH =
            "PP_540_P15_NUMERATOR_RAW_PREMIUM_RECONCILIATION_MISMATCH";
    public static final String CURRENCY_MISMATCH =
            "AVERAGE_EARNINGS_NUMERATOR_CURRENCY_MISMATCH";
    public static final String CURRENCY_MISSING =
            "AVERAGE_EARNINGS_NUMERATOR_CURRENCY_MISSING";

    private final AverageEarningsNumeratorFactsService numeratorFacts;
    private final AverageEarningsBonusP15CalculationPipelineService p15;

    public AverageEarningsNumeratorCalculationService(
            AverageEarningsNumeratorFactsService numeratorFacts,
            AverageEarningsBonusP15CalculationPipelineService p15
    ) {
        this.numeratorFacts = Objects.requireNonNull(
                numeratorFacts,
                "Average earnings numerator facts are required"
        );
        this.p15 = Objects.requireNonNull(
                p15,
                "Paragraph-15 calculation pipeline is required"
        );
    }

    @Transactional(readOnly = true)
    public Resolution calculate(
            AppUser user,
            LocalDate eventDate,
            YearMonth discoveryThroughMonth,
            List<YearMonth> provenNoPayrollMonths
    ) {
        Objects.requireNonNull(user, "Average earnings numerator requires user");
        Objects.requireNonNull(eventDate, "Average earnings numerator requires event date");
        Objects.requireNonNull(
                discoveryThroughMonth,
                "Average earnings numerator requires discovery-through month"
        );
        provenNoPayrollMonths = List.copyOf(Objects.requireNonNull(
                provenNoPayrollMonths,
                "Average earnings numerator requires explicit no-Payroll proofs"
        ));

        AverageEarningsLegalPolicy.requireRegime(eventDate);

        YearMonth eventMonth = YearMonth.from(eventDate);
        YearMonth referenceFrom = eventMonth.minusMonths(12);
        YearMonth referenceTo = eventMonth.minusMonths(1);

        if (discoveryThroughMonth.isBefore(referenceTo)) {
            throw new IllegalArgumentException(
                    "Average earnings numerator discovery cannot end before reference period"
            );
        }

        AverageEarningsNumeratorFactsService.Resolution facts =
                Objects.requireNonNull(
                        numeratorFacts.resolve(user, eventDate),
                        "Average earnings numerator facts authority returned null"
                );

        if (!facts.ready()) {
            return Resolution.blocked(
                    eventDate,
                    eventMonth,
                    referenceFrom,
                    referenceTo,
                    discoveryThroughMonth,
                    BlockingStage.NUMERATOR_FACTS,
                    facts.blockingReason(),
                    facts.blockingPeriod()
            );
        }

        if (!facts.eventDate().equals(eventDate)
                || !facts.eventMonth().equals(eventMonth)
                || !facts.referenceFrom().equals(referenceFrom)
                || !facts.referenceTo().equals(referenceTo)) {
            return Resolution.blocked(
                    eventDate,
                    eventMonth,
                    referenceFrom,
                    referenceTo,
                    discoveryThroughMonth,
                    BlockingStage.NUMERATOR_FACTS,
                    AUTHORITY_WINDOW_MISMATCH,
                    null
            );
        }

        List<YearMonth> mergedNoPayroll = mergeNoPayrollAuthority(
                facts,
                provenNoPayrollMonths,
                referenceFrom,
                referenceTo,
                discoveryThroughMonth
        );

        if (mergedNoPayroll == null) {
            YearMonth contradiction = firstEmploymentContradiction(
                    facts,
                    provenNoPayrollMonths,
                    referenceFrom,
                    referenceTo
            );
            return Resolution.blocked(
                    eventDate,
                    eventMonth,
                    referenceFrom,
                    referenceTo,
                    discoveryThroughMonth,
                    BlockingStage.NO_PAYROLL_AUTHORITY,
                    NO_PAYROLL_EMPLOYMENT_CONTRADICTION,
                    contradiction
            );
        }

        AverageEarningsBonusP15CalculationPipelineService.Resolution premium =
                Objects.requireNonNull(
                        p15.calculate(
                                user,
                                eventDate,
                                discoveryThroughMonth,
                                mergedNoPayroll
                        ),
                        "Paragraph-15 pipeline returned null"
                );

        if (!premium.ready()) {
            return Resolution.blocked(
                    eventDate,
                    eventMonth,
                    referenceFrom,
                    referenceTo,
                    discoveryThroughMonth,
                    BlockingStage.P15_PIPELINE,
                    premium.blockingReason(),
                    premium.blockingPeriod()
            );
        }

        if (!premium.eventDate().equals(eventDate)
                || !premium.eventMonth().equals(eventMonth)
                || !premium.referenceFrom().equals(referenceFrom)
                || !premium.referenceTo().equals(referenceTo)
                || !premium.discoveryThroughMonth().equals(discoveryThroughMonth)) {
            return Resolution.blocked(
                    eventDate,
                    eventMonth,
                    referenceFrom,
                    referenceTo,
                    discoveryThroughMonth,
                    BlockingStage.P15_PIPELINE,
                    AUTHORITY_WINDOW_MISMATCH,
                    null
            );
        }

        YearMonth premiumMismatch = reconcileReferencePremiumFacts(
                facts,
                premium,
                referenceFrom,
                referenceTo
        );

        if (premiumMismatch != null) {
            return Resolution.blocked(
                    eventDate,
                    eventMonth,
                    referenceFrom,
                    referenceTo,
                    discoveryThroughMonth,
                    BlockingStage.P15_RECONCILIATION,
                    RAW_PREMIUM_RECONCILIATION_MISMATCH,
                    premiumMismatch
            );
        }

        AverageEarningsParagraph5MoneyPolicy.Resolution paragraph5 =
                AverageEarningsParagraph5MoneyPolicy.resolve(
                        eventDate,
                        facts.months(),
                        premium.referenceCompleteness().paragraph5Exclusions()
                );

        if (!paragraph5.ready()) {
            return Resolution.blocked(
                    eventDate,
                    eventMonth,
                    referenceFrom,
                    referenceTo,
                    discoveryThroughMonth,
                    BlockingStage.PARAGRAPH_5_MONEY,
                    paragraph5.blockingReason(),
                    paragraph5.blockingPeriod()
            );
        }

        String currency = reconcileCurrency(
                facts.currencyCode(),
                premium.currencyCode()
        );

        if (currency == null
                && facts.currencyCode() != null
                && premium.currencyCode() != null) {
            return Resolution.blocked(
                    eventDate,
                    eventMonth,
                    referenceFrom,
                    referenceTo,
                    discoveryThroughMonth,
                    BlockingStage.CURRENCY,
                    CURRENCY_MISMATCH,
                    null
            );
        }

        long premiumIncluded = premium.calculation().includedPremiumAmountMinor();
        long total = Math.addExact(
                paragraph5.includedOrdinaryAmountMinor(),
                premiumIncluded
        );

        if (total > 0L && currency == null) {
            return Resolution.blocked(
                    eventDate,
                    eventMonth,
                    referenceFrom,
                    referenceTo,
                    discoveryThroughMonth,
                    BlockingStage.CURRENCY,
                    CURRENCY_MISSING,
                    null
            );
        }

        return Resolution.ready(
                eventDate,
                eventMonth,
                referenceFrom,
                referenceTo,
                discoveryThroughMonth,
                currency,
                facts,
                premium,
                paragraph5,
                paragraph5.includedOrdinaryAmountMinor(),
                paragraph5.excludedParagraph5OrdinaryAmountMinor(),
                premiumIncluded,
                facts.premiumSpecialAmountMinor(),
                facts.excludedAmountMinor(),
                total
        );
    }

    private List<YearMonth> mergeNoPayrollAuthority(
            AverageEarningsNumeratorFactsService.Resolution facts,
            List<YearMonth> explicit,
            YearMonth referenceFrom,
            YearMonth referenceTo,
            YearMonth discoveryThroughMonth
    ) {
        if (firstEmploymentContradiction(
                facts,
                explicit,
                referenceFrom,
                referenceTo
        ) != null) {
            return null;
        }

        Set<YearMonth> merged = new LinkedHashSet<>();

        for (YearMonth month : explicit) {
            Objects.requireNonNull(month, "No-Payroll proof month is required");
            if (month.isBefore(referenceFrom) || month.isAfter(discoveryThroughMonth)) {
                throw new IllegalArgumentException(
                        "No-Payroll proof month is outside discovery window: " + month
                );
            }
            merged.add(month);
        }

        for (AverageEarningsNumeratorFactsService.MonthFact month : facts.months()) {
            if (!month.employed()) {
                merged.add(month.period());
            }
        }

        return merged.stream().sorted().toList();
    }

    private YearMonth firstEmploymentContradiction(
            AverageEarningsNumeratorFactsService.Resolution facts,
            List<YearMonth> explicit,
            YearMonth referenceFrom,
            YearMonth referenceTo
    ) {
        Set<YearMonth> employed = new LinkedHashSet<>();
        for (AverageEarningsNumeratorFactsService.MonthFact month : facts.months()) {
            if (month.employed()) {
                employed.add(month.period());
            }
        }

        return explicit.stream()
                .filter(Objects::nonNull)
                .filter(month -> !month.isBefore(referenceFrom) && !month.isAfter(referenceTo))
                .filter(employed::contains)
                .sorted()
                .findFirst()
                .orElse(null);
    }

    private YearMonth reconcileReferencePremiumFacts(
            AverageEarningsNumeratorFactsService.Resolution facts,
            AverageEarningsBonusP15CalculationPipelineService.Resolution premium,
            YearMonth referenceFrom,
            YearMonth referenceTo
    ) {
        Map<YearMonth, Long> semantic = new LinkedHashMap<>();
        for (AverageEarningsNumeratorFactsService.MonthFact month : facts.months()) {
            semantic.put(month.period(), month.premiumSpecialAmountMinor());
        }

        Map<YearMonth, Long> p15Facts = new LinkedHashMap<>();
        for (AverageEarningsBonusP15Policy.Decision decision : premium.policy().decisions()) {
            YearMonth accrualMonth = decision.accrualMonth();
            if (accrualMonth.isBefore(referenceFrom) || accrualMonth.isAfter(referenceTo)) {
                continue;
            }
            p15Facts.merge(
                    accrualMonth,
                    decision.factualAmountMinor(),
                    Math::addExact
            );
        }

        for (YearMonth month = referenceFrom;
                !month.isAfter(referenceTo);
                month = month.plusMonths(1)) {
            long semanticAmount = semantic.getOrDefault(month, 0L);
            long p15Amount = p15Facts.getOrDefault(month, 0L);
            if (semanticAmount != p15Amount) {
                return month;
            }
        }

        return null;
    }

    private String reconcileCurrency(
            String numeratorCurrency,
            String p15Currency
    ) {
        if (numeratorCurrency != null
                && p15Currency != null
                && !numeratorCurrency.equals(p15Currency)) {
            return null;
        }
        return numeratorCurrency != null
                ? numeratorCurrency
                : p15Currency;
    }

    public enum BlockingStage {
        NUMERATOR_FACTS,
        NO_PAYROLL_AUTHORITY,
        P15_PIPELINE,
        P15_RECONCILIATION,
        PARAGRAPH_5_MONEY,
        CURRENCY
    }

    public record Resolution(
            LocalDate eventDate,
            YearMonth eventMonth,
            YearMonth referenceFrom,
            YearMonth referenceTo,
            YearMonth discoveryThroughMonth,
            boolean ready,
            BlockingStage blockingStage,
            String blockingReason,
            YearMonth blockingPeriod,
            String currencyCode,
            AverageEarningsNumeratorFactsService.Resolution numeratorFacts,
            AverageEarningsBonusP15CalculationPipelineService.Resolution p15,
            AverageEarningsParagraph5MoneyPolicy.Resolution paragraph5Money,
            long includedOrdinaryAmountMinor,
            long excludedParagraph5OrdinaryAmountMinor,
            long includedPremiumAmountMinor,
            long rawPremiumSpecialAmountMinor,
            long excludedPreservedAverageAmountMinor,
            long numeratorAmountMinor
    ) {
        public Resolution {
            Objects.requireNonNull(eventDate, "Numerator calculation event date is required");
            Objects.requireNonNull(eventMonth, "Numerator calculation event month is required");
            Objects.requireNonNull(referenceFrom, "Numerator calculation reference start is required");
            Objects.requireNonNull(referenceTo, "Numerator calculation reference end is required");
            Objects.requireNonNull(
                    discoveryThroughMonth,
                    "Numerator calculation discovery-through month is required"
            );
            if (!eventMonth.equals(YearMonth.from(eventDate))
                    || !referenceFrom.equals(eventMonth.minusMonths(12))
                    || !referenceTo.equals(eventMonth.minusMonths(1))
                    || discoveryThroughMonth.isBefore(referenceTo)) {
                throw new IllegalArgumentException(
                        "Numerator calculation window is not canonical"
                );
            }
            if (ready == (blockingReason != null)) {
                throw new IllegalArgumentException(
                        "Numerator calculation state is invalid"
                );
            }
            if (includedOrdinaryAmountMinor < 0L
                    || excludedParagraph5OrdinaryAmountMinor < 0L
                    || includedPremiumAmountMinor < 0L
                    || rawPremiumSpecialAmountMinor < 0L
                    || excludedPreservedAverageAmountMinor < 0L
                    || numeratorAmountMinor < 0L) {
                throw new IllegalArgumentException(
                        "Numerator calculation money must be non-negative"
                );
            }

            if (ready) {
                if (blockingStage != null
                        || blockingPeriod != null
                        || numeratorFacts == null
                        || !numeratorFacts.ready()
                        || p15 == null
                        || !p15.ready()
                        || paragraph5Money == null
                        || !paragraph5Money.ready()
                        || numeratorAmountMinor
                            != Math.addExact(
                                    includedOrdinaryAmountMinor,
                                    includedPremiumAmountMinor
                            )
                        || (currencyCode != null && !currencyCode.matches("[A-Z]{3}"))) {
                    throw new IllegalArgumentException(
                            "Ready numerator calculation is incomplete"
                    );
                }
            } else {
                if (blockingStage == null
                        || currencyCode != null
                        || numeratorFacts != null
                        || p15 != null
                        || paragraph5Money != null
                        || includedOrdinaryAmountMinor != 0L
                        || excludedParagraph5OrdinaryAmountMinor != 0L
                        || includedPremiumAmountMinor != 0L
                        || rawPremiumSpecialAmountMinor != 0L
                        || excludedPreservedAverageAmountMinor != 0L
                        || numeratorAmountMinor != 0L) {
                    throw new IllegalArgumentException(
                            "Blocked numerator calculation cannot expose partial money"
                    );
                }
            }
        }

        public static Resolution ready(
                LocalDate eventDate,
                YearMonth eventMonth,
                YearMonth referenceFrom,
                YearMonth referenceTo,
                YearMonth discoveryThroughMonth,
                String currencyCode,
                AverageEarningsNumeratorFactsService.Resolution facts,
                AverageEarningsBonusP15CalculationPipelineService.Resolution p15,
                AverageEarningsParagraph5MoneyPolicy.Resolution paragraph5,
                long ordinaryIncluded,
                long paragraph5Excluded,
                long premiumIncluded,
                long rawPremiumSpecial,
                long preservedAverageExcluded,
                long numeratorAmount
        ) {
            return new Resolution(
                    eventDate,
                    eventMonth,
                    referenceFrom,
                    referenceTo,
                    discoveryThroughMonth,
                    true,
                    null,
                    null,
                    null,
                    currencyCode,
                    facts,
                    p15,
                    paragraph5,
                    ordinaryIncluded,
                    paragraph5Excluded,
                    premiumIncluded,
                    rawPremiumSpecial,
                    preservedAverageExcluded,
                    numeratorAmount
            );
        }

        public static Resolution blocked(
                LocalDate eventDate,
                YearMonth eventMonth,
                YearMonth referenceFrom,
                YearMonth referenceTo,
                YearMonth discoveryThroughMonth,
                BlockingStage stage,
                String reason,
                YearMonth period
        ) {
            Objects.requireNonNull(stage, "Numerator blocking stage is required");
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("Numerator blocking reason is required");
            }
            return new Resolution(
                    eventDate,
                    eventMonth,
                    referenceFrom,
                    referenceTo,
                    discoveryThroughMonth,
                    false,
                    stage,
                    reason,
                    period,
                    null,
                    null,
                    null,
                    null,
                    0L,
                    0L,
                    0L,
                    0L,
                    0L,
                    0L
            );
        }
    }
}
