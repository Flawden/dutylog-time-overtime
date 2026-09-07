package ru.daniil.shifts.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.Article153LocalRateAuthority;
import ru.daniil.shifts.model.PayPricingTerm;
import ru.daniil.shifts.repo.Article153LocalRateAuthorityRepository;
import ru.daniil.shifts.repo.PayPricingTermRepository;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class Article153LocalRateAuthorityServiceTest {
    private PayPricingTermRepository terms;
    private Article153LocalRateAuthorityRepository authorities;
    private Article153LocalRateAuthorityService service;
    private AppUser user;
    private AtomicLong ids;
    private AtomicReference<Article153LocalRateAuthority> lastSaved;

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
        ids = new AtomicLong(200L);
        lastSaved = new AtomicReference<>();

        when(authorities.saveAndFlush(any(Article153LocalRateAuthority.class)))
                .thenAnswer(invocation -> {
                    Article153LocalRateAuthority row = invocation.getArgument(0);
                    if (row.getId() == null) {
                        setId(row, ids.incrementAndGet());
                    }
                    lastSaved.set(row);
                    return row;
                });
    }

    @Test
    void missingPricingTermFailsClosed() {
        LocalDate date = LocalDate.of(2026, 5, 9);
        when(terms.findFirstByOwnerAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(user, date))
                .thenReturn(Optional.empty());

        var result = service.resolve(user, date);

        assertFalse(result.ready());
        assertEquals(Article153LocalRateAuthorityService.State.BLOCKED, result.state());
        assertTrue(result.blockingReason().startsWith(
                Article153LocalRateAuthorityService.PAY_PRICING_RULES_REQUIRED));
    }

    @Test
    void explicitTermWithoutHolidayRuleMeansStatutoryOnly() {
        LocalDate effective = LocalDate.of(2026, 1, 1);
        LocalDate date = LocalDate.of(2026, 5, 9);
        PayPricingTerm term = term(effective, 10L);
        term.addRule("night", "NIGHT", 2_000, 0, null, null);
        when(terms.findFirstByOwnerAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(user, date))
                .thenReturn(Optional.of(term));

        var result = service.resolve(user, date);

        assertTrue(result.ready());
        assertEquals(Article153LocalRateAuthorityService.State.STATUTORY_ONLY, result.state());
        assertEquals(0, result.configuredAdditionalTariffBps());
        assertNull(result.authority());
        verifyNoInteractions(authorities);
    }

    @Test
    void holidayRuleWithoutLegalSourceBlocks() {
        LocalDate effective = LocalDate.of(2026, 1, 1);
        LocalDate date = LocalDate.of(2026, 5, 9);
        PayPricingTerm term = term(effective, 11L);
        term.addRule("holiday", "HOLIDAY", 15_000, 0, null, null);
        when(terms.findFirstByOwnerAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(user, date))
                .thenReturn(Optional.of(term));
        when(authorities.findByOwnerAndPricingTerm(user, term))
                .thenReturn(Optional.empty());

        var result = service.resolve(user, date);

        assertFalse(result.ready());
        assertTrue(result.blockingReason().startsWith(
                Article153LocalRateAuthorityService.SOURCE_AUTHORITY_REQUIRED));
    }

    @Test
    void certifyRequiresExistingHolidayRule() {
        LocalDate effective = LocalDate.of(2026, 1, 1);
        PayPricingTerm term = term(effective, 12L);
        when(terms.findByOwnerAndEffectiveFrom(user, effective))
                .thenReturn(Optional.of(term));

        var ex = assertThrows(IllegalStateException.class, () ->
                service.certify(
                        user,
                        effective,
                        Article153LocalRateAuthorityService.SourceKind.LOCAL_NORMATIVE_ACT,
                        "LNA-42",
                        "rev-1"
                ));

        assertTrue(ex.getMessage().startsWith(
                Article153LocalRateAuthorityService.HOLIDAY_RULE_REQUIRED));
    }

    @Test
    void certifyPersistsLegalSourceAndRuleFingerprint() {
        LocalDate effective = LocalDate.of(2026, 2, 1);
        PayPricingTerm term = term(effective, 13L);
        term.addRule("holiday", "HOLIDAY", 15_000, 0, null, null);
        when(terms.findByOwnerAndEffectiveFrom(user, effective))
                .thenReturn(Optional.of(term));
        when(authorities.findByOwnerAndPricingTerm(user, term))
                .thenReturn(Optional.empty());

        var fact = service.certify(
                user,
                effective,
                Article153LocalRateAuthorityService.SourceKind.COLLECTIVE_AGREEMENT,
                "COLLECTIVE-2026",
                "signed-2026-01-15"
        );

        assertTrue(fact.authorityId() > 0);
        assertEquals(effective, fact.pricingEffectiveFrom());
        assertEquals(
                Article153LocalRateAuthorityService.SourceKind.COLLECTIVE_AGREEMENT,
                fact.sourceKind()
        );
        assertEquals("COLLECTIVE-2026", fact.sourceReference());
        assertTrue(fact.holidayPolicyFingerprint().matches("[0-9a-f]{64}"));
        verify(authorities).saveAndFlush(any(Article153LocalRateAuthority.class));
    }

    @Test
    void repeatedCertificationIsIdempotentWhenExactIdentityMatches() {
        LocalDate effective = LocalDate.of(2026, 3, 1);
        PayPricingTerm term = term(effective, 14L);
        term.addRule("holiday", "HOLIDAY", 12_000, 0, null, null);
        when(terms.findByOwnerAndEffectiveFrom(user, effective))
                .thenReturn(Optional.of(term));
        when(authorities.findByOwnerAndPricingTerm(user, term))
                .thenReturn(Optional.empty());

        var first = service.certify(
                user,
                effective,
                Article153LocalRateAuthorityService.SourceKind.EMPLOYMENT_CONTRACT,
                "CONTRACT-7",
                "2026-02-20"
        );

        Article153LocalRateAuthority persisted = lastSaved.get();
        when(authorities.findByOwnerAndPricingTerm(user, term))
                .thenReturn(Optional.of(persisted));

        var second = service.certify(
                user,
                effective,
                Article153LocalRateAuthorityService.SourceKind.EMPLOYMENT_CONTRACT,
                "CONTRACT-7",
                "2026-02-20"
        );

        assertEquals(first.authorityId(), second.authorityId());
        verify(authorities, times(1)).saveAndFlush(any(Article153LocalRateAuthority.class));
    }

    @Test
    void differentRecertificationCannotRewriteLegalHistory() {
        LocalDate effective = LocalDate.of(2026, 4, 1);
        PayPricingTerm term = term(effective, 15L);
        term.addRule("holiday", "HOLIDAY", 12_000, 0, null, null);
        when(terms.findByOwnerAndEffectiveFrom(user, effective))
                .thenReturn(Optional.of(term));
        when(authorities.findByOwnerAndPricingTerm(user, term))
                .thenReturn(Optional.empty());

        service.certify(
                user,
                effective,
                Article153LocalRateAuthorityService.SourceKind.LOCAL_NORMATIVE_ACT,
                "LNA-1",
                "r1"
        );
        Article153LocalRateAuthority persisted = lastSaved.get();
        when(authorities.findByOwnerAndPricingTerm(user, term))
                .thenReturn(Optional.of(persisted));

        var ex = assertThrows(IllegalStateException.class, () ->
                service.certify(
                        user,
                        effective,
                        Article153LocalRateAuthorityService.SourceKind.LOCAL_NORMATIVE_ACT,
                        "LNA-2",
                        "r2"
                ));

        assertTrue(ex.getMessage().startsWith(
                Article153LocalRateAuthorityService.AUTHORITY_ALREADY_CERTIFIED_IMMUTABLE));
    }

    @Test
    void certifiedHolidayRuleResolvesConfiguredAdditionalTariff() {
        LocalDate effective = LocalDate.of(2026, 1, 1);
        LocalDate date = LocalDate.of(2026, 6, 12);
        PayPricingTerm term = term(effective, 16L);
        term.addRule("holiday", "HOLIDAY", 15_000, 0, null, null);
        certifyAndStub(term, effective,
                Article153LocalRateAuthorityService.SourceKind.LOCAL_NORMATIVE_ACT);

        when(terms.findFirstByOwnerAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(user, date))
                .thenReturn(Optional.of(term));

        var result = service.resolve(user, date);

        assertTrue(result.ready());
        assertEquals(Article153LocalRateAuthorityService.State.SOURCE_BACKED_LOCAL_RATE, result.state());
        assertEquals(15_000, result.configuredAdditionalTariffBps());
        assertNotNull(result.authority());
    }

    @Test
    void exclusiveHolidayRulesUseHigherConfiguredPremium() {
        LocalDate effective = LocalDate.of(2026, 1, 1);
        LocalDate date = LocalDate.of(2026, 7, 1);
        PayPricingTerm term = term(effective, 17L);
        term.addRule("holiday-a", "HOLIDAY", 11_000, 0, null, "holiday-rate");
        term.addRule("holiday-b", "HOLIDAY", 18_000, 0, null, "holiday-rate");
        certifyAndStub(term, effective,
                Article153LocalRateAuthorityService.SourceKind.COLLECTIVE_AGREEMENT);
        when(terms.findFirstByOwnerAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(user, date))
                .thenReturn(Optional.of(term));

        var result = service.resolve(user, date);

        assertTrue(result.ready());
        assertEquals(18_000, result.configuredAdditionalTariffBps());
    }

    @Test
    void stackableHolidayRulesRemainAdditiveLikeExistingPricingEngine() {
        LocalDate effective = LocalDate.of(2026, 1, 1);
        LocalDate date = LocalDate.of(2026, 8, 1);
        PayPricingTerm term = term(effective, 18L);
        term.addRule("holiday-a", "HOLIDAY", 10_000, 0, null, null);
        term.addRule("holiday-b", "HOLIDAY", 2_500, 0, null, null);
        certifyAndStub(term, effective,
                Article153LocalRateAuthorityService.SourceKind.EMPLOYMENT_CONTRACT);
        when(terms.findFirstByOwnerAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(user, date))
                .thenReturn(Optional.of(term));

        var result = service.resolve(user, date);

        assertTrue(result.ready());
        assertEquals(12_500, result.configuredAdditionalTariffBps());
    }

    @Test
    void mutationOfCertifiedHolidayRulesFailsClosed() {
        LocalDate effective = LocalDate.of(2026, 1, 1);
        LocalDate date = LocalDate.of(2026, 9, 1);
        PayPricingTerm term = term(effective, 19L);
        term.addRule("holiday", "HOLIDAY", 10_000, 0, null, null);
        certifyAndStub(term, effective,
                Article153LocalRateAuthorityService.SourceKind.LOCAL_NORMATIVE_ACT);

        term.clearRules();
        term.addRule("holiday", "HOLIDAY", 20_000, 0, null, null);
        when(terms.findFirstByOwnerAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(user, date))
                .thenReturn(Optional.of(term));

        var result = service.resolve(user, date);

        assertFalse(result.ready());
        assertTrue(result.blockingReason().startsWith(
                Article153LocalRateAuthorityService.SOURCE_POLICY_CHANGED));
    }

    private void certifyAndStub(
            PayPricingTerm term,
            LocalDate effective,
            Article153LocalRateAuthorityService.SourceKind kind
    ) {
        when(terms.findByOwnerAndEffectiveFrom(user, effective))
                .thenReturn(Optional.of(term));
        when(authorities.findByOwnerAndPricingTerm(user, term))
                .thenReturn(Optional.empty());

        service.certify(user, effective, kind, "SOURCE-REF", "SOURCE-REV");
        Article153LocalRateAuthority persisted = lastSaved.get();
        when(authorities.findByOwnerAndPricingTerm(user, term))
                .thenReturn(Optional.of(persisted));
    }

    private PayPricingTerm term(LocalDate effectiveFrom, long id) {
        PayPricingTerm term = new PayPricingTerm(user, effectiveFrom);
        setId(term, id);
        return term;
    }

    private static void setId(Object target, long id) {
        try {
            Field field = target.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(target, id);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }
}
