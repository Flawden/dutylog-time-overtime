package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Pure monetary kernel for already-resolved pay pricing.
 *
 * It deliberately does NOT know:
 * - Actual Work;
 * - NIGHT/HOLIDAY/OVERTIME classification semantics;
 * - Time Bank / FIFO;
 * - settlement ownership;
 * - effective-date rule lookup;
 * - country/company policy;
 * - Payroll persistence.
 *
 * Callers resolve those concerns first and supply explicit premium components.
 *
 * Basis points:
 *   10_000 = 1.00x
 *    5_000 = +0.50x premium
 *    2_000 = +0.20x premium
 *
 * The ordinary 1.00x base is implicit and always priced exactly once.
 */
@Service
public class PayPricingEngine {

    public static final int BASE_BPS = 10_000;

    public PricingResult price(
            long baseHourlyRateMinor,
            List<PricingSlice> slices
    ) {
        if (baseHourlyRateMinor <= 0) {
            throw new IllegalArgumentException(
                    "Pricing requires positive base hourly rate"
            );
        }

        List<PricingSlice> safeSlices =
                slices == null
                        ? List.of()
                        : List.copyOf(slices);

        int totalMinutes = 0;

        /*
         * Premiums are aggregated by economic identity before rounding.
         *
         * This makes money invariant to arbitrary factual/provenance
         * segmentation. Splitting one 60-minute source into two 30-minute
         * slices must not create an extra minor currency unit merely because
         * rounding happened twice.
         */
        Map<ComponentKey, Integer> premiumMinutes =
                new LinkedHashMap<>();

        for (PricingSlice slice : safeSlices) {
            if (slice == null) {
                throw new IllegalArgumentException(
                        "Pricing slice cannot be null"
                );
            }

            totalMinutes =
                    Math.addExact(
                            totalMinutes,
                            slice.minutes()
                    );

            Set<ComponentKey> seenInSlice =
                    new LinkedHashSet<>();

            for (PremiumComponent component :
                    slice.components()) {

                ComponentKey key =
                        new ComponentKey(
                                component.code(),
                                component.premiumBps()
                        );

                if (!seenInSlice.add(key)) {
                    throw new IllegalArgumentException(
                            "Duplicate premium component inside one pricing slice: "
                                    + component.code()
                    );
                }

                premiumMinutes.merge(
                        key,
                        slice.minutes(),
                        Math::addExact
                );
            }
        }

        long baseAmountMinor =
                moneyForBps(
                        baseHourlyRateMinor,
                        totalMinutes,
                        BASE_BPS
                );

        List<PricedPremium> premiums =
                new ArrayList<>();

        long premiumAmountMinor = 0L;

        List<Map.Entry<ComponentKey, Integer>> ordered =
                premiumMinutes.entrySet()
                        .stream()
                        .sorted(
                                Comparator
                                        .comparing(
                                                (Map.Entry<ComponentKey, Integer> item) ->
                                                        item.getKey().code()
                                        )
                                        .thenComparingInt(
                                                item ->
                                                        item.getKey().premiumBps()
                                        )
                        )
                        .toList();

        for (Map.Entry<ComponentKey, Integer> entry :
                ordered) {

            ComponentKey key =
                    entry.getKey();

            int minutes =
                    entry.getValue();

            long amount =
                    moneyForBps(
                            baseHourlyRateMinor,
                            minutes,
                            key.premiumBps()
                    );

            premiumAmountMinor =
                    Math.addExact(
                            premiumAmountMinor,
                            amount
                    );

            premiums.add(
                    new PricedPremium(
                            key.code(),
                            key.premiumBps(),
                            minutes,
                            amount
                    )
            );
        }

        long totalAmountMinor =
                Math.addExact(
                        baseAmountMinor,
                        premiumAmountMinor
                );

        return new PricingResult(
                totalMinutes,
                baseHourlyRateMinor,
                baseAmountMinor,
                premiumAmountMinor,
                totalAmountMinor,
                List.copyOf(premiums)
        );
    }

    private long moneyForBps(
            long hourlyRateMinor,
            int minutes,
            int rateBps
    ) {
        if (minutes < 0) {
            throw new IllegalArgumentException(
                    "Pricing minutes cannot be negative"
            );
        }

        if (rateBps < 0) {
            throw new IllegalArgumentException(
                    "Pricing basis points cannot be negative"
            );
        }

        try {
            return BigDecimal
                    .valueOf(hourlyRateMinor)
                    .multiply(
                            BigDecimal.valueOf(minutes)
                    )
                    .multiply(
                            BigDecimal.valueOf(rateBps)
                    )
                    .divide(
                            BigDecimal.valueOf(
                                    60L * BASE_BPS
                            ),
                            0,
                            RoundingMode.HALF_UP
                    )
                    .longValueExact();

        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException(
                    "Pricing amount overflow",
                    ex
            );
        }
    }

    public record PremiumComponent(
            String code,
            int premiumBps
    ) {
        public PremiumComponent {
            String clean =
                    code == null
                            ? ""
                            : code.trim();

            if (clean.isEmpty()) {
                throw new IllegalArgumentException(
                        "Premium component code is required"
                );
            }

            if (premiumBps < 0) {
                throw new IllegalArgumentException(
                        "Premium basis points cannot be negative"
                );
            }

            code = clean;
        }
    }

    public record PricingSlice(
            int minutes,
            List<PremiumComponent> components
    ) {
        public PricingSlice {
            if (minutes <= 0) {
                throw new IllegalArgumentException(
                        "Pricing slice must contain positive minutes"
                );
            }

            components =
                    components == null
                            ? List.of()
                            : List.copyOf(components);

            if (components.stream()
                    .anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException(
                        "Pricing components cannot contain null"
                );
            }
        }
    }

    public record PricedPremium(
            String code,
            int premiumBps,
            int minutes,
            long amountMinor
    ) {}

    public record PricingResult(
            int totalMinutes,
            long baseHourlyRateMinor,
            long baseAmountMinor,
            long premiumAmountMinor,
            long totalAmountMinor,
            List<PricedPremium> premiums
    ) {}

    private record ComponentKey(
            String code,
            int premiumBps
    ) {}
}
