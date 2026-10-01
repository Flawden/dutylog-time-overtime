package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.model.*;
import ru.daniil.shifts.model.Article153ComponentAuthority.Decision;
import ru.daniil.shifts.model.Article153ComponentAuthority.SourceKind;
import ru.daniil.shifts.repo.Article153ComponentAuthorityRepository;
import ru.daniil.shifts.repo.CompensationComponentVersionRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

/**
 * P1B3C2: complete classification of the configured effective component set.
 * This is an authority gate, not a monetary calculator or evidence that the
 * employer has entered every real-world component. Source review remains explicit.
 */
@Service
public class Article153ComponentAuthorityService {
    public static final String SOURCE_REQUIRED = "ARTICLE153_COMPONENT_SOURCE_REQUIRED";
    public static final String UNCLASSIFIED = "ARTICLE153_COMPONENT_UNCLASSIFIED";
    public static final String POLICY_CHANGED = "ARTICLE153_COMPONENT_POLICY_CHANGED";
    public static final String IMMUTABLE = "ARTICLE153_COMPONENT_AUTHORITY_IMMUTABLE";
    public static final String VERSION_REQUIRED = "ARTICLE153_COMPONENT_VERSION_REQUIRED";
    public static final String INVALID_IDENTITY = "ARTICLE153_COMPONENT_INVALID_IDENTITY";
    public static final String FINGERPRINT_SCHEMA = "article153-component-v1";

    private final CompensationComponentVersionRepository versions;
    private final CompensationComponentResolverService resolver;
    private final Article153ComponentAuthorityRepository authorities;

    public Article153ComponentAuthorityService(CompensationComponentVersionRepository versions,
            CompensationComponentResolverService resolver, Article153ComponentAuthorityRepository authorities) {
        this.versions = Objects.requireNonNull(versions);
        this.resolver = Objects.requireNonNull(resolver);
        this.authorities = Objects.requireNonNull(authorities);
    }

    /** Source review must cover the entire effective version, including any EXCLUDE decision. */
    @Transactional
    public AuthorityFact certify(AppUser user, Long versionId, Decision decision, SourceKind kind,
            String reference, String revision, String basis) {
        requireUser(user);
        requireId(versionId);
        CompensationComponentVersion version = versions.findOwnedForArticle153Certification(user, versionId)
                .orElseThrow(() -> new IllegalStateException(VERSION_REQUIRED));
        validateIdentity(user, version);
        if (!versionId.equals(version.getId()) || !version.isEnabled()) {
            throw new IllegalStateException(VERSION_REQUIRED);
        }
        Article153ComponentAuthority candidate = new Article153ComponentAuthority(version, decision, kind,
                reference, revision, basis, fingerprint(version), Instant.now());
        var previous = authorities.findByComponentVersion(version);
        if (previous.isPresent()) {
            Article153ComponentAuthority row = previous.get();
            validateRow(row, version);
            if (row.getDecision() != candidate.getDecision()
                    || row.getSourceKind() != candidate.getSourceKind()
                    || !row.getSourceReference().equals(candidate.getSourceReference())
                    || !row.getSourceRevision().equals(candidate.getSourceRevision())
                    || !row.getClassificationBasis().equals(candidate.getClassificationBasis())
                    || !row.getComponentFingerprint().equals(candidate.getComponentFingerprint())) {
                throw new IllegalStateException(IMMUTABLE);
            }
            return fact(row, version);
        }
        Article153ComponentAuthority row = authorities.saveAndFlush(candidate);
        validateRow(row, version);
        return fact(row, version);
    }

    /** No partial authority facts are returned when any component blocks. */
    @Transactional(readOnly = true)
    public Resolution resolve(AppUser user, LocalDate sourceDate) {
        requireUser(user);
        Objects.requireNonNull(sourceDate, "Source work date is required");
        List<AuthorityFact> facts = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (CompensationComponentVersion version : resolver.resolve(user, YearMonth.from(sourceDate))) {
            validateIdentity(user, version);
            if (version.getEffectiveFrom().isAfter(sourceDate) || !seen.add(version.getComponent().getId())) {
                throw new IllegalStateException(INVALID_IDENTITY);
            }
            var existing = authorities.findByComponentVersion(version);
            if (existing.isEmpty()) {
                if (version.isEnabled()) return Resolution.blocked(sourceDate, SOURCE_REQUIRED, version.getId());
                continue;
            }
            Article153ComponentAuthority row = existing.get();
            validateRow(row, version);
            // A certified enabled version may not be silently edited into a disabled version.
            if (!row.getComponentFingerprint().equals(fingerprint(version))) {
                return Resolution.blocked(sourceDate, POLICY_CHANGED, version.getId());
            }
            if (!version.isEnabled()) continue;
            if (row.getDecision() == Decision.UNCLASSIFIED) {
                return Resolution.blocked(sourceDate, UNCLASSIFIED, version.getId());
            }
            facts.add(fact(row, version));
        }
        facts.sort(Comparator.comparing(AuthorityFact::componentId));
        return new Resolution(sourceDate, true, List.copyOf(facts), null, null);
    }

    private static void requireUser(AppUser user) {
        Objects.requireNonNull(user, "Worker is required");
        requireId(user.getId());
    }

    private static void requireId(Long id) {
        if (id == null || id <= 0) throw new IllegalArgumentException(INVALID_IDENTITY);
    }

    private static void validateIdentity(AppUser user, CompensationComponentVersion version) {
        Objects.requireNonNull(version, "Component version is required");
        requireId(version.getId());
        CompensationComponent component = Objects.requireNonNull(version.getComponent());
        requireId(component.getId());
        AppUser owner = Objects.requireNonNull(component.getOwner());
        if (!user.getId().equals(owner.getId()) || version.getEffectiveFrom() == null
                || version.getEffectiveFrom().getDayOfMonth() != 1) {
            throw new IllegalStateException(INVALID_IDENTITY);
        }
    }

    private static void validateRow(Article153ComponentAuthority row, CompensationComponentVersion version) {
        row.validate();
        requireId(row.getId());
        if (!version.getId().equals(row.getComponentVersion().getId())) {
            throw new IllegalStateException(INVALID_IDENTITY);
        }
    }

    public record Formula(PayrollEarningKind earningKind,
            CompensationComponentVersion.CalculationType calculationType,
            CompensationComponentVersion.CalculationBase calculationBase,
            Integer rateBps, Long amountMinor, String currencyCode, boolean enabled) {}

    private static Formula formula(CompensationComponentVersion version) {
        return new Formula(version.getEarningKind(), version.getCalculationType(), version.getCalculationBase(),
                version.getRateBps(), version.getAmountMinor(), version.getCurrencyCode(), version.isEnabled());
    }

    /** Fixed typed tuple; no display name or mutable audit time participates. Null has an explicit marker. */
    public static String fingerprint(CompensationComponentVersion version) {
        AppUser owner = version.getComponent().getOwner();
        requireUser(owner);
        validateIdentity(owner, version);
        Formula formula = formula(version);
        String canonical = String.join("\n", FINGERPRINT_SCHEMA,
                owner.getId().toString(), version.getComponent().getId().toString(), version.getId().toString(),
                version.getEffectiveFrom().toString(), Objects.toString(formula.earningKind(), "<null>"),
                Objects.toString(formula.calculationType(), "<null>"),
                Objects.toString(formula.calculationBase(), "<null>"),
                Objects.toString(formula.rateBps(), "<null>"), Objects.toString(formula.amountMinor(), "<null>"),
                Objects.toString(formula.currencyCode(), "<null>"), Boolean.toString(formula.enabled()));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required", e);
        }
    }

    private static AuthorityFact fact(Article153ComponentAuthority row, CompensationComponentVersion version) {
        return new AuthorityFact(row.getId(), version.getComponent().getOwner().getId(),
                version.getComponent().getId(), version.getId(), version.getEffectiveFrom(), row.getDecision(),
                row.getSourceKind(), row.getSourceReference(), row.getSourceRevision(), row.getClassificationBasis(),
                row.getComponentFingerprint(), row.getCertifiedAt(), formula(version));
    }

    public record AuthorityFact(Long authorityId, Long ownerId, Long componentId, Long versionId,
            LocalDate effectiveFrom, Decision decision, SourceKind sourceKind, String sourceReference,
            String sourceRevision, String classificationBasis, String componentFingerprint,
            Instant certifiedAt, Formula formula) {}

    public record Resolution(LocalDate sourceDate, boolean ready, List<AuthorityFact> components,
            String blockingReason, Long blockingVersionId) {
        public Resolution { components = List.copyOf(components); }
        private static Resolution blocked(LocalDate date, String reason, Long versionId) {
            return new Resolution(date, false, List.of(), reason, versionId);
        }
    }
}
