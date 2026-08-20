package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.service.HistoricalCompensationRateService.HistoricalBaseRate;
import ru.daniil.shifts.service.OvertimeSettlementPricingSourceService.SettlementPricingSource;
import ru.daniil.shifts.service.OvertimeSettlementPricingSourceService.SourcePiece;
import ru.daniil.shifts.service.PayPricingEngine.PricedPremium;
import ru.daniil.shifts.service.PayPricingEngine.PricingResult;
import ru.daniil.shifts.service.PayPricingEngine.PricingSlice;
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
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Read-only monetary projection for one explicit overtime settlement.
 *
 * Flow:
 *
 * Settlement
 *   -> canonical FIFO consumed provenance
 *   -> source-date pricing policy
 *   -> source-month historical base hourly value
 *   -> pure PayPricingEngine
 *   -> settlement money projection.
 *
 * Nothing is persisted here.
 *
 * Rounding boundary is economic rate, NOT provenance-piece shape.
 * Equal-rate source fragments are priced together so arbitrary factual
 * segmentation cannot change money.
 */
@Service
public class OvertimeSettlementPricingService {

    private final OvertimeSettlementPricingSourceService sourceService;
    private final HistoricalCompensationRateService historicalRates;
    private final PayPricingEngine pricingEngine;

    public OvertimeSettlementPricingService(
            OvertimeSettlementPricingSourceService sourceService,
            HistoricalCompensationRateService historicalRates,
            PayPricingEngine pricingEngine
    ) {
        this.sourceService = sourceService;
        this.historicalRates = historicalRates;
        this.pricingEngine = pricingEngine;
    }

    @Transactional(readOnly = true)
    public SettlementMoneyProjection price(
            AppUser user,
            Long settlementId
    ) {
        if (user == null) {
            throw new IllegalArgumentException(
                    "Settlement pricing requires user"
            );
        }

        SettlementPricingSource source =
                sourceService.project(
                        user,
                        settlementId
                );

        if (source.pieces().isEmpty()) {
            throw new IllegalStateException(
                    "Settlement pricing source contains no provenance pieces"
            );
        }

        /*
         * One source date may appear in several provenance pieces.
         * Resolve its historical compensation identity once.
         */
        Map<LocalDate, HistoricalBaseRate> rateBySourceDate =
                new LinkedHashMap<>();

        List<SourceValuation> valuations =
                new ArrayList<>();

        String currency = null;

        /*
         * TreeMap gives deterministic economic bucket ordering by hourly rate.
         *
         * Key is deliberately only the monetary base rate after currency has
         * been proven homogeneous. Compensation/pricing version identity is
         * retained in SourceValuation, but it is NOT a rounding boundary.
         */
        Map<Long, List<PricingSlice>> slicesByRate =
                new TreeMap<>();

        Map<Long, Integer> minutesByRate =
                new TreeMap<>();

        for (SourcePiece piece :
                source.pieces()) {

            HistoricalBaseRate rate =
                    rateBySourceDate.get(
                            piece.sourceDate()
                    );

            if (rate == null) {
                rate =
                        historicalRates.resolve(
                                user,
                                piece.sourceDate()
                        );

                if (!piece.sourceDate()
                        .equals(
                                rate.sourceDate()
                        )) {
                    throw new IllegalStateException(
                            "Historical compensation valuation returned another source date"
                    );
                }

                rateBySourceDate.put(
                        piece.sourceDate(),
                        rate
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
                        "Settlement использует часы из разных валютных периодов: "
                                + currency
                                + " и "
                                + rate.currencyCode()
                );
            }

            int pricedMinutes =
                    piece.pricingSlices()
                            .stream()
                            .mapToInt(
                                    PricingSlice::minutes
                            )
                            .sum();

            if (pricedMinutes
                    != piece.minutes()) {
                throw new IllegalStateException(
                        "Pricing source piece changed minute total"
                );
            }

            slicesByRate
                    .computeIfAbsent(
                            rate.baseHourlyRateMinor(),
                            ignored ->
                                    new ArrayList<>()
                    )
                    .addAll(
                            piece.pricingSlices()
                    );

            minutesByRate.merge(
                    rate.baseHourlyRateMinor(),
                    piece.minutes(),
                    Math::addExact
            );

            valuations.add(
                    new SourceValuation(
                            piece.allocationId(),
                            piece.creditId(),
                            piece.sourceActualWorkIntervalId(),
                            piece.sourceDate(),
                            piece.minutes(),
                            piece.overtimeCreditOffsetStartMinutes(),
                            piece.factualWorkedOrdinalStartMinutes(),
                            piece.pricingEffectiveFrom(),
                            rate.sourceMonth(),
                            rate.compensationEffectiveFrom(),
                            rate.payMode(),
                            rate.currencyCode(),
                            rate.baseHourlyRateMinor(),
                            rate.productionNormMinutes(),
                            piece.night(),
                            piece.holiday(),
                            piece.pricingSlices()
                    )
            );
        }

        if (currency == null) {
            throw new IllegalStateException(
                    "Settlement pricing currency was not resolved"
            );
        }

        List<PricedRateBucket> buckets =
                new ArrayList<>();

        long baseAmount = 0L;
        long premiumAmount = 0L;
        long totalAmount = 0L;
        int totalMinutes = 0;

        for (Map.Entry<Long, List<PricingSlice>> entry :
                slicesByRate.entrySet()) {

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
                        "Pricing engine changed rate-bucket minute total"
                );
            }

            totalMinutes =
                    Math.addExact(
                            totalMinutes,
                            priced.totalMinutes()
                    );

            baseAmount =
                    Math.addExact(
                            baseAmount,
                            priced.baseAmountMinor()
                    );

            premiumAmount =
                    Math.addExact(
                            premiumAmount,
                            priced.premiumAmountMinor()
                    );

            totalAmount =
                    Math.addExact(
                            totalAmount,
                            priced.totalAmountMinor()
                    );

            buckets.add(
                    new PricedRateBucket(
                            hourlyRate,
                            priced.totalMinutes(),
                            priced.baseAmountMinor(),
                            priced.premiumAmountMinor(),
                            priced.totalAmountMinor(),
                            priced.premiums()
                    )
            );
        }

        if (totalMinutes
                != source.requestedMinutes()) {
            throw new IllegalStateException(
                    "Settlement money projection does not preserve requested minutes"
            );
        }

        if (Math.addExact(
                baseAmount,
                premiumAmount
        ) != totalAmount) {
            throw new IllegalStateException(
                    "Settlement money components do not sum to total"
            );
        }

        return new SettlementMoneyProjection(
                source.settlementId(),
                source.settlementDate(),
                currency,
                source.requestedMinutes(),
                baseAmount,
                premiumAmount,
                totalAmount,
                List.copyOf(buckets),
                List.copyOf(valuations)
        );
    }


    /**
     * Deterministic identity of the complete monetary projection.
     *
     * It is deliberately derived from the projection already produced by
     * price(); no provenance, pricing-policy or money service is called again.
     *
     * Length-prefixed tokens avoid delimiter collisions in configurable rule
     * codes and other string identities.
     */
    private static String projectionFingerprint(
            SettlementMoneyProjection value
    ) {
        StringBuilder canonical =
                new StringBuilder();

        token(
                canonical,
                "DUTYLOG_SETTLEMENT_PRICING_V1"
        );

        token(
                canonical,
                value.settlementId()
        );
        token(
                canonical,
                value.settlementDate()
        );
        token(
                canonical,
                value.currencyCode()
        );
        token(
                canonical,
                value.minutes()
        );
        token(
                canonical,
                value.baseAmountMinor()
        );
        token(
                canonical,
                value.premiumAmountMinor()
        );
        token(
                canonical,
                value.totalAmountMinor()
        );

        List<SourceValuation> orderedSources =
                new ArrayList<>(
                        value.sources()
                );

        orderedSources.sort(
                Comparator
                        .comparing(
                                SourceValuation::allocationId,
                                Comparator.nullsLast(
                                        Long::compareTo
                                )
                        )
                        .thenComparing(
                                SourceValuation::creditId,
                                Comparator.nullsLast(
                                        Long::compareTo
                                )
                        )
                        .thenComparing(
                                SourceValuation::sourceActualWorkIntervalId
                        )
                        .thenComparing(
                                SourceValuation::sourceDate
                        )
                        .thenComparingInt(
                                SourceValuation::overtimeCreditOffsetStartMinutes
                        )
                        .thenComparingInt(
                                SourceValuation::factualWorkedOrdinalStartMinutes
                        )
                        .thenComparingInt(
                                SourceValuation::minutes
                        )
                        .thenComparing(
                                SourceValuation::pricingEffectiveFrom
                        )
        );

        token(
                canonical,
                orderedSources.size()
        );

        for (SourceValuation source :
                orderedSources) {

            token(
                    canonical,
                    "SOURCE"
            );

            token(
                    canonical,
                    source.allocationId()
            );
            token(
                    canonical,
                    source.creditId()
            );
            token(
                    canonical,
                    source.sourceActualWorkIntervalId()
            );
            token(
                    canonical,
                    source.sourceDate()
            );
            token(
                    canonical,
                    source.minutes()
            );
            token(
                    canonical,
                    source.overtimeCreditOffsetStartMinutes()
            );
            token(
                    canonical,
                    source.factualWorkedOrdinalStartMinutes()
            );
            token(
                    canonical,
                    source.pricingEffectiveFrom()
            );
            token(
                    canonical,
                    source.compensationSourceMonth()
            );
            token(
                    canonical,
                    source.compensationEffectiveFrom()
            );
            token(
                    canonical,
                    source.payMode()
            );
            token(
                    canonical,
                    source.currencyCode()
            );
            token(
                    canonical,
                    source.baseHourlyRateMinor()
            );
            token(
                    canonical,
                    source.productionNormMinutes()
            );
            token(
                    canonical,
                    source.night()
            );
            token(
                    canonical,
                    source.holiday()
            );

            /*
             * Pricing slices are ordered sub-ranges of this consumed source.
             * Components inside one slice are a set, so canonicalize their
             * order by code + bps.
             */
            long pricingOffset =
                    source.overtimeCreditOffsetStartMinutes();

            token(
                    canonical,
                    source.pricingSlices()
                            .size()
            );

            for (PayPricingEngine.PricingSlice slice :
                    source.pricingSlices()) {

                token(
                        canonical,
                        "SLICE"
                );
                token(
                        canonical,
                        pricingOffset
                );
                token(
                        canonical,
                        slice.minutes()
                );

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

                    token(
                            canonical,
                            component.code()
                    );
                    token(
                            canonical,
                            component.premiumBps()
                    );
                }

                pricingOffset =
                        Math.addExact(
                                pricingOffset,
                                slice.minutes()
                        );
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

            token(
                    canonical,
                    "RATE_BUCKET"
            );
            token(
                    canonical,
                    bucket.baseHourlyRateMinor()
            );
            token(
                    canonical,
                    bucket.minutes()
            );
            token(
                    canonical,
                    bucket.baseAmountMinor()
            );
            token(
                    canonical,
                    bucket.premiumAmountMinor()
            );
            token(
                    canonical,
                    bucket.totalAmountMinor()
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

                token(
                        canonical,
                        premium.code()
                );
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

        String text =
                String.valueOf(
                        value
                );

        target
                .append(
                        text.length()
                )
                .append(
                        ':'
                )
                .append(
                        text
                )
                .append(
                        '|'
                );
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

    /**
     * Explainability identity of one factual source piece.
     *
     * No individual rounded amount is attached here intentionally:
     * source-piece boundaries are not monetary rounding boundaries.
     */
    public record SourceValuation(
            Long allocationId,
            Long creditId,
            Long sourceActualWorkIntervalId,
            LocalDate sourceDate,
            int minutes,
            int overtimeCreditOffsetStartMinutes,
            int factualWorkedOrdinalStartMinutes,
            LocalDate pricingEffectiveFrom,
            YearMonth compensationSourceMonth,
            LocalDate compensationEffectiveFrom,
            String payMode,
            String currencyCode,
            long baseHourlyRateMinor,
            Integer productionNormMinutes,
            boolean night,
            boolean holiday,
            List<PricingSlice> pricingSlices
    ) {
        /**
         * Source-compatible constructor for callers created before the
         * deep pricing fingerprint retained source-level resolved slices.
         */
        public SourceValuation(
                Long allocationId,
                Long creditId,
                Long sourceActualWorkIntervalId,
                LocalDate sourceDate,
                int minutes,
                int overtimeCreditOffsetStartMinutes,
                int factualWorkedOrdinalStartMinutes,
                LocalDate pricingEffectiveFrom,
                YearMonth compensationSourceMonth,
                LocalDate compensationEffectiveFrom,
                String payMode,
                String currencyCode,
                long baseHourlyRateMinor,
                Integer productionNormMinutes
        ) {
            this(
                    allocationId,
                    creditId,
                    sourceActualWorkIntervalId,
                    sourceDate,
                    minutes,
                    overtimeCreditOffsetStartMinutes,
                    factualWorkedOrdinalStartMinutes,
                    pricingEffectiveFrom,
                    compensationSourceMonth,
                    compensationEffectiveFrom,
                    payMode,
                    currencyCode,
                    baseHourlyRateMinor,
                    productionNormMinutes,
                    false,
                    false,
                    List.of(
                            new PricingSlice(
                                    minutes,
                                    List.of()
                            )
                    )
            );
        }

        public SourceValuation {
            if (sourceActualWorkIntervalId == null
                    || sourceDate == null
                    || minutes <= 0
                    || overtimeCreditOffsetStartMinutes < 0
                    || factualWorkedOrdinalStartMinutes < 0
                    || pricingEffectiveFrom == null
                    || compensationSourceMonth == null
                    || compensationEffectiveFrom == null
                    || payMode == null
                    || currencyCode == null
                    || baseHourlyRateMinor <= 0) {
                throw new IllegalArgumentException(
                        "Settlement source valuation identity is incomplete"
                );
            }

            if (!compensationSourceMonth.equals(
                    YearMonth.from(
                            sourceDate
                    )
            )) {
                throw new IllegalArgumentException(
                        "Compensation source month disagrees with factual source date"
                );
            }

            if (pricingEffectiveFrom.isAfter(
                    sourceDate
            )) {
                throw new IllegalArgumentException(
                        "Pricing term cannot become effective after factual source date"
                );
            }

            if (compensationEffectiveFrom.isAfter(
                    compensationSourceMonth.atDay(1)
            )) {
                throw new IllegalArgumentException(
                        "Compensation term cannot become effective after source month"
                );
            }

            pricingSlices =
                    pricingSlices == null
                            ? List.of()
                            : List.copyOf(
                                    pricingSlices
                            );

            int pricedMinutes =
                    pricingSlices
                            .stream()
                            .mapToInt(
                                    PricingSlice::minutes
                            )
                            .sum();

            if (pricedMinutes
                    != minutes) {
                throw new IllegalArgumentException(
                        "Source valuation pricing slices must preserve source minutes"
                );
            }
        }
    }

    public record PricedRateBucket(
            long baseHourlyRateMinor,
            int minutes,
            long baseAmountMinor,
            long premiumAmountMinor,
            long totalAmountMinor,
            List<PricedPremium> premiums
    ) {
        public PricedRateBucket {
            if (baseHourlyRateMinor <= 0
                    || minutes <= 0
                    || baseAmountMinor < 0
                    || premiumAmountMinor < 0
                    || totalAmountMinor < 0) {
                throw new IllegalArgumentException(
                        "Invalid settlement pricing rate bucket"
                );
            }

            if (Math.addExact(
                    baseAmountMinor,
                    premiumAmountMinor
            ) != totalAmountMinor) {
                throw new IllegalArgumentException(
                        "Rate-bucket money components do not sum"
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

    public record SettlementMoneyProjection(
            Long settlementId,
            LocalDate settlementDate,
            String currencyCode,
            int minutes,
            long baseAmountMinor,
            long premiumAmountMinor,
            long totalAmountMinor,
            List<PricedRateBucket> rateBuckets,
            List<SourceValuation> sources
    ) {
        public SettlementMoneyProjection {
            if (settlementId == null
                    || settlementDate == null
                    || currencyCode == null
                    || currencyCode.isBlank()
                    || minutes <= 0
                    || baseAmountMinor < 0
                    || premiumAmountMinor < 0
                    || totalAmountMinor < 0) {
                throw new IllegalArgumentException(
                        "Invalid settlement money projection"
                );
            }

            if (Math.addExact(
                    baseAmountMinor,
                    premiumAmountMinor
            ) != totalAmountMinor) {
                throw new IllegalArgumentException(
                        "Settlement money components do not sum"
                );
            }

            rateBuckets =
                    rateBuckets == null
                            ? List.of()
                            : List.copyOf(
                                    rateBuckets
                            );

            sources =
                    sources == null
                            ? List.of()
                            : List.copyOf(
                                    sources
                            );

            int bucketMinutes =
                    rateBuckets.stream()
                            .mapToInt(
                                    PricedRateBucket::minutes
                            )
                            .sum();

            int sourceMinutes =
                    sources.stream()
                            .mapToInt(
                                    SourceValuation::minutes
                            )
                            .sum();

            if (bucketMinutes != minutes
                    || sourceMinutes != minutes) {
                throw new IllegalArgumentException(
                        "Settlement money projection must preserve all minutes"
                );
            }
        }

        /**
         * Complete deterministic pricing/source identity for Payroll snapshot
         * provenance. Computing it performs no additional pricing lookup.
         */
        public String pricingFingerprint() {
            return projectionFingerprint(
                    this
            );
        }
    }

}
