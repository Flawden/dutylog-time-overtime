package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.Article153LocalRateAuthority;
import ru.daniil.shifts.model.PayPricingRule;
import ru.daniil.shifts.model.PayPricingTerm;
import ru.daniil.shifts.repo.Article153LocalRateAuthorityRepository;
import ru.daniil.shifts.repo.PayPricingTermRepository;
import ru.daniil.shifts.service.PayPricingRuleResolver.ConsumedSlice;
import ru.daniil.shifts.service.PayPricingRuleResolver.Dimension;
import ru.daniil.shifts.service.PayPricingRuleResolver.Rule;
import ru.daniil.shifts.service.PayPricingRuleResolver.RuleSet;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Machine-owned Article 153 authority that binds an existing country-neutral
 * HOLIDAY pricing rule set to its legal source.
 *
 * <p>No second pricing engine is created. The existing PayPricingTerm and
 * PayPricingRuleResolver remain the economic rule machinery. This service
 * only decides whether the effective HOLIDAY rule set is legally source-backed
 * and returns its additive premium BPS. The statutory floor remains owned by
 * Article153EconomicLegalPolicy and must dominate later final pricing.</p>
 */
@Service
public class Article153LocalRateAuthorityService {
    public static final String PAY_PRICING_RULES_REQUIRED =
            "ARTICLE153_PAY_PRICING_RULES_REQUIRED";
    public static final String SOURCE_AUTHORITY_REQUIRED =
            "ARTICLE153_LOCAL_RATE_SOURCE_AUTHORITY_REQUIRED";
    public static final String SOURCE_POLICY_CHANGED =
            "ARTICLE153_LOCAL_RATE_SOURCE_POLICY_CHANGED";
    public static final String HOLIDAY_RULE_REQUIRED =
            "ARTICLE153_LOCAL_RATE_HOLIDAY_RULE_REQUIRED";
    public static final String AUTHORITY_ALREADY_CERTIFIED_IMMUTABLE =
            "ARTICLE153_LOCAL_RATE_AUTHORITY_ALREADY_CERTIFIED_IMMUTABLE";

    private final PayPricingTermRepository terms;
    private final Article153LocalRateAuthorityRepository authorities;
    private final PayPricingRuleResolver resolver;

    public Article153LocalRateAuthorityService(
            PayPricingTermRepository terms,
            Article153LocalRateAuthorityRepository authorities,
            PayPricingRuleResolver resolver
    ) {
        this.terms = Objects.requireNonNull(terms);
        this.authorities = Objects.requireNonNull(authorities);
        this.resolver = Objects.requireNonNull(resolver);
    }

    @Transactional
    public AuthorityFact certify(
            AppUser user,
            LocalDate effectiveFrom,
            SourceKind sourceKind,
            String sourceReference,
            String sourceRevision
    ) {
        requireUserAndDate(user, effectiveFrom);
        Objects.requireNonNull(sourceKind, "Article 153 local rate source kind is required");
        String reference = requireText(sourceReference, "Article 153 local rate source reference is required");
        String revision = requireText(sourceRevision, "Article 153 local rate source revision is required");

        PayPricingTerm term = terms.findByOwnerAndEffectiveFrom(user, effectiveFrom)
                .orElseThrow(() -> new IllegalStateException(
                        PAY_PRICING_RULES_REQUIRED + ":" + effectiveFrom
                ));

        List<PayPricingRule> holidayRules = holidayRules(term);
        if (holidayRules.isEmpty()) {
            throw new IllegalStateException(HOLIDAY_RULE_REQUIRED + ":" + effectiveFrom);
        }

        String fingerprint = fingerprint(term, holidayRules);
        var existing = authorities.findByOwnerAndPricingTerm(user, term);
        if (existing.isPresent()) {
            Article153LocalRateAuthority row = existing.get();
            validatePersisted(row);
            boolean same = row.getSourceKind().equals(sourceKind.name())
                    && row.getSourceReference().equals(reference)
                    && row.getSourceRevision().equals(revision)
                    && row.getHolidayPolicyFingerprint().equals(fingerprint);
            if (!same) {
                throw new IllegalStateException(
                        AUTHORITY_ALREADY_CERTIFIED_IMMUTABLE + ":" + effectiveFrom
                );
            }
            return fact(row);
        }

        Article153LocalRateAuthority saved = authorities.saveAndFlush(
                new Article153LocalRateAuthority(
                        user,
                        term,
                        sourceKind.name(),
                        reference,
                        revision,
                        fingerprint,
                        Instant.now()
                )
        );
        validatePersisted(saved);
        return fact(saved);
    }

    @Transactional(readOnly = true)
    public Resolution resolve(
            AppUser user,
            LocalDate sourceDate
    ) {
        requireUserAndDate(user, sourceDate);

        PayPricingTerm term = terms
                .findFirstByOwnerAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                        user,
                        sourceDate
                )
                .orElse(null);

        if (term == null) {
            return Resolution.blocked(
                    sourceDate,
                    PAY_PRICING_RULES_REQUIRED + ":" + sourceDate
            );
        }

        List<PayPricingRule> holidayRules = holidayRules(term);
        String fingerprint = fingerprint(term, holidayRules);

        if (holidayRules.isEmpty()) {
            return new Resolution(
                    sourceDate,
                    true,
                    State.STATUTORY_ONLY,
                    term.getEffectiveFrom(),
                    fingerprint,
                    0,
                    null,
                    null
            );
        }

        Article153LocalRateAuthority authority =
                authorities.findByOwnerAndPricingTerm(user, term)
                        .orElse(null);

        if (authority == null) {
            return Resolution.blocked(
                    sourceDate,
                    SOURCE_AUTHORITY_REQUIRED + ":" + term.getEffectiveFrom()
            );
        }

        validatePersisted(authority);
        if (!authority.getHolidayPolicyFingerprint().equals(fingerprint)) {
            return Resolution.blocked(
                    sourceDate,
                    SOURCE_POLICY_CHANGED + ":" + term.getEffectiveFrom()
            );
        }

        int configuredPremiumBps =
                resolvedHolidayPremiumBps(holidayRules);

        return new Resolution(
                sourceDate,
                true,
                State.SOURCE_BACKED_LOCAL_RATE,
                term.getEffectiveFrom(),
                fingerprint,
                configuredPremiumBps,
                fact(authority),
                null
        );
    }

    private int resolvedHolidayPremiumBps(
            List<PayPricingRule> holidayRules
    ) {
        RuleSet ruleSet = new RuleSet(
                holidayRules.stream()
                        .map(this::toRule)
                        .toList()
        );

        var slices = resolver.resolve(
                ruleSet,
                List.of(
                        new ConsumedSlice(
                                1,
                                false,
                                true,
                                false,
                                0
                        )
                )
        );

        if (slices.size() != 1 || slices.get(0).minutes() != 1) {
            throw new IllegalStateException(
                    "Article 153 HOLIDAY pricing must resolve one deterministic probe minute"
            );
        }

        long sum = 0L;
        for (var component : slices.get(0).components()) {
            sum = Math.addExact(sum, component.premiumBps());
        }
        if (sum > Integer.MAX_VALUE) {
            throw new IllegalStateException("Article 153 local premium exceeds integer range");
        }
        return (int) sum;
    }

    private List<PayPricingRule> holidayRules(
            PayPricingTerm term
    ) {
        if (term == null || term.getEffectiveFrom() == null) {
            throw new IllegalStateException("Article 153 pricing term identity is incomplete");
        }
        return term.getRules().stream()
                .filter(Objects::nonNull)
                .filter(rule -> "HOLIDAY".equals(rule.getDimension()))
                .toList();
    }

    private String fingerprint(
            PayPricingTerm term,
            List<PayPricingRule> holidayRules
    ) {
        StringBuilder canonical = new StringBuilder()
                .append("ARTICLE153_LOCAL_RATE_V1")
                .append('|')
                .append(term.getEffectiveFrom());

        for (PayPricingRule rule : holidayRules.stream()
                .sorted(java.util.Comparator.comparing(PayPricingRule::getCode))
                .toList()) {
            canonical.append("||")
                    .append(rule.getCode()).append('|')
                    .append(rule.getDimension()).append('|')
                    .append(rule.getPremiumBps()).append('|')
                    .append(rule.getFromMinute()).append('|')
                    .append(rule.getToMinuteExclusive() == null ? "-" : rule.getToMinuteExclusive()).append('|')
                    .append(rule.getExclusiveGroup() == null ? "-" : rule.getExclusiveGroup());
        }

        return sha256(canonical.toString());
    }

    private Rule toRule(PayPricingRule stored) {
        return new Rule(
                stored.getCode(),
                Dimension.HOLIDAY,
                stored.getPremiumBps(),
                stored.getFromMinute(),
                stored.getToMinuteExclusive(),
                stored.getExclusiveGroup()
        );
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(64);
            for (byte b : bytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private void validatePersisted(Article153LocalRateAuthority row) {
        if (row == null
                || row.getId() == null
                || row.getId() <= 0L
                || row.getOwner() == null
                || row.getPricingTerm() == null
                || row.getPricingTerm().getEffectiveFrom() == null
                || row.getSourceKind() == null
                || row.getSourceReference() == null
                || row.getSourceRevision() == null
                || row.getHolidayPolicyFingerprint() == null
                || !row.getHolidayPolicyFingerprint().matches("[0-9a-f]{64}")
                || row.getCertifiedAt() == null) {
            throw new IllegalStateException(
                    "Persisted Article 153 local rate authority lacks immutable identity"
            );
        }
    }

    private AuthorityFact fact(Article153LocalRateAuthority row) {
        return new AuthorityFact(
                row.getId(),
                row.getPricingTerm().getEffectiveFrom(),
                SourceKind.valueOf(row.getSourceKind()),
                row.getSourceReference(),
                row.getSourceRevision(),
                row.getHolidayPolicyFingerprint(),
                row.getCertifiedAt()
        );
    }

    private void requireUserAndDate(AppUser user, LocalDate date) {
        Objects.requireNonNull(user, "Article 153 local rate requires user");
        Objects.requireNonNull(date, "Article 153 local rate requires date");
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    public enum SourceKind {
        COLLECTIVE_AGREEMENT,
        LOCAL_NORMATIVE_ACT,
        EMPLOYMENT_CONTRACT
    }

    public enum State {
        STATUTORY_ONLY,
        SOURCE_BACKED_LOCAL_RATE,
        BLOCKED
    }

    public record AuthorityFact(
            long authorityId,
            LocalDate pricingEffectiveFrom,
            SourceKind sourceKind,
            String sourceReference,
            String sourceRevision,
            String holidayPolicyFingerprint,
            Instant certifiedAt
    ) {
        public AuthorityFact {
            if (authorityId <= 0L
                    || pricingEffectiveFrom == null
                    || sourceKind == null
                    || sourceReference == null
                    || sourceReference.isBlank()
                    || sourceRevision == null
                    || sourceRevision.isBlank()
                    || holidayPolicyFingerprint == null
                    || !holidayPolicyFingerprint.matches("[0-9a-f]{64}")
                    || certifiedAt == null) {
                throw new IllegalArgumentException(
                        "Article 153 local rate authority fact is incomplete"
                );
            }
        }
    }

    public record Resolution(
            LocalDate sourceDate,
            boolean ready,
            State state,
            LocalDate pricingEffectiveFrom,
            String holidayPolicyFingerprint,
            int configuredAdditionalTariffBps,
            AuthorityFact authority,
            String blockingReason
    ) {
        public Resolution {
            Objects.requireNonNull(sourceDate);
            Objects.requireNonNull(state);
            if (configuredAdditionalTariffBps < 0) {
                throw new IllegalArgumentException("Article 153 configured premium cannot be negative");
            }
            if (ready) {
                if (state == State.BLOCKED
                        || pricingEffectiveFrom == null
                        || holidayPolicyFingerprint == null
                        || !holidayPolicyFingerprint.matches("[0-9a-f]{64}")
                        || blockingReason != null) {
                    throw new IllegalArgumentException("Ready Article 153 local rate resolution is invalid");
                }
                if (state == State.STATUTORY_ONLY
                        && (configuredAdditionalTariffBps != 0 || authority != null)) {
                    throw new IllegalArgumentException("Statutory-only Article 153 resolution cannot carry local rate");
                }
                if (state == State.SOURCE_BACKED_LOCAL_RATE && authority == null) {
                    throw new IllegalArgumentException("Source-backed Article 153 resolution requires authority");
                }
            } else if (state != State.BLOCKED
                    || blockingReason == null
                    || pricingEffectiveFrom != null
                    || holidayPolicyFingerprint != null
                    || authority != null) {
                throw new IllegalArgumentException("Blocked Article 153 local rate resolution is invalid");
            }
        }

        static Resolution blocked(LocalDate sourceDate, String reason) {
            return new Resolution(
                    sourceDate,
                    false,
                    State.BLOCKED,
                    null,
                    null,
                    0,
                    null,
                    requireReason(reason)
            );
        }

        private static String requireReason(String value) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("Article 153 blocker reason is required");
            }
            return value;
        }
    }
}
