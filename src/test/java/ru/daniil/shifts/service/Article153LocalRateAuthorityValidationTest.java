package ru.daniil.shifts.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.Article153LocalRateAuthority;
import ru.daniil.shifts.model.PayPricingTerm;
import ru.daniil.shifts.repo.Article153LocalRateAuthorityRepository;
import ru.daniil.shifts.repo.PayPricingTermRepository;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class Article153LocalRateAuthorityValidationTest {
    private PayPricingTermRepository terms;
    private Article153LocalRateAuthorityRepository authorities;
    private Article153LocalRateAuthorityService service;
    private AppUser user;
    private AtomicLong ids;

    @BeforeEach
    void setUp() {
        terms = mock(PayPricingTermRepository.class);
        authorities = mock(Article153LocalRateAuthorityRepository.class);
        service = new Article153LocalRateAuthorityService(
                terms,
                authorities,
                new PayPricingRuleResolver()
        );
        user = mock(AppUser.class);
        ids = new AtomicLong(500L);

        when(authorities.saveAndFlush(any(Article153LocalRateAuthority.class)))
                .thenAnswer(invocation -> {
                    Article153LocalRateAuthority row = invocation.getArgument(0);
                    if (row.getId() == null) {
                        set(row, "id", ids.incrementAndGet());
                    }
                    return row;
                });
    }

    @Test
    void constructorRequiresAllCollaborators() {
        assertThrows(NullPointerException.class, () ->
                new Article153LocalRateAuthorityService(null, authorities, new PayPricingRuleResolver()));
        assertThrows(NullPointerException.class, () ->
                new Article153LocalRateAuthorityService(terms, null, new PayPricingRuleResolver()));
        assertThrows(NullPointerException.class, () ->
                new Article153LocalRateAuthorityService(terms, authorities, null));
    }

    @Test
    void certifyRejectsNullAndBlankInputs() {
        LocalDate date = LocalDate.of(2026, 1, 1);
        assertThrows(NullPointerException.class, () ->
                service.certify(null, date,
                        Article153LocalRateAuthorityService.SourceKind.LOCAL_NORMATIVE_ACT,
                        "ref", "rev"));
        assertThrows(NullPointerException.class, () ->
                service.certify(user, null,
                        Article153LocalRateAuthorityService.SourceKind.LOCAL_NORMATIVE_ACT,
                        "ref", "rev"));
        assertThrows(NullPointerException.class, () ->
                service.certify(user, date, null, "ref", "rev"));
        assertThrows(IllegalArgumentException.class, () ->
                service.certify(user, date,
                        Article153LocalRateAuthorityService.SourceKind.LOCAL_NORMATIVE_ACT,
                        null, "rev"));
        assertThrows(IllegalArgumentException.class, () ->
                service.certify(user, date,
                        Article153LocalRateAuthorityService.SourceKind.LOCAL_NORMATIVE_ACT,
                        "   ", "rev"));
        assertThrows(IllegalArgumentException.class, () ->
                service.certify(user, date,
                        Article153LocalRateAuthorityService.SourceKind.LOCAL_NORMATIVE_ACT,
                        "ref", null));
        assertThrows(IllegalArgumentException.class, () ->
                service.certify(user, date,
                        Article153LocalRateAuthorityService.SourceKind.LOCAL_NORMATIVE_ACT,
                        "ref", "   "));
    }

    @Test
    void resolveRejectsNullUserAndDate() {
        LocalDate date = LocalDate.of(2026, 1, 1);
        assertThrows(NullPointerException.class, () -> service.resolve(null, date));
        assertThrows(NullPointerException.class, () -> service.resolve(user, null));
    }

    @Test
    void certifyMissingEffectiveTermFailsClosed() {
        LocalDate date = LocalDate.of(2026, 2, 1);
        when(terms.findByOwnerAndEffectiveFrom(user, date)).thenReturn(Optional.empty());

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                service.certify(
                        user,
                        date,
                        Article153LocalRateAuthorityService.SourceKind.EMPLOYMENT_CONTRACT,
                        "contract",
                        "r1"
                ));

        assertTrue(ex.getMessage().startsWith(
                Article153LocalRateAuthorityService.PAY_PRICING_RULES_REQUIRED));
    }

    @Test
    void authorityFactRejectsEveryIncompleteIdentityShape() {
        LocalDate date = LocalDate.of(2026, 1, 1);
        Instant at = Instant.parse("2026-01-01T00:00:00Z");
        String sha = "a".repeat(64);
        var kind = Article153LocalRateAuthorityService.SourceKind.LOCAL_NORMATIVE_ACT;

        assertThrows(IllegalArgumentException.class, () ->
                new Article153LocalRateAuthorityService.AuthorityFact(0, date, kind, "ref", "rev", sha, at));
        assertThrows(IllegalArgumentException.class, () ->
                new Article153LocalRateAuthorityService.AuthorityFact(1, null, kind, "ref", "rev", sha, at));
        assertThrows(IllegalArgumentException.class, () ->
                new Article153LocalRateAuthorityService.AuthorityFact(1, date, null, "ref", "rev", sha, at));
        assertThrows(IllegalArgumentException.class, () ->
                new Article153LocalRateAuthorityService.AuthorityFact(1, date, kind, null, "rev", sha, at));
        assertThrows(IllegalArgumentException.class, () ->
                new Article153LocalRateAuthorityService.AuthorityFact(1, date, kind, " ", "rev", sha, at));
        assertThrows(IllegalArgumentException.class, () ->
                new Article153LocalRateAuthorityService.AuthorityFact(1, date, kind, "ref", null, sha, at));
        assertThrows(IllegalArgumentException.class, () ->
                new Article153LocalRateAuthorityService.AuthorityFact(1, date, kind, "ref", " ", sha, at));
        assertThrows(IllegalArgumentException.class, () ->
                new Article153LocalRateAuthorityService.AuthorityFact(1, date, kind, "ref", "rev", null, at));
        assertThrows(IllegalArgumentException.class, () ->
                new Article153LocalRateAuthorityService.AuthorityFact(1, date, kind, "ref", "rev", "bad", at));
        assertThrows(IllegalArgumentException.class, () ->
                new Article153LocalRateAuthorityService.AuthorityFact(1, date, kind, "ref", "rev", sha, null));
    }

    @Test
    void resolutionRejectsInvalidReadyShapes() {
        LocalDate date = LocalDate.of(2026, 1, 1);
        String sha = "b".repeat(64);
        var fact = validFact();

        assertThrows(IllegalArgumentException.class, () ->
                new Article153LocalRateAuthorityService.Resolution(
                        date, true, Article153LocalRateAuthorityService.State.STATUTORY_ONLY,
                        date, sha, -1, null, null));
        assertThrows(IllegalArgumentException.class, () ->
                new Article153LocalRateAuthorityService.Resolution(
                        date, true, Article153LocalRateAuthorityService.State.BLOCKED,
                        date, sha, 0, null, null));
        assertThrows(IllegalArgumentException.class, () ->
                new Article153LocalRateAuthorityService.Resolution(
                        date, true, Article153LocalRateAuthorityService.State.STATUTORY_ONLY,
                        null, sha, 0, null, null));
        assertThrows(IllegalArgumentException.class, () ->
                new Article153LocalRateAuthorityService.Resolution(
                        date, true, Article153LocalRateAuthorityService.State.STATUTORY_ONLY,
                        date, null, 0, null, null));
        assertThrows(IllegalArgumentException.class, () ->
                new Article153LocalRateAuthorityService.Resolution(
                        date, true, Article153LocalRateAuthorityService.State.STATUTORY_ONLY,
                        date, "bad", 0, null, null));
        assertThrows(IllegalArgumentException.class, () ->
                new Article153LocalRateAuthorityService.Resolution(
                        date, true, Article153LocalRateAuthorityService.State.STATUTORY_ONLY,
                        date, sha, 0, null, "blocked"));
        assertThrows(IllegalArgumentException.class, () ->
                new Article153LocalRateAuthorityService.Resolution(
                        date, true, Article153LocalRateAuthorityService.State.STATUTORY_ONLY,
                        date, sha, 1, null, null));
        assertThrows(IllegalArgumentException.class, () ->
                new Article153LocalRateAuthorityService.Resolution(
                        date, true, Article153LocalRateAuthorityService.State.STATUTORY_ONLY,
                        date, sha, 0, fact, null));
        assertThrows(IllegalArgumentException.class, () ->
                new Article153LocalRateAuthorityService.Resolution(
                        date, true, Article153LocalRateAuthorityService.State.SOURCE_BACKED_LOCAL_RATE,
                        date, sha, 1, null, null));
    }

    @Test
    void resolutionRejectsInvalidBlockedShapesAndBlankReason() {
        LocalDate date = LocalDate.of(2026, 1, 1);
        String sha = "c".repeat(64);
        var fact = validFact();

        assertThrows(IllegalArgumentException.class, () ->
                new Article153LocalRateAuthorityService.Resolution(
                        date, false, Article153LocalRateAuthorityService.State.STATUTORY_ONLY,
                        null, null, 0, null, "reason"));
        assertThrows(IllegalArgumentException.class, () ->
                new Article153LocalRateAuthorityService.Resolution(
                        date, false, Article153LocalRateAuthorityService.State.BLOCKED,
                        null, null, 0, null, null));
        assertThrows(IllegalArgumentException.class, () ->
                new Article153LocalRateAuthorityService.Resolution(
                        date, false, Article153LocalRateAuthorityService.State.BLOCKED,
                        date, null, 0, null, "reason"));
        assertThrows(IllegalArgumentException.class, () ->
                new Article153LocalRateAuthorityService.Resolution(
                        date, false, Article153LocalRateAuthorityService.State.BLOCKED,
                        null, sha, 0, null, "reason"));
        assertThrows(IllegalArgumentException.class, () ->
                new Article153LocalRateAuthorityService.Resolution(
                        date, false, Article153LocalRateAuthorityService.State.BLOCKED,
                        null, null, 0, fact, "reason"));
        assertThrows(IllegalArgumentException.class, () ->
                Article153LocalRateAuthorityService.Resolution.blocked(date, "   "));
    }

    @Test
    void persistedAuthorityIdentityIsValidatedBeforeUse() {
        LocalDate effective = LocalDate.of(2026, 1, 1);
        LocalDate sourceDate = LocalDate.of(2026, 6, 12);
        PayPricingTerm term = term(effective, 90L);
        term.addRule("holiday", "HOLIDAY", 10_000, 0, null, null);
        when(terms.findFirstByOwnerAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(user, sourceDate))
                .thenReturn(Optional.of(term));

        String[] fields = {
                "id", "owner", "pricingTerm", "sourceKind",
                "sourceReference", "sourceRevision",
                "holidayPolicyFingerprint", "certifiedAt"
        };
        Object[] badValues = {0L, null, null, null, null, null, null, null};

        for (int i = 0; i < fields.length; i++) {
            Article153LocalRateAuthority row = validRow(term, "d".repeat(64));
            set(row, fields[i], badValues[i]);
            when(authorities.findByOwnerAndPricingTerm(user, term))
                    .thenReturn(Optional.of(row));

            assertThrows(IllegalStateException.class, () -> service.resolve(user, sourceDate),
                    "expected invalid persisted field to fail: " + fields[i]);
        }
    }

    private Article153LocalRateAuthorityService.AuthorityFact validFact() {
        return new Article153LocalRateAuthorityService.AuthorityFact(
                7L,
                LocalDate.of(2026, 1, 1),
                Article153LocalRateAuthorityService.SourceKind.LOCAL_NORMATIVE_ACT,
                "ref",
                "rev",
                "f".repeat(64),
                Instant.parse("2026-01-01T00:00:00Z")
        );
    }

    private Article153LocalRateAuthority validRow(PayPricingTerm term, String fingerprint) {
        Article153LocalRateAuthority row = new Article153LocalRateAuthority(
                user,
                term,
                Article153LocalRateAuthority.SOURCE_LOCAL_NORMATIVE_ACT,
                "ref",
                "rev",
                fingerprint,
                Instant.parse("2026-01-01T00:00:00Z")
        );
        set(row, "id", ids.incrementAndGet());
        return row;
    }

    private PayPricingTerm term(LocalDate effectiveFrom, long id) {
        PayPricingTerm term = new PayPricingTerm(user, effectiveFrom);
        set(term, "id", id);
        return term;
    }

    private static void set(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }
}
