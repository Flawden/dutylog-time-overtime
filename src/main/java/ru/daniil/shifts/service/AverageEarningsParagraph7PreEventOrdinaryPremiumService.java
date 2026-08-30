package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.service.HistoricalCompensationRateService.HistoricalBaseRate;
import ru.daniil.shifts.service.OrdinaryWorkPremiumSourceService.OrdinaryPremiumSource;
import ru.daniil.shifts.service.OrdinaryWorkPremiumSourceService.SourceKind;
import ru.daniil.shifts.service.OrdinaryWorkPremiumSourceService.SourcePiece;
import ru.daniil.shifts.service.PayPricingEngine.PricedPremium;
import ru.daniil.shifts.service.PayPricingEngine.PricingResult;
import ru.daniil.shifts.service.PayPricingEngine.PricingSlice;
import ru.daniil.shifts.service.PayPricingPolicyService.ResolvedPricingPolicy;
import ru.daniil.shifts.service.PayPricingRuleResolver.ConsumedSlice;
import ru.daniil.shifts.service.exception.ApiException;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Paragraph-7 range-bound ordinary NIGHT / HOLIDAY premium calculation.
 *
 * <p>The legal source window is exactly {@code [eventMonthStart,eventDate)}.
 * This adapter reuses the same factual source, effective pricing policy,
 * historical ordinary rate and pure PayPricingEngine that Native Payroll uses,
 * but it never asks the month-wide preview to price days on/after the event.</p>
 *
 * <p>Premium slices from all admissible pre-event source dates are grouped by
 * historical hourly rate before money is calculated. PayPricingEngine therefore
 * keeps its canonical rule: one economic premium key is aggregated before the
 * minor-unit HALF_UP boundary. Pricing each day separately is intentionally
 * forbidden because it could create rounding drift.</p>
 *
 * <p>The engine's ordinary base amount is reference-only here and is never
 * added to paragraph-7 BASE_PAY. J3B2 already owns BASE_PAY money.</p>
 */
@Service
public class AverageEarningsParagraph7PreEventOrdinaryPremiumService {
    public static final String SEMANTIC_AUTHORITY_WINDOW_MISMATCH =
            "PP_540_P7_PRE_EVENT_ORDINARY_PREMIUM_WINDOW_MISMATCH";
    public static final String SOURCE_BLOCKED =
            "PP_540_P7_PRE_EVENT_ORDINARY_PREMIUM_SOURCE_BLOCKED";
    public static final String PRICING_AUTHORITY_BLOCKED =
            "PP_540_P7_PRE_EVENT_ORDINARY_PREMIUM_PRICING_BLOCKED";
    public static final String CURRENCY_MISMATCH =
            "PP_540_P7_PRE_EVENT_ORDINARY_PREMIUM_CURRENCY_MISMATCH";

    private final OrdinaryWorkPremiumSourceService sourceService;
    private final PayPricingPolicyService pricingPolicy;
    private final HistoricalCompensationRateService historicalRates;
    private final PayPricingEngine pricingEngine;

    public AverageEarningsParagraph7PreEventOrdinaryPremiumService(
            OrdinaryWorkPremiumSourceService sourceService,
            PayPricingPolicyService pricingPolicy,
            HistoricalCompensationRateService historicalRates,
            PayPricingEngine pricingEngine
    ) {
        this.sourceService = Objects.requireNonNull(
                sourceService,
                "Paragraph-7 ordinary premium requires ordinary-work source authority"
        );
        this.pricingPolicy = Objects.requireNonNull(
                pricingPolicy,
                "Paragraph-7 ordinary premium requires effective pricing policy"
        );
        this.historicalRates = Objects.requireNonNull(
                historicalRates,
                "Paragraph-7 ordinary premium requires historical rate authority"
        );
        this.pricingEngine = Objects.requireNonNull(
                pricingEngine,
                "Paragraph-7 ordinary premium requires canonical pricing engine"
        );
    }

    @Transactional(readOnly = true)
    public Resolution resolve(
            AppUser user,
            AverageEarningsParagraph7PreEventSemanticWageFactService.Resolution semanticFacts
    ) {
        Objects.requireNonNull(
                user,
                "Paragraph-7 ordinary premium requires user"
        );
        Objects.requireNonNull(
                semanticFacts,
                "Paragraph-7 ordinary premium requires semantic wage provenance"
        );
        if (!semanticFacts.ready()) {
            throw new IllegalArgumentException(
                    "Blocked paragraph-7 semantic facts cannot reach ordinary premium money"
            );
        }

        AverageEarningsParagraph7PreEventBasePayFormula.Calculation basePay =
                Objects.requireNonNull(
                        semanticFacts.basePay(),
                        "Paragraph-7 semantic facts lost BASE_PAY provenance"
                );
        AverageEarningsParagraph7PreEventBasePayAuthorityService.Resolution authority =
                Objects.requireNonNull(
                        basePay.authority(),
                        "Paragraph-7 ordinary premium lost BASE_PAY authority"
                );

        LocalDate eventDate = Objects.requireNonNull(
                semanticFacts.eventDate(),
                "Paragraph-7 ordinary premium requires event date"
        );
        LocalDate periodFrom = YearMonth.from(eventDate).atDay(1);
        LocalDate cutoffExclusive = eventDate;

        if (!periodFrom.equals(semanticFacts.periodFrom())
                || !cutoffExclusive.equals(semanticFacts.cutoffExclusive())
                || !eventDate.equals(authority.eventDate())
                || !periodFrom.equals(authority.periodFrom())
                || !cutoffExclusive.equals(authority.cutoffExclusive())) {
            return Resolution.blocked(
                    eventDate,
                    periodFrom,
                    semanticFacts,
                    SEMANTIC_AUTHORITY_WINDOW_MISMATCH,
                    "Paragraph-7 provenance does not match the legal pre-event ordinary-premium window",
                    null
            );
        }

        if (!authority.workedTimePresent()) {
            return Resolution.ready(
                    eventDate,
                    periodFrom,
                    semanticFacts,
                    0,
                    0L,
                    null,
                    List.of(),
                    List.of()
            );
        }

        String expectedCurrency = basePay.currencyCode();
        if (expectedCurrency == null
                || !expectedCurrency.matches("[A-Z]{3}")) {
            throw new IllegalStateException(
                    "Worked paragraph-7 ordinary premium requires canonical BASE_PAY currency"
            );
        }

        List<OrdinaryPremiumSource> dailySources = new ArrayList<>();
        int ordinaryMinutes = 0;
        for (LocalDate date = periodFrom;
             date.isBefore(cutoffExclusive);
             date = date.plusDays(1)) {
            OrdinaryPremiumSource source = Objects.requireNonNull(
                    sourceService.project(user, date),
                    "Paragraph-7 ordinary premium source returned null"
            );
            if (!date.equals(source.payrollDate())) {
                throw new IllegalStateException(
                        "Paragraph-7 ordinary premium source returned another payroll date"
                );
            }
            ordinaryMinutes = Math.addExact(
                    ordinaryMinutes,
                    source.canonicalOrdinaryMinutes()
            );
            if (!source.ready()) {
                return Resolution.blocked(
                        eventDate,
                        periodFrom,
                        semanticFacts,
                        SOURCE_BLOCKED,
                        "Ordinary premium source is blocked for " + date,
                        source.blockingReason()
                );
            }
            for (SourcePiece piece : source.pieces()) {
                if (!date.equals(piece.sourceDate())
                        || piece.sourceDate().isBefore(periodFrom)
                        || !piece.sourceDate().isBefore(cutoffExclusive)) {
                    throw new IllegalStateException(
                            "Paragraph-7 ordinary premium source piece exceeds legal pre-event window"
                    );
                }
            }
            dailySources.add(source);
        }

        if (ordinaryMinutes == 0) {
            return Resolution.ready(
                    eventDate,
                    periodFrom,
                    semanticFacts,
                    0,
                    0L,
                    expectedCurrency,
                    List.of(),
                    List.of()
            );
        }

        Map<Long, List<PricingSlice>> slicesByRate = new TreeMap<>();
        Map<Long, Integer> minutesByRate = new TreeMap<>();
        List<SourceDateAuthority> pricingSources = new ArrayList<>();

        for (OrdinaryPremiumSource source : dailySources) {
            if (source.canonicalOrdinaryMinutes() == 0) {
                continue;
            }
            List<SourcePiece> pieces = source.pieces();
            boolean premiumDimensionsPresent = pieces.stream()
                    .anyMatch(piece -> piece.night() || piece.holiday());
            if (!premiumDimensionsPresent) {
                continue;
            }

            List<ConsumedSlice> consumed = pieces.stream()
                    .map(SourcePiece::consumedSlice)
                    .toList();

            final ResolvedPricingPolicy policy;
            final HistoricalBaseRate rate;
            try {
                policy = Objects.requireNonNull(
                        pricingPolicy.resolveForSourceDate(
                                user,
                                source.payrollDate(),
                                consumed
                        ),
                        "Paragraph-7 ordinary premium pricing policy returned null"
                );
                rate = Objects.requireNonNull(
                        historicalRates.resolve(
                                user,
                                source.payrollDate()
                        ),
                        "Paragraph-7 ordinary premium historical rate returned null"
                );
            } catch (ApiException ex) {
                String sourceReason = ex.getCode();
                if (sourceReason == null || sourceReason.isBlank()) {
                    throw ex;
                }
                return Resolution.blocked(
                        eventDate,
                        periodFrom,
                        semanticFacts,
                        PRICING_AUTHORITY_BLOCKED,
                        ex.getMessage() == null
                                ? "Paragraph-7 ordinary premium pricing authority is blocked"
                                : ex.getMessage(),
                        sourceReason
                );
            }

            if (!source.payrollDate().equals(policy.sourceDate())) {
                throw new IllegalStateException(
                        "Paragraph-7 ordinary premium pricing policy returned another source date"
                );
            }
            if (!source.payrollDate().equals(rate.sourceDate())) {
                throw new IllegalStateException(
                        "Paragraph-7 ordinary premium historical rate returned another source date"
                );
            }
            if (!expectedCurrency.equals(rate.currencyCode())) {
                return Resolution.blocked(
                        eventDate,
                        periodFrom,
                        semanticFacts,
                        CURRENCY_MISMATCH,
                        "Ordinary premium currency disagrees with paragraph-7 BASE_PAY",
                        rate.currencyCode()
                );
            }

            List<PricingSlice> pricingSlices = policy.pricingSlices();
            int pricedMinutes = pricingSlices.stream()
                    .mapToInt(PricingSlice::minutes)
                    .sum();
            if (pricedMinutes != source.canonicalOrdinaryMinutes()) {
                throw new IllegalStateException(
                        "Paragraph-7 ordinary premium policy changed canonical source minutes"
                );
            }

            long hourlyRate = rate.baseHourlyRateMinor();
            slicesByRate
                    .computeIfAbsent(
                            hourlyRate,
                            ignored -> new ArrayList<>()
                    )
                    .addAll(pricingSlices);
            minutesByRate.merge(
                    hourlyRate,
                    source.canonicalOrdinaryMinutes(),
                    Math::addExact
            );

            int nightMinutes = pieces.stream()
                    .filter(SourcePiece::night)
                    .mapToInt(SourcePiece::minutes)
                    .sum();
            int holidayMinutes = pieces.stream()
                    .filter(SourcePiece::holiday)
                    .mapToInt(SourcePiece::minutes)
                    .sum();

            pricingSources.add(
                    new SourceDateAuthority(
                            source.payrollDate(),
                            source.sourceKind(),
                            source.canonicalOrdinaryMinutes(),
                            nightMinutes,
                            holidayMinutes,
                            policy.effectiveFrom(),
                            rate.compensationEffectiveFrom(),
                            rate.payMode(),
                            rate.currencyCode(),
                            hourlyRate,
                            rate.productionNormMinutes()
                    )
            );
        }

        if (slicesByRate.isEmpty()) {
            return Resolution.ready(
                    eventDate,
                    periodFrom,
                    semanticFacts,
                    ordinaryMinutes,
                    0L,
                    expectedCurrency,
                    List.of(),
                    List.of()
            );
        }

        long premiumAmountMinor = 0L;
        List<RateBucket> buckets = new ArrayList<>();
        for (Map.Entry<Long, List<PricingSlice>> entry : slicesByRate.entrySet()) {
            long hourlyRate = entry.getKey();
            PricingResult priced = Objects.requireNonNull(
                    pricingEngine.price(
                            hourlyRate,
                            entry.getValue()
                    ),
                    "Paragraph-7 ordinary premium pricing engine returned null"
            );
            int expectedMinutesForRate = minutesByRate.getOrDefault(
                    hourlyRate,
                    0
            );
            if (priced.totalMinutes() != expectedMinutesForRate) {
                throw new IllegalStateException(
                        "Paragraph-7 ordinary premium pricing changed rate-bucket minutes"
                );
            }
            premiumAmountMinor = Math.addExact(
                    premiumAmountMinor,
                    priced.premiumAmountMinor()
            );
            buckets.add(
                    new RateBucket(
                            hourlyRate,
                            priced.totalMinutes(),
                            priced.premiumAmountMinor(),
                            priced.premiums()
                    )
            );
        }

        return Resolution.ready(
                eventDate,
                periodFrom,
                semanticFacts,
                ordinaryMinutes,
                premiumAmountMinor,
                expectedCurrency,
                List.copyOf(pricingSources),
                List.copyOf(buckets)
        );
    }

    public record SourceDateAuthority(
            LocalDate sourceDate,
            SourceKind sourceKind,
            int ordinaryMinutes,
            int nightMinutes,
            int holidayMinutes,
            LocalDate pricingEffectiveFrom,
            LocalDate compensationEffectiveFrom,
            String payMode,
            String currencyCode,
            long baseHourlyRateMinor,
            Integer productionNormMinutes
    ) {
        public SourceDateAuthority {
            Objects.requireNonNull(sourceDate, "Ordinary premium source date is required");
            Objects.requireNonNull(sourceKind, "Ordinary premium source kind is required");
            Objects.requireNonNull(pricingEffectiveFrom, "Ordinary premium pricing identity is required");
            Objects.requireNonNull(compensationEffectiveFrom, "Ordinary premium compensation identity is required");
            if (ordinaryMinutes <= 0
                    || nightMinutes < 0
                    || holidayMinutes < 0
                    || (nightMinutes == 0 && holidayMinutes == 0)
                    || nightMinutes > ordinaryMinutes
                    || holidayMinutes > ordinaryMinutes
                    || payMode == null
                    || currencyCode == null
                    || !currencyCode.matches("[A-Z]{3}")
                    || baseHourlyRateMinor <= 0L
                    || pricingEffectiveFrom.isAfter(sourceDate)) {
                throw new IllegalArgumentException(
                        "Paragraph-7 ordinary premium source authority is invalid"
                );
            }
        }
    }

    public record RateBucket(
            long baseHourlyRateMinor,
            int minutes,
            long premiumAmountMinor,
            List<PricedPremium> premiums
    ) {
        public RateBucket {
            if (baseHourlyRateMinor <= 0L
                    || minutes <= 0
                    || premiumAmountMinor < 0L) {
                throw new IllegalArgumentException(
                        "Paragraph-7 ordinary premium rate bucket is invalid"
                );
            }
            premiums = premiums == null
                    ? List.of()
                    : List.copyOf(premiums);
        }
    }

    public record Resolution(
            LocalDate eventDate,
            LocalDate periodFrom,
            LocalDate cutoffExclusive,
            boolean ready,
            String blockingReason,
            String blockingMessage,
            String sourceBlockingReason,
            AverageEarningsParagraph7PreEventSemanticWageFactService.Resolution semanticFacts,
            int ordinaryMinutes,
            long ordinaryPremiumAmountMinor,
            String currencyCode,
            List<SourceDateAuthority> pricingSources,
            List<RateBucket> rateBuckets
    ) {
        public Resolution {
            Objects.requireNonNull(eventDate, "Paragraph-7 ordinary premium event date is required");
            Objects.requireNonNull(periodFrom, "Paragraph-7 ordinary premium period start is required");
            Objects.requireNonNull(cutoffExclusive, "Paragraph-7 ordinary premium cutoff is required");
            Objects.requireNonNull(semanticFacts, "Paragraph-7 ordinary premium provenance is required");
            pricingSources = pricingSources == null
                    ? List.of()
                    : List.copyOf(pricingSources);
            rateBuckets = rateBuckets == null
                    ? List.of()
                    : List.copyOf(rateBuckets);

            if (!periodFrom.equals(YearMonth.from(eventDate).atDay(1))
                    || !cutoffExclusive.equals(eventDate)
                    || ordinaryMinutes < 0
                    || ordinaryPremiumAmountMinor < 0L) {
                throw new IllegalArgumentException(
                        "Paragraph-7 ordinary premium resolution is invalid"
                );
            }
            if (ready) {
                if (blockingReason != null
                        || blockingMessage != null
                        || sourceBlockingReason != null) {
                    throw new IllegalArgumentException(
                            "Ready paragraph-7 ordinary premium cannot contain blocker"
                    );
                }
                if (currencyCode != null
                        && !currencyCode.matches("[A-Z]{3}")) {
                    throw new IllegalArgumentException(
                            "Ready paragraph-7 ordinary premium has invalid currency"
                    );
                }
                long bucketMoney = 0L;
                for (RateBucket bucket : rateBuckets) {
                    bucketMoney = Math.addExact(
                            bucketMoney,
                            bucket.premiumAmountMinor()
                    );
                }
                if (bucketMoney != ordinaryPremiumAmountMinor) {
                    throw new IllegalArgumentException(
                            "Paragraph-7 ordinary premium buckets do not preserve money"
                    );
                }
            } else {
                if (blockingReason == null
                        || blockingReason.isBlank()
                        || blockingMessage == null
                        || blockingMessage.isBlank()
                        || ordinaryPremiumAmountMinor != 0L
                        || currencyCode != null
                        || !pricingSources.isEmpty()
                        || !rateBuckets.isEmpty()) {
                    throw new IllegalArgumentException(
                            "Blocked paragraph-7 ordinary premium cannot expose partial money"
                    );
                }
            }
        }

        static Resolution ready(
                LocalDate eventDate,
                LocalDate periodFrom,
                AverageEarningsParagraph7PreEventSemanticWageFactService.Resolution semanticFacts,
                int ordinaryMinutes,
                long premiumAmountMinor,
                String currencyCode,
                List<SourceDateAuthority> pricingSources,
                List<RateBucket> buckets
        ) {
            return new Resolution(
                    eventDate,
                    periodFrom,
                    eventDate,
                    true,
                    null,
                    null,
                    null,
                    semanticFacts,
                    ordinaryMinutes,
                    premiumAmountMinor,
                    currencyCode,
                    pricingSources,
                    buckets
            );
        }

        static Resolution blocked(
                LocalDate eventDate,
                LocalDate periodFrom,
                AverageEarningsParagraph7PreEventSemanticWageFactService.Resolution semanticFacts,
                String reason,
                String message,
                String sourceReason
        ) {
            return new Resolution(
                    eventDate,
                    periodFrom,
                    eventDate,
                    false,
                    reason,
                    message,
                    sourceReason,
                    semanticFacts,
                    0,
                    0L,
                    null,
                    List.of(),
                    List.of()
            );
        }

        public boolean premiumMoneyPresent() {
            return ready && ordinaryPremiumAmountMinor > 0L;
        }
    }
}
