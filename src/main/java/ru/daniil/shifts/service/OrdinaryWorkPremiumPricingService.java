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
import ru.daniil.shifts.service.PayPricingRuleResolver.Dimension;
import ru.daniil.shifts.service.PayPricingRuleResolver.Rule;
import ru.daniil.shifts.service.PayPricingRuleResolver.RuleSet;
import ru.daniil.shifts.service.exception.ApiException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Read-only monthly money projection for ordinary-work NIGHT / HOLIDAY premiums.
 *
 * IMPORTANT:
 * ordinary base money is already paid by Native Payroll.
 *
 * PayPricingEngine.baseAmountMinor is retained only for explainability and
 * rounding verification. Only premiumAmountMinor is a future Payroll addition.
 *
 * No Payroll DTO, snapshot, migration or persisted money is changed here.
 */
@Service
public class OrdinaryWorkPremiumPricingService {

    private final OrdinaryWorkPremiumSourceService sourceService;
    private final PayPricingPolicyService pricingPolicy;
    private final HistoricalCompensationRateService historicalRates;
    private final PayPricingEngine pricingEngine;

    public OrdinaryWorkPremiumPricingService(
            OrdinaryWorkPremiumSourceService sourceService,
            PayPricingPolicyService pricingPolicy,
            HistoricalCompensationRateService historicalRates,
            PayPricingEngine pricingEngine
    ) {
        this.sourceService = sourceService;
        this.pricingPolicy = pricingPolicy;
        this.historicalRates = historicalRates;
        this.pricingEngine = pricingEngine;
    }

    @Transactional(readOnly = true)
    public MonthPremiumProjection priceMonth(
            AppUser user,
            YearMonth payrollMonth
    ) {
        if (user == null) {
            throw new IllegalArgumentException(
                    "Ordinary premium pricing requires user"
            );
        }

        if (payrollMonth == null) {
            throw new IllegalArgumentException(
                    "Ordinary premium pricing requires payroll month"
            );
        }

        List<OrdinaryPremiumSource> dailySources =
                new ArrayList<>();

        List<BlockingDay> blockers =
                new ArrayList<>();

        int ordinaryMinutes = 0;

        for (
                LocalDate date = payrollMonth.atDay(1);
                !date.isAfter(payrollMonth.atEndOfMonth());
                date = date.plusDays(1)
        ) {
            OrdinaryPremiumSource source =
                    sourceService.project(
                            user,
                            date
                    );

            if (source == null) {
                throw new IllegalStateException(
                        "Ordinary premium source returned null"
                );
            }

            if (!date.equals(
                    source.payrollDate()
            )) {
                throw new IllegalStateException(
                        "Ordinary premium source returned another payroll date"
                );
            }

            ordinaryMinutes =
                    Math.addExact(
                            ordinaryMinutes,
                            source.canonicalOrdinaryMinutes()
                    );

            dailySources.add(source);

            if (!source.ready()) {
                blockers.add(
                        new BlockingDay(
                                date,
                                source.canonicalOrdinaryMinutes(),
                                source.blockingReason()
                        )
                );
            }
        }

        /*
         * Never emit partial money for a month whose clock source is ambiguous.
         */
        if (!blockers.isEmpty()) {
            return MonthPremiumProjection.blocked(
                    payrollMonth,
                    ordinaryMinutes,
                    blockers
            );
        }

        if (ordinaryMinutes == 0) {
            return MonthPremiumProjection.ready(
                    payrollMonth,
                    null,
                    0,
                    0L,
                    0L,
                    0L,
                    0L,
                    List.of(),
                    List.of(),
                    List.of()
            );
        }

        Map<LocalDate, List<SourcePiece>> piecesBySourceDate =
                new TreeMap<>();

        int sourcePieceMinutes = 0;

        for (OrdinaryPremiumSource source :
                dailySources) {

            for (SourcePiece piece :
                    source.pieces()) {

                sourcePieceMinutes =
                        Math.addExact(
                                sourcePieceMinutes,
                                piece.minutes()
                        );

                piecesBySourceDate
                        .computeIfAbsent(
                                piece.sourceDate(),
                                ignored -> new ArrayList<>()
                        )
                        .add(piece);
            }
        }

        if (sourcePieceMinutes != ordinaryMinutes) {
            throw new IllegalStateException(
                    "Ordinary premium monthly source changed canonical minute total"
            );
        }

        String currency = null;

        Map<Long, List<PricingSlice>> slicesByRate =
                new TreeMap<>();

        Map<Long, Integer> minutesByRate =
                new TreeMap<>();

        /*
         * Semantic identity remains outside PayPricingEngine.
         *
         * PayPricingEngine keeps its existing economic key
         * (code, premiumBps), preserving all rounding semantics.
         *
         * For semantic history we separately retain the machine-owned
         * Dimension associated with each economic key.
         */
        Map<Long, Map<PremiumEconomicKey, Set<Dimension>>>
                premiumDimensionsByRate =
                new TreeMap<>();

        Map<Long, Set<PremiumEconomicKey>>
                unresolvedPremiumKeysByRate =
                new TreeMap<>();

        Map<Long, Map<PremiumEconomicKey, Set<LocalDate>>>
                premiumSourceDatesByRate =
                new TreeMap<>();

        List<SourceDateValuation> valuations =
                new ArrayList<>();

        for (
                Map.Entry<LocalDate, List<SourcePiece>> entry :
                piecesBySourceDate.entrySet()
        ) {
            LocalDate sourceDate =
                    entry.getKey();

            List<SourcePiece> pieces =
                    List.copyOf(
                            entry.getValue()
                    );

            int sourceMinutes =
                    pieces.stream()
                            .mapToInt(
                                    SourcePiece::minutes
                            )
                            .sum();

            List<ConsumedSlice> consumed =
                    pieces.stream()
                            .map(
                                    SourcePiece::consumedSlice
                            )
                            .toList();

            /*
             * PREMIUM_POLICY_ONLY_WHEN_DIMENSION_PRESENT
             *
             * Pure REGULAR ordinary work has no additive premium dimension.
             * Requiring a PayPricingTerm for such minutes would regress the
             * existing Payroll foundation by making ordinary daytime work
             * depend on NIGHT / HOLIDAY configuration that cannot affect it.
             *
             * Historical base rate is still resolved below for reference /
             * explainability, but pricing policy identity exists only when a
             * premium-capable dimension is actually present.
             */
            boolean premiumDimensionsPresent =
                    pieces.stream()
                            .anyMatch(piece ->
                                    piece.night()
                                            || piece.holiday()
                            );

            List<PricingSlice> resolvedPricingSlices;
            RuleSet resolvedRuleSet = null;
            LocalDate pricingEffectiveFrom = null;

            if (premiumDimensionsPresent) {
                ResolvedPricingPolicy policy =
                        pricingPolicy.resolveForSourceDate(
                                user,
                                sourceDate,
                                consumed
                        );

                resolvedPricingSlices =
                        policy.pricingSlices();

                resolvedRuleSet =
                        policy.rules();

                pricingEffectiveFrom =
                        policy.effectiveFrom();
            } else {
                resolvedPricingSlices =
                        List.of(
                                new PricingSlice(
                                        sourceMinutes,
                                        List.of()
                                )
                        );
            }

            int pricedMinutes =
                    resolvedPricingSlices
                            .stream()
                            .mapToInt(
                                    PricingSlice::minutes
                            )
                            .sum();

            if (pricedMinutes != sourceMinutes) {
                throw new IllegalStateException(
                        "Ordinary pricing policy changed source minute total"
                );
            }

            HistoricalBaseRate rate =
                    historicalRates.resolve(
                            user,
                            sourceDate
                    );

            if (!sourceDate.equals(
                    rate.sourceDate()
            )) {
                throw new IllegalStateException(
                        "Historical ordinary rate returned another source date"
                );
            }

            if (currency == null) {
                currency =
                        rate.currencyCode();
            } else if (!currency.equals(
                    rate.currencyCode()
            )) {
                throw ApiException.conflict(
                        "PAY_PRICING_CURRENCY_MISMATCH",
                        "Обычные премиальные часы месяца используют разные валюты: "
                                + currency
                                + " и "
                                + rate.currencyCode()
                );
            }

            retainPremiumSemanticIdentity(
                    rate.baseHourlyRateMinor(),
                    resolvedRuleSet,
                    resolvedPricingSlices,
                    premiumDimensionsByRate,
                    unresolvedPremiumKeysByRate
            );

            retainPremiumSourceDate(
                    rate.baseHourlyRateMinor(),
                    sourceDate,
                    resolvedPricingSlices,
                    premiumSourceDatesByRate
            );

            slicesByRate
                    .computeIfAbsent(
                            rate.baseHourlyRateMinor(),
                            ignored -> new ArrayList<>()
                    )
                    .addAll(
                            resolvedPricingSlices
                    );

            minutesByRate.merge(
                    rate.baseHourlyRateMinor(),
                    sourceMinutes,
                    Math::addExact
            );

            int nightMinutes =
                    pieces.stream()
                            .filter(
                                    SourcePiece::night
                            )
                            .mapToInt(
                                    SourcePiece::minutes
                            )
                            .sum();

            int holidayMinutes =
                    pieces.stream()
                            .filter(
                                    SourcePiece::holiday
                            )
                            .mapToInt(
                                    SourcePiece::minutes
                            )
                            .sum();

            SourceKind sourceKind =
                    pieces.get(0)
                            .sourceKind();

            if (pieces.stream()
                    .anyMatch(piece ->
                            piece.sourceKind()
                                    != sourceKind
                    )) {
                throw new IllegalStateException(
                        "One ordinary source date contains mixed source kinds"
                );
            }

            valuations.add(
                    new SourceDateValuation(
                            sourceDate,
                            sourceKind,
                            sourceMinutes,
                            nightMinutes,
                            holidayMinutes,
                            pricingEffectiveFrom,
                            rate.sourceMonth(),
                            rate.compensationEffectiveFrom(),
                            rate.payMode(),
                            rate.currencyCode(),
                            rate.baseHourlyRateMinor(),
                            rate.productionNormMinutes(),
                            pieces,
                            resolvedPricingSlices
                    )
            );
        }

        if (currency == null) {
            throw new IllegalStateException(
                    "Ordinary premium pricing currency was not resolved"
            );
        }

        long referenceBaseAmountMinor = 0L;
        long premiumAmountMinor = 0L;

        long nightPremiumAmountMinor = 0L;
        long unclassifiedPremiumAmountMinor = 0L;

        int pricedTotalMinutes = 0;

        List<PricedRateBucket> buckets =
                new ArrayList<>();

        List<NightPremiumSourceLine> exactNightPremiumSourceLines =
                new ArrayList<>();

        for (
                Map.Entry<Long, List<PricingSlice>> entry :
                slicesByRate.entrySet()
        ) {
            long hourlyRate =
                    entry.getKey();

            PricingResult priced =
                    pricingEngine.price(
                            hourlyRate,
                            entry.getValue()
                    );

            int expectedMinutes =
                    minutesByRate.getOrDefault(
                            hourlyRate,
                            0
                    );

            if (priced.totalMinutes()
                    != expectedMinutes) {
                throw new IllegalStateException(
                        "Ordinary premium rate bucket changed minute total"
                );
            }

            pricedTotalMinutes =
                    Math.addExact(
                            pricedTotalMinutes,
                            priced.totalMinutes()
                    );

            referenceBaseAmountMinor =
                    Math.addExact(
                            referenceBaseAmountMinor,
                            priced.baseAmountMinor()
                    );

            premiumAmountMinor =
                    Math.addExact(
                            premiumAmountMinor,
                            priced.premiumAmountMinor()
                    );

            Map<PremiumEconomicKey, Set<Dimension>>
                    dimensions =
                    premiumDimensionsByRate
                            .getOrDefault(
                                    hourlyRate,
                                    Map.of()
                            );

            Set<PremiumEconomicKey> unresolved =
                    unresolvedPremiumKeysByRate
                            .getOrDefault(
                                    hourlyRate,
                                    Set.of()
                            );

            for (PricedPremium premium :
                    priced.premiums()) {

                PremiumEconomicKey key =
                        new PremiumEconomicKey(
                                premium.code(),
                                premium.premiumBps()
                        );

                Set<Dimension> observed =
                        dimensions.get(
                                key
                        );

                boolean provenNight =
                        !unresolved.contains(
                                key
                        )
                                && observed != null
                                && observed.size() == 1
                                && observed.contains(
                                        Dimension.NIGHT
                                );

                if (provenNight) {
                    nightPremiumAmountMinor =
                            Math.addExact(
                                    nightPremiumAmountMinor,
                                    premium.amountMinor()
                            );

                    Set<LocalDate> sourceDates =
                            premiumSourceDatesByRate
                                    .getOrDefault(
                                            hourlyRate,
                                            Map.of()
                                    )
                                    .get(
                                            key
                                    );

                    if (sourceDates == null
                            || sourceDates.isEmpty()) {
                        throw new IllegalStateException(
                                "Proven NIGHT premium lacks source-date provenance"
                        );
                    }

                    /*
                     * PayPricingEngine rounds after aggregating one economic
                     * premium key inside one hourly-rate bucket. Therefore
                     * money is attributable to one source date without a new
                     * allocation policy only when every priced minute of this
                     * economic bucket came from that one date.
                     */
                    if (sourceDates.size() == 1
                            && premium.amountMinor() > 0L) {
                        exactNightPremiumSourceLines.add(
                                new NightPremiumSourceLine(
                                        sourceDates
                                                .iterator()
                                                .next(),
                                        premium.minutes(),
                                        premium.amountMinor()
                                )
                        );
                    }
                } else {
                    unclassifiedPremiumAmountMinor =
                            Math.addExact(
                                    unclassifiedPremiumAmountMinor,
                                    premium.amountMinor()
                            );
                }
            }

            buckets.add(
                    new PricedRateBucket(
                            hourlyRate,
                            priced.totalMinutes(),
                            priced.baseAmountMinor(),
                            priced.premiumAmountMinor(),
                            priced.premiums()
                    )
            );
        }

        long semanticPremiumTotal =
                Math.addExact(
                        nightPremiumAmountMinor,
                        unclassifiedPremiumAmountMinor
                );

        if (semanticPremiumTotal
                != premiumAmountMinor) {
            throw new IllegalStateException(
                    "Ordinary premium semantic breakdown changed premium money"
            );
        }

        if (pricedTotalMinutes != ordinaryMinutes) {
            throw new IllegalStateException(
                    "Ordinary premium money projection changed canonical minute total"
            );
        }

        /*
         * DO NOT add PricingResult.totalAmountMinor here.
         *
         * The ordinary base component is already present in Payroll base pay.
         * Only premiumAmountMinor is the future additive payroll component.
         */
        return MonthPremiumProjection.ready(
                payrollMonth,
                currency,
                ordinaryMinutes,
                referenceBaseAmountMinor,
                premiumAmountMinor,
                nightPremiumAmountMinor,
                unclassifiedPremiumAmountMinor,
                List.copyOf(exactNightPremiumSourceLines),
                List.copyOf(buckets),
                List.copyOf(valuations)
        );
    }

    /**
     * Retains the machine dimension associated with each economic pricing key.
     *
     * Missing, inconsistent or mixed identity is not guessed: corresponding
     * money remains explicitly unclassified.
     */
    private static void retainPremiumSemanticIdentity(
            long hourlyRate,
            RuleSet rules,
            List<PricingSlice> slices,
            Map<Long, Map<PremiumEconomicKey, Set<Dimension>>>
                    dimensionsByRate,
            Map<Long, Set<PremiumEconomicKey>>
                    unresolvedByRate
    ) {
        Map<String, List<Rule>> rulesByCode =
                new LinkedHashMap<>();

        if (rules != null) {
            for (Rule rule : rules.rules()) {
                rulesByCode
                        .computeIfAbsent(
                                rule.code(),
                                ignored -> new ArrayList<>()
                        )
                        .add(rule);
            }
        }

        Map<PremiumEconomicKey, Set<Dimension>> dimensions =
                dimensionsByRate.computeIfAbsent(
                        hourlyRate,
                        ignored -> new LinkedHashMap<>()
                );

        Set<PremiumEconomicKey> unresolved =
                unresolvedByRate.computeIfAbsent(
                        hourlyRate,
                        ignored -> new LinkedHashSet<>()
                );

        for (PricingSlice slice : slices) {
            for (PayPricingEngine.PremiumComponent component :
                    slice.components()) {

                PremiumEconomicKey key =
                        new PremiumEconomicKey(
                                component.code(),
                                component.premiumBps()
                        );

                List<Rule> matches =
                        rulesByCode.getOrDefault(
                                component.code(),
                                List.of()
                        );

                if (matches.size() != 1
                        || matches.get(0).premiumBps()
                        != component.premiumBps()) {
                    unresolved.add(key);
                    continue;
                }

                dimensions
                        .computeIfAbsent(
                                key,
                                ignored -> new LinkedHashSet<>()
                        )
                        .add(
                                matches.get(0).dimension()
                        );
            }
        }
    }

    private static void retainPremiumSourceDate(
            long hourlyRate,
            LocalDate sourceDate,
            List<PricingSlice> slices,
            Map<Long, Map<PremiumEconomicKey, Set<LocalDate>>>
                    sourceDatesByRate
    ) {
        Map<PremiumEconomicKey, Set<LocalDate>> sourceDates =
                sourceDatesByRate.computeIfAbsent(
                        hourlyRate,
                        ignored -> new LinkedHashMap<>()
                );

        for (PricingSlice slice : slices) {
            for (PayPricingEngine.PremiumComponent component :
                    slice.components()) {
                PremiumEconomicKey key =
                        new PremiumEconomicKey(
                                component.code(),
                                component.premiumBps()
                        );

                sourceDates
                        .computeIfAbsent(
                                key,
                                ignored -> new LinkedHashSet<>()
                        )
                        .add(
                                sourceDate
                        );
            }
        }
    }

    private record PremiumEconomicKey(
            String code,
            int premiumBps
    ) {}

    /**
     * Deterministic immutable identity of the complete ordinary premium
     * projection.
     *
     * The fingerprint is derived only from the already-resolved projection:
     * no source, compensation or pricing lookup is repeated here.
     *
     * Pure REGULAR work has no premium pricing identity and therefore returns
     * null. Once effective-dated premium pricing participated, complete deep
     * source identity is mandatory even when the resulting premium is zero.
     */
    private static String projectionFingerprint(
            MonthPremiumProjection value
    ) {
        if (value == null) {
            throw new IllegalArgumentException(
                    "Ordinary premium projection is required"
            );
        }

        if (!value.ready()) {
            return null;
        }

        boolean pricingIdentityRequired =
                value.sources()
                        .stream()
                        .anyMatch(source ->
                                source.pricingEffectiveFrom()
                                        != null
                        );

        if (!pricingIdentityRequired) {
            return null;
        }

        if (value.sources()
                .stream()
                .anyMatch(source ->
                        !source.deepIdentityComplete()
                )) {
            throw new IllegalStateException(
                    "Ordinary premium pricing fingerprint requires complete deep source identity"
            );
        }

        StringBuilder canonical =
                new StringBuilder();

        token(
                canonical,
                "DUTYLOG_ORDINARY_PREMIUM_PRICING_V1"
        );

        token(
                canonical,
                value.payrollMonth()
        );

        token(
                canonical,
                value.currencyCode()
        );

        token(
                canonical,
                value.ordinaryMinutes()
        );

        token(
                canonical,
                value.referenceBaseAmountMinor()
        );

        token(
                canonical,
                value.premiumAmountMinor()
        );

        token(
                canonical,
                value.nightPremiumAmountMinor()
        );

        token(
                canonical,
                value.unclassifiedPremiumAmountMinor()
        );

        List<SourceDateValuation> orderedSources =
                new ArrayList<>(
                        value.sources()
                );

        orderedSources.sort(
                Comparator
                        .comparing(
                                SourceDateValuation::sourceDate
                        )
                        .thenComparing(
                                SourceDateValuation::sourceKind
                        )
        );

        token(
                canonical,
                orderedSources.size()
        );

        for (SourceDateValuation source :
                orderedSources) {

            token(canonical, "SOURCE_DATE");
            token(canonical, source.sourceDate());
            token(canonical, source.sourceKind());
            token(canonical, source.minutes());
            token(canonical, source.nightMinutes());
            token(canonical, source.holidayMinutes());
            token(canonical, source.pricingEffectiveFrom());
            token(canonical, source.compensationSourceMonth());
            token(canonical, source.compensationEffectiveFrom());
            token(canonical, source.payMode());
            token(canonical, source.currencyCode());
            token(canonical, source.baseHourlyRateMinor());
            token(canonical, source.productionNormMinutes());

            List<SourcePiece> pieces =
                    new ArrayList<>(
                            source.sourcePieces()
                    );

            pieces.sort(
                    Comparator
                            .comparing(
                                    SourcePiece::sourceKind
                            )
                            .thenComparing(
                                    SourcePiece::sourceActualWorkIntervalId,
                                    Comparator.nullsLast(
                                            Long::compareTo
                                    )
                            )
                            .thenComparing(
                                    SourcePiece::sourceDayEntryId,
                                    Comparator.nullsLast(
                                            Long::compareTo
                                    )
                            )
                            .thenComparing(
                                    SourcePiece::sourceEvidenceStartInstant
                            )
                            .thenComparing(
                                    SourcePiece::sourceEvidenceEndInstant
                            )
                            .thenComparing(
                                    SourcePiece::sourceEvidenceTimezone
                            )
                            .thenComparing(
                                    SourcePiece::night
                            )
                            .thenComparing(
                                    SourcePiece::holiday
                            )
                            .thenComparingInt(
                                    SourcePiece::minutes
                            )
            );

            token(
                    canonical,
                    pieces.size()
            );

            for (SourcePiece piece :
                    pieces) {

                token(canonical, "SOURCE_PIECE");
                token(canonical, piece.sourceDate());
                token(canonical, piece.sourceKind());
                token(
                        canonical,
                        piece.sourceActualWorkIntervalId()
                );
                token(
                        canonical,
                        piece.sourceDayEntryId()
                );
                token(
                        canonical,
                        piece.sourceEvidenceStartInstant()
                );
                token(
                        canonical,
                        piece.sourceEvidenceEndInstant()
                );
                token(
                        canonical,
                        piece.sourceEvidenceTimezone()
                );
                token(canonical, piece.minutes());
                token(canonical, piece.night());
                token(canonical, piece.holiday());
            }

            /*
             * Slice order is retained from the resolved policy projection.
             * Components inside each slice are an unordered economic set and
             * therefore canonicalized by code + bps.
             */
            token(
                    canonical,
                    source.pricingSlices()
                            .size()
            );

            for (PricingSlice slice :
                    source.pricingSlices()) {

                token(canonical, "PRICING_SLICE");
                token(canonical, slice.minutes());

                List<PayPricingEngine.PremiumComponent> components =
                        new ArrayList<>(
                                slice.components()
                        );

                components.sort(
                        Comparator
                                .comparing(
                                        PayPricingEngine.PremiumComponent::code
                                )
                                .thenComparingInt(
                                        PayPricingEngine.PremiumComponent::premiumBps
                                )
                );

                token(
                        canonical,
                        components.size()
                );

                for (PayPricingEngine.PremiumComponent component :
                        components) {

                    token(canonical, component.code());
                    token(
                            canonical,
                            component.premiumBps()
                    );
                }
            }
        }

        List<PricedRateBucket> orderedBuckets =
                new ArrayList<>(
                        value.rateBuckets()
                );

        orderedBuckets.sort(
                Comparator
                        .comparingLong(
                                PricedRateBucket::baseHourlyRateMinor
                        )
                        .thenComparingInt(
                                PricedRateBucket::minutes
                        )
        );

        token(
                canonical,
                orderedBuckets.size()
        );

        for (PricedRateBucket bucket :
                orderedBuckets) {

            token(canonical, "RATE_BUCKET");
            token(
                    canonical,
                    bucket.baseHourlyRateMinor()
            );
            token(canonical, bucket.minutes());
            token(
                    canonical,
                    bucket.referenceBaseAmountMinor()
            );
            token(
                    canonical,
                    bucket.premiumAmountMinor()
            );

            List<PricedPremium> premiums =
                    new ArrayList<>(
                            bucket.premiums()
                    );

            premiums.sort(
                    Comparator
                            .comparing(
                                    PricedPremium::code
                            )
                            .thenComparingInt(
                                    PricedPremium::premiumBps
                            )
                            .thenComparingInt(
                                    PricedPremium::minutes
                            )
                            .thenComparingLong(
                                    PricedPremium::amountMinor
                            )
            );

            token(
                    canonical,
                    premiums.size()
            );

            for (PricedPremium premium :
                    premiums) {

                token(canonical, premium.code());
                token(
                        canonical,
                        premium.premiumBps()
                );
                token(
                        canonical,
                        premium.minutes()
                );
                token(
                        canonical,
                        premium.amountMinor()
                );
            }
        }

        return sha256(
                canonical.toString()
        );
    }

    private static void token(
            StringBuilder target,
            Object value
    ) {
        if (value == null) {
            target.append(
                    "-1:|"
            );
            return;
        }

        String rendered =
                String.valueOf(
                        value
                );

        target
                .append(
                        rendered.length()
                )
                .append(':')
                .append(rendered)
                .append('|');
    }

    private static String sha256(
            String value
    ) {
        try {
            return HexFormat
                    .of()
                    .formatHex(
                            MessageDigest
                                    .getInstance(
                                            "SHA-256"
                                    )
                                    .digest(
                                            value.getBytes(
                                                    StandardCharsets.UTF_8
                                            )
                                    )
                    );
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(
                    "SHA-256 is unavailable",
                    ex
            );
        }
    }

    public record BlockingDay(
            LocalDate date,
            int ordinaryMinutes,
            String reason
    ) {
        public BlockingDay {
            if (date == null
                    || ordinaryMinutes < 0
                    || reason == null
                    || reason.isBlank()) {
                throw new IllegalArgumentException(
                        "Invalid ordinary premium blocking day"
                );
            }
        }
    }

    public record SourceDateValuation(
            LocalDate sourceDate,
            SourceKind sourceKind,
            int minutes,
            int nightMinutes,
            int holidayMinutes,
            LocalDate pricingEffectiveFrom,
            YearMonth compensationSourceMonth,
            LocalDate compensationEffectiveFrom,
            String payMode,
            String currencyCode,
            long baseHourlyRateMinor,
            Integer productionNormMinutes,
            List<SourcePiece> sourcePieces,
            List<PricingSlice> pricingSlices
    ) {
        /**
         * Compatibility constructor for callers created before D1a retained
         * deep source/pricing identity.
         */
        public SourceDateValuation(
                LocalDate sourceDate,
                SourceKind sourceKind,
                int minutes,
                int nightMinutes,
                int holidayMinutes,
                LocalDate pricingEffectiveFrom,
                YearMonth compensationSourceMonth,
                LocalDate compensationEffectiveFrom,
                String payMode,
                String currencyCode,
                long baseHourlyRateMinor,
                Integer productionNormMinutes
        ) {
            this(
                    sourceDate,
                    sourceKind,
                    minutes,
                    nightMinutes,
                    holidayMinutes,
                    pricingEffectiveFrom,
                    compensationSourceMonth,
                    compensationEffectiveFrom,
                    payMode,
                    currencyCode,
                    baseHourlyRateMinor,
                    productionNormMinutes,
                    List.of(),
                    List.of()
            );
        }

        public SourceDateValuation {
            if (sourceDate == null
                    || sourceKind == null
                    || minutes <= 0
                    || nightMinutes < 0
                    || holidayMinutes < 0
                    || nightMinutes > minutes
                    || holidayMinutes > minutes
                    || compensationSourceMonth == null
                    || compensationEffectiveFrom == null
                    || payMode == null
                    || currencyCode == null
                    || baseHourlyRateMinor <= 0) {
                throw new IllegalArgumentException(
                        "Invalid ordinary premium source valuation"
                );
            }

            sourcePieces =
                    sourcePieces == null
                            ? List.of()
                            : List.copyOf(
                                    sourcePieces
                            );

            pricingSlices =
                    pricingSlices == null
                            ? List.of()
                            : List.copyOf(
                                    pricingSlices
                            );

            boolean premiumDimensionsPresent =
                    nightMinutes > 0
                            || holidayMinutes > 0;

            if (premiumDimensionsPresent
                    && pricingEffectiveFrom == null) {
                throw new IllegalArgumentException(
                        "Premium-bearing ordinary source requires pricing identity"
                );
            }

            if (pricingEffectiveFrom != null
                    && pricingEffectiveFrom.isAfter(
                            sourceDate
                    )) {
                throw new IllegalArgumentException(
                        "Ordinary pricing term cannot become effective after source date"
                );
            }

            if (!sourcePieces.isEmpty()) {
                int sourcePieceMinutes =
                        sourcePieces.stream()
                                .mapToInt(
                                        SourcePiece::minutes
                                )
                                .sum();

                if (sourcePieceMinutes != minutes) {
                    throw new IllegalArgumentException(
                            "Ordinary source valuation pieces must preserve source minutes"
                    );
                }

                if (sourcePieces.stream()
                        .anyMatch(piece ->
                                !sourceDate.equals(
                                        piece.sourceDate()
                                )
                                        || piece.sourceKind()
                                        != sourceKind
                        )) {
                    throw new IllegalArgumentException(
                            "Ordinary source valuation contains foreign source identity"
                    );
                }
            }

            if (!pricingSlices.isEmpty()) {
                int pricedMinutes =
                        pricingSlices.stream()
                                .mapToInt(
                                        PricingSlice::minutes
                                )
                                .sum();

                if (pricedMinutes != minutes) {
                    throw new IllegalArgumentException(
                            "Ordinary source valuation pricing slices must preserve source minutes"
                    );
                }
            }
        }

        public boolean deepIdentityComplete() {
            return !sourcePieces.isEmpty()
                    && sourcePieces.stream()
                            .allMatch(
                                    SourcePiece::deepIdentityComplete
                            )
                    && !pricingSlices.isEmpty();
        }
    }

    public record NightPremiumSourceLine(
            LocalDate earningDate,
            int minutes,
            long amountMinor
    ) {
        public NightPremiumSourceLine {
            if (earningDate == null
                    || minutes <= 0
                    || amountMinor <= 0L) {
                throw new IllegalArgumentException(
                        "Exact NIGHT premium source line is invalid"
                );
            }
        }
    }

    public record PricedRateBucket(
            long baseHourlyRateMinor,
            int minutes,
            long referenceBaseAmountMinor,
            long premiumAmountMinor,
            List<PricedPremium> premiums
    ) {
        public PricedRateBucket {
            if (baseHourlyRateMinor <= 0
                    || minutes <= 0
                    || referenceBaseAmountMinor < 0
                    || premiumAmountMinor < 0) {
                throw new IllegalArgumentException(
                        "Invalid ordinary premium rate bucket"
                );
            }

            premiums =
                    premiums == null
                            ? List.of()
                            : List.copyOf(
                                    premiums
                            );
        }
    }

    public record MonthPremiumProjection(
            YearMonth payrollMonth,
            boolean ready,
            String blockingReason,
            List<BlockingDay> blockers,
            String currencyCode,
            int ordinaryMinutes,
            long referenceBaseAmountMinor,
            long premiumAmountMinor,
            long nightPremiumAmountMinor,
            long unclassifiedPremiumAmountMinor,
            List<NightPremiumSourceLine> exactNightPremiumSourceLines,
            List<PricedRateBucket> rateBuckets,
            List<SourceDateValuation> sources
    ) {
        public MonthPremiumProjection(
                YearMonth payrollMonth,
                boolean ready,
                String blockingReason,
                List<BlockingDay> blockers,
                String currencyCode,
                int ordinaryMinutes,
                long referenceBaseAmountMinor,
                long premiumAmountMinor,
                List<PricedRateBucket> rateBuckets,
                List<SourceDateValuation> sources
        ) {
            this(
                    payrollMonth,
                    ready,
                    blockingReason,
                    blockers,
                    currencyCode,
                    ordinaryMinutes,
                    referenceBaseAmountMinor,
                    premiumAmountMinor,
                    0L,
                    premiumAmountMinor,
                    List.of(),
                    rateBuckets,
                    sources
            );
        }

        /**
         * Compatibility constructor for callers created before 8A4E2B1.
         */
        public MonthPremiumProjection(
                YearMonth payrollMonth,
                boolean ready,
                String blockingReason,
                List<BlockingDay> blockers,
                String currencyCode,
                int ordinaryMinutes,
                long referenceBaseAmountMinor,
                long premiumAmountMinor,
                long nightPremiumAmountMinor,
                long unclassifiedPremiumAmountMinor,
                List<PricedRateBucket> rateBuckets,
                List<SourceDateValuation> sources
        ) {
            this(
                    payrollMonth,
                    ready,
                    blockingReason,
                    blockers,
                    currencyCode,
                    ordinaryMinutes,
                    referenceBaseAmountMinor,
                    premiumAmountMinor,
                    nightPremiumAmountMinor,
                    unclassifiedPremiumAmountMinor,
                    List.of(),
                    rateBuckets,
                    sources
            );
        }

        public MonthPremiumProjection {
            if (payrollMonth == null
                    || ordinaryMinutes < 0
                    || referenceBaseAmountMinor < 0
                    || premiumAmountMinor < 0
                    || nightPremiumAmountMinor < 0
                    || unclassifiedPremiumAmountMinor < 0) {
                throw new IllegalArgumentException(
                        "Invalid ordinary premium month projection"
                );
            }

            blockers =
                    blockers == null
                            ? List.of()
                            : List.copyOf(blockers);

            long semanticPremiumTotal;

            try {
                semanticPremiumTotal =
                        Math.addExact(
                                nightPremiumAmountMinor,
                                unclassifiedPremiumAmountMinor
                        );
            } catch (ArithmeticException ex) {
                throw new IllegalArgumentException(
                        "Ordinary premium semantic amount overflow",
                        ex
                );
            }

            if (semanticPremiumTotal
                    != premiumAmountMinor) {
                throw new IllegalArgumentException(
                        "Ordinary premium semantic breakdown must preserve premium money"
                );
            }

            exactNightPremiumSourceLines =
                    exactNightPremiumSourceLines == null
                            ? List.of()
                            : List.copyOf(
                                    exactNightPremiumSourceLines
                            );

            long exactNightAmount = 0L;

            for (NightPremiumSourceLine line :
                    exactNightPremiumSourceLines) {
                if (line == null
                        || !YearMonth.from(
                                line.earningDate()
                        ).equals(
                                payrollMonth
                        )) {
                    throw new IllegalArgumentException(
                            "Exact NIGHT premium source line belongs to another payroll month"
                    );
                }

                exactNightAmount =
                        Math.addExact(
                                exactNightAmount,
                                line.amountMinor()
                        );
            }

            if (exactNightAmount > nightPremiumAmountMinor) {
                throw new IllegalArgumentException(
                        "Exact NIGHT premium source money exceeds proven NIGHT aggregate"
                );
            }

            rateBuckets =
                    rateBuckets == null
                            ? List.of()
                            : List.copyOf(rateBuckets);

            sources =
                    sources == null
                            ? List.of()
                            : List.copyOf(sources);

            if (ready) {
                if (blockingReason != null
                        || !blockers.isEmpty()) {
                    throw new IllegalArgumentException(
                            "Ready ordinary premium month cannot be blocked"
                    );
                }

                if (ordinaryMinutes > 0
                        && (currencyCode == null
                        || currencyCode.isBlank())) {
                    throw new IllegalArgumentException(
                            "Priced ordinary premium month requires currency"
                    );
                }
            } else {
                if (blockingReason == null
                        || blockingReason.isBlank()
                        || blockers.isEmpty()
                        || currencyCode != null
                        || referenceBaseAmountMinor != 0
                        || premiumAmountMinor != 0
                        || !exactNightPremiumSourceLines.isEmpty()
                        || !rateBuckets.isEmpty()
                        || !sources.isEmpty()) {
                    throw new IllegalArgumentException(
                            "Blocked ordinary premium month cannot contain speculative money"
                    );
                }
            }
        }

        /**
         * Complete deterministic source / pricing / historical-rate identity.
         *
         * Null means no effective-dated ordinary premium pricing participated.
         */
        public String pricingFingerprint() {
            return projectionFingerprint(
                    this
            );
        }

        static MonthPremiumProjection ready(
                YearMonth month,
                String currency,
                int minutes,
                long referenceBase,
                long premium,
                long nightPremium,
                long unclassifiedPremium,
                List<NightPremiumSourceLine> exactNightPremiumSourceLines,
                List<PricedRateBucket> buckets,
                List<SourceDateValuation> sources
        ) {
            return new MonthPremiumProjection(
                    month,
                    true,
                    null,
                    List.of(),
                    currency,
                    minutes,
                    referenceBase,
                    premium,
                    nightPremium,
                    unclassifiedPremium,
                    exactNightPremiumSourceLines,
                    buckets,
                    sources
            );
        }

        static MonthPremiumProjection blocked(
                YearMonth month,
                int minutes,
                List<BlockingDay> blockers
        ) {
            return new MonthPremiumProjection(
                    month,
                    false,
                    "ORDINARY_PREMIUM_SOURCE_NOT_READY",
                    blockers,
                    null,
                    minutes,
                    0L,
                    0L,
                    0L,
                    0L,
                    List.of(),
                    List.of(),
                    List.of()
            );
        }
    }
}
