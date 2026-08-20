package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import ru.daniil.shifts.service.PayPricingEngine.PremiumComponent;
import ru.daniil.shifts.service.PayPricingEngine.PricingSlice;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure resolver from factual pay dimensions into explicit pricing
 * components.
 *
 * The input explicitly says whether one factual slice is OVERTIME.
 * NIGHT and HOLIDAY may overlap both ordinary and overtime work.
 *
 * It does not:
 * - load effective-dated rules;
 * - read repositories;
 * - calculate money;
 * - know Payroll;
 * - mutate Time Bank;
 * - encode country labor law.
 *
 * Rule configuration decides which dimensions stack. Components sharing one
 * exclusive group compete; the highest configured premium wins inside that
 * group. Components without an exclusive group stack independently.
 */
@Service
public class PayPricingRuleResolver {

    public List<PricingSlice> resolve(
            RuleSet rules,
            List<ConsumedSlice> consumed
    ) {
        if (rules == null) {
            throw new IllegalArgumentException(
                    "Pricing rule set is required"
            );
        }

        if (consumed == null || consumed.isEmpty()) {
            return List.of();
        }

        List<PricingSlice> result =
                new ArrayList<>();

        for (ConsumedSlice source : consumed) {
            if (source == null) {
                throw new IllegalArgumentException(
                        "Consumed pricing slice cannot be null"
                );
            }

            /*
             * Overtime tier boundaries can cut one provenance piece.
             * NIGHT/HOLIDAY do not need special splitting because their flags
             * are already homogeneous in stored provenance.
             */
            int cursor = 0;

            while (cursor < source.minutes()) {
                int overtimeOffset =
                        Math.addExact(
                                source.overtimeOffsetStartMinutes(),
                                cursor
                        );

                int nextBoundary =
                        source.minutes();

                for (Rule rule : rules.rules()) {
                    if (!source.overtime()
                            || rule.dimension()
                            != Dimension.OVERTIME) {
                        continue;
                    }

                    if (rule.fromMinute() > overtimeOffset) {
                        nextBoundary =
                                Math.min(
                                        nextBoundary,
                                        rule.fromMinute()
                                                - source.overtimeOffsetStartMinutes()
                                );
                    }

                    if (rule.toMinuteExclusive() != null
                            && rule.toMinuteExclusive() > overtimeOffset) {
                        nextBoundary =
                                Math.min(
                                        nextBoundary,
                                        rule.toMinuteExclusive()
                                                - source.overtimeOffsetStartMinutes()
                                );
                    }
                }

                if (nextBoundary <= cursor) {
                    nextBoundary = cursor + 1;
                }

                int segmentMinutes =
                        nextBoundary - cursor;

                List<PremiumComponent> components =
                        resolveComponents(
                                rules,
                                source,
                                overtimeOffset
                        );

                appendMerged(
                        result,
                        new PricingSlice(
                                segmentMinutes,
                                components
                        )
                );

                cursor = nextBoundary;
            }
        }

        return List.copyOf(result);
    }

    private List<PremiumComponent> resolveComponents(
            RuleSet rules,
            ConsumedSlice source,
            int overtimeOffset
    ) {
        List<Rule> applicable =
                rules.rules()
                        .stream()
                        .filter(rule ->
                                applies(
                                        rule,
                                        source,
                                        overtimeOffset
                                )
                        )
                        .toList();

        List<Rule> stackable =
                applicable.stream()
                        .filter(rule ->
                                rule.exclusiveGroup() == null
                        )
                        .toList();

        Map<String, Rule> exclusiveWinners =
                new LinkedHashMap<>();

        for (Rule rule : applicable) {
            if (rule.exclusiveGroup() == null) {
                continue;
            }

            exclusiveWinners.merge(
                    rule.exclusiveGroup(),
                    rule,
                    this::higherPremium
            );
        }

        List<PremiumComponent> components =
                new ArrayList<>();

        for (Rule rule : stackable) {
            components.add(
                    component(rule)
            );
        }

        for (Rule rule :
                exclusiveWinners.values()) {
            components.add(
                    component(rule)
            );
        }

        components.sort(
                Comparator.comparing(
                        PremiumComponent::code
                )
        );

        return List.copyOf(components);
    }

    private Rule higherPremium(
            Rule left,
            Rule right
    ) {
        if (right.premiumBps()
                > left.premiumBps()) {
            return right;
        }

        if (right.premiumBps()
                < left.premiumBps()) {
            return left;
        }

        /*
         * Stable deterministic tie-break so persisted rule row order cannot
         * change payroll money/hash.
         */
        return right.code()
                .compareTo(left.code()) < 0
                ? right
                : left;
    }

    private boolean applies(
            Rule rule,
            ConsumedSlice source,
            int overtimeOffset
    ) {
        return switch (rule.dimension()) {
            case NIGHT ->
                    source.night();

            case HOLIDAY ->
                    source.holiday();

            case OVERTIME ->
                    source.overtime()
                            && overtimeOffset
                            >= rule.fromMinute()
                            && (
                            rule.toMinuteExclusive() == null
                                    || overtimeOffset
                                    < rule.toMinuteExclusive()
                    );
        };
    }

    private PremiumComponent component(
            Rule rule
    ) {
        return new PremiumComponent(
                rule.code(),
                rule.premiumBps()
        );
    }

    private void appendMerged(
            List<PricingSlice> target,
            PricingSlice next
    ) {
        if (target.isEmpty()) {
            target.add(next);
            return;
        }

        PricingSlice previous =
                target.get(
                        target.size() - 1
                );

        if (!previous.components()
                .equals(next.components())) {
            target.add(next);
            return;
        }

        target.set(
                target.size() - 1,
                new PricingSlice(
                        Math.addExact(
                                previous.minutes(),
                                next.minutes()
                        ),
                        previous.components()
                )
        );
    }

    public enum Dimension {
        NIGHT,
        HOLIDAY,
        OVERTIME
    }

    public record Rule(
            String code,
            Dimension dimension,
            int premiumBps,
            int fromMinute,
            Integer toMinuteExclusive,
            String exclusiveGroup
    ) {
        public Rule {
            code =
                    code == null
                            ? ""
                            : code.trim();

            if (code.isEmpty()) {
                throw new IllegalArgumentException(
                        "Pricing rule code is required"
                );
            }

            if (dimension == null) {
                throw new IllegalArgumentException(
                        "Pricing rule dimension is required"
                );
            }

            if (premiumBps < 0) {
                throw new IllegalArgumentException(
                        "Pricing premium cannot be negative"
                );
            }

            if (dimension == Dimension.OVERTIME) {
                if (fromMinute < 0) {
                    throw new IllegalArgumentException(
                            "Overtime tier start cannot be negative"
                    );
                }

                if (toMinuteExclusive != null
                        && toMinuteExclusive <= fromMinute) {
                    throw new IllegalArgumentException(
                            "Overtime tier end must be after start"
                    );
                }
            } else if (fromMinute != 0
                    || toMinuteExclusive != null) {
                throw new IllegalArgumentException(
                        "Only OVERTIME rules may define minute ranges"
                );
            }

            exclusiveGroup =
                    exclusiveGroup == null
                            || exclusiveGroup.isBlank()
                            ? null
                            : exclusiveGroup.trim();
        }
    }

    public record RuleSet(
            List<Rule> rules
    ) {
        public RuleSet {
            rules =
                    rules == null
                            ? List.of()
                            : List.copyOf(rules);

            if (rules.stream()
                    .anyMatch(item -> item == null)) {
                throw new IllegalArgumentException(
                        "Pricing rule set cannot contain null"
                );
            }
        }
    }

    /**
     * Homogeneous factual slice projected into pricing vocabulary.
     *
     * overtime=false:
     * ordinary factual work. NIGHT/HOLIDAY rules may apply, OVERTIME rules
     * and tier boundaries must not.
     *
     * overtime=true:
     * consumed canonical overtime provenance. overtimeOffsetStartMinutes is
     * the offset inside the canonical derived overtime credit; zero means the
     * first overtime minute of that source day.
     */
    public record ConsumedSlice(
            int minutes,
            boolean night,
            boolean holiday,
            boolean overtime,
            int overtimeOffsetStartMinutes
    ) {
        /**
         * Compatibility constructor for the existing explicit-settlement
         * pipeline. Before ordinary-work pricing existed, every ConsumedSlice
         * was necessarily consumed overtime provenance.
         */
        public ConsumedSlice(
                int minutes,
                boolean night,
                boolean holiday,
                int overtimeOffsetStartMinutes
        ) {
            this(
                    minutes,
                    night,
                    holiday,
                    true,
                    overtimeOffsetStartMinutes
            );
        }

        public ConsumedSlice {
            if (minutes <= 0) {
                throw new IllegalArgumentException(
                        "Consumed pricing slice must contain positive minutes"
                );
            }

            if (overtimeOffsetStartMinutes < 0) {
                throw new IllegalArgumentException(
                        "Overtime offset cannot be negative"
                );
            }

            if (!overtime
                    && overtimeOffsetStartMinutes != 0) {
                throw new IllegalArgumentException(
                        "Ordinary pricing slice cannot carry overtime offset"
                );
            }
        }
    }
}
