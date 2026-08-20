package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.PayPricingRule;
import ru.daniil.shifts.model.PayPricingTerm;
import ru.daniil.shifts.repo.PayPricingTermRepository;
import ru.daniil.shifts.service.PayPricingEngine.PricingSlice;
import ru.daniil.shifts.service.PayPricingRuleResolver.ConsumedSlice;
import ru.daniil.shifts.service.PayPricingRuleResolver.Dimension;
import ru.daniil.shifts.service.PayPricingRuleResolver.Rule;
import ru.daniil.shifts.service.PayPricingRuleResolver.RuleSet;
import ru.daniil.shifts.service.exception.ApiException;

import java.time.LocalDate;
import java.util.List;

/**
 * Effective-dated pricing-policy binding.
 *
 * This service owns:
 *   source valuation date
 *     -> effective persisted PayPricingTerm
 *     -> pure RuleSet
 *     -> resolved economic PricingSlices.
 *
 * It does NOT:
 * - read Actual Work or overtime allocations;
 * - decide which factual minutes were consumed;
 * - calculate money;
 * - mutate Settlement / Time Bank;
 * - assemble Payroll.
 *
 * v27.46.1 cash-settlement valuation policy is SOURCE_WORK_DATE.
 * The caller supplies that historical source date explicitly.
 */
@Service
public class PayPricingPolicyService {

    private final PayPricingTermRepository terms;
    private final PayPricingRuleResolver resolver;

    public PayPricingPolicyService(
            PayPricingTermRepository terms,
            PayPricingRuleResolver resolver
    ) {
        this.terms = terms;
        this.resolver = resolver;
    }

    @Transactional(readOnly = true)
    public ResolvedPricingPolicy resolveForSourceDate(
            AppUser user,
            LocalDate sourceDate,
            List<ConsumedSlice> consumed
    ) {
        if (user == null) {
            throw new IllegalArgumentException(
                    "Pricing policy requires user"
            );
        }

        if (sourceDate == null) {
            throw new IllegalArgumentException(
                    "Pricing policy requires source date"
            );
        }

        PayPricingTerm term =
                terms
                        .findFirstByOwnerAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                                user,
                                sourceDate
                        )
                        .orElseThrow(() ->
                                ApiException.conflict(
                                        "PAY_PRICING_RULES_REQUIRED",
                                        "Для даты "
                                                + sourceDate
                                                + " не настроены правила оплаты"
                                )
                        );

        RuleSet ruleSet =
                new RuleSet(
                        term.getRules()
                                .stream()
                                .map(this::toRule)
                                .toList()
                );

        List<PricingSlice> pricingSlices =
                resolver.resolve(
                        ruleSet,
                        consumed
                );

        return new ResolvedPricingPolicy(
                sourceDate,
                term.getEffectiveFrom(),
                ruleSet,
                pricingSlices
        );
    }

    private Rule toRule(
            PayPricingRule stored
    ) {
        if (stored == null) {
            throw new IllegalStateException(
                    "Persisted pricing term contains null rule"
            );
        }

        final Dimension dimension;

        try {
            dimension =
                    Dimension.valueOf(
                            stored.getDimension()
                    );
        } catch (
                IllegalArgumentException
                | NullPointerException ex
        ) {
            throw new IllegalStateException(
                    "Persisted pricing rule has unsupported dimension: "
                            + stored.getDimension(),
                    ex
            );
        }

        return new Rule(
                stored.getCode(),
                dimension,
                stored.getPremiumBps(),
                stored.getFromMinute(),
                stored.getToMinuteExclusive(),
                stored.getExclusiveGroup()
        );
    }

    public record ResolvedPricingPolicy(
            LocalDate sourceDate,
            LocalDate effectiveFrom,
            RuleSet rules,
            List<PricingSlice> pricingSlices
    ) {
        public ResolvedPricingPolicy {
            if (sourceDate == null
                    || effectiveFrom == null
                    || rules == null) {
                throw new IllegalArgumentException(
                        "Resolved pricing policy identity is incomplete"
                );
            }

            if (effectiveFrom.isAfter(
                    sourceDate
            )) {
                throw new IllegalArgumentException(
                        "Pricing term cannot become effective after source date"
                );
            }

            pricingSlices =
                    pricingSlices == null
                            ? List.of()
                            : List.copyOf(
                                    pricingSlices
                            );
        }
    }
}
