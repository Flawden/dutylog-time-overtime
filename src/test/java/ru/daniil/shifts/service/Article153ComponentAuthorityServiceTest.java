package ru.daniil.shifts.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.model.*;
import ru.daniil.shifts.model.Article153ComponentAuthority.Decision;
import ru.daniil.shifts.model.Article153ComponentAuthority.SourceKind;
import ru.daniil.shifts.repo.*;

import java.time.LocalDate;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static ru.daniil.shifts.model.CompensationComponentVersion.CalculationType.*;
import static ru.daniil.shifts.model.CompensationComponentVersion.CalculationBase.*;

@SpringBootTest
@Transactional
class Article153ComponentAuthorityServiceTest {
    @Autowired Article153ComponentAuthorityService service;
    @Autowired CompensationComponentRepository components;
    @Autowired CompensationComponentVersionRepository versions;
    @Autowired Article153ComponentAuthorityRepository authorities;
    @Autowired UserRepository users;
    @Autowired EntityManager em;
    AppUser owner;
    static final LocalDate JAN = LocalDate.of(2026, 1, 1);
    static final LocalDate MAY = LocalDate.of(2026, 5, 9);

    @BeforeEach void setup() { owner = users.saveAndFlush(new AppUser("c2-" + UUID.randomUUID(), "{noop}unused")); }

    CompensationComponentVersion fixed(String name) {
        return versions.saveAndFlush(new CompensationComponentVersion(
                components.saveAndFlush(new CompensationComponent(owner)), JAN, name,
                FIXED_AMOUNT, null, null, 50000L, "RUB", true));
    }
    Article153ComponentAuthorityService.AuthorityFact certify(CompensationComponentVersion v, Decision d) {
        return service.certify(owner, v.getId(), d, SourceKind.LOCAL_NORMATIVE_ACT,
                " LNA-42 §5 ", " revision-1 ", " Reviewed applicability for this entire effective version ");
    }
    void blocked(String reason) {
        var result = service.resolve(owner, MAY);
        assertFalse(result.ready()); assertEquals(reason, result.blockingReason());
        assertTrue(result.components().isEmpty()); assertNotNull(result.blockingVersionId());
    }

    @Test void emptyConfiguredSetDoesNotInventComponents() {
        var result = service.resolve(owner, MAY);
        assertTrue(result.ready()); assertTrue(result.components().isEmpty());
        assertNull(result.blockingReason()); assertNull(result.blockingVersionId());
    }
    @Test void nameNeverSuppliesMissingAuthority() {
        fixed("Районный коэффициент: обязательно включить");
        blocked(Article153ComponentAuthorityService.SOURCE_REQUIRED);
    }
    @ParameterizedTest @EnumSource(SourceKind.class)
    void allReviewedSourceKindsRoundTrip(SourceKind kind) {
        var v = fixed("Произвольное имя");
        var fact = service.certify(owner, v.getId(), Decision.INCLUDE, kind, "LNA", "rev", "clause 5");
        em.flush(); em.clear();
        var result = service.resolve(owner, MAY);
        assertTrue(result.ready()); assertEquals(fact, result.components().get(0));
        assertEquals(kind, fact.sourceKind()); assertEquals(owner.getId(), fact.ownerId());
        assertEquals(v.getId(), fact.versionId()); assertEquals(JAN, fact.effectiveFrom());
        assertEquals(50000L, fact.formula().amountMinor()); assertEquals("RUB", fact.formula().currencyCode());
        assertNotNull(fact.certifiedAt()); assertTrue(fact.componentFingerprint().matches("[0-9a-f]{64}"));
    }
    @Test void includeAndExcludeAreBothPreservedAsEvidence() {
        var a = fixed("Любое имя"); var b = fixed("Вредность");
        certify(a, Decision.INCLUDE); certify(b, Decision.EXCLUDE);
        var result = service.resolve(owner, MAY);
        assertTrue(result.ready()); assertEquals(2, result.components().size());
        assertEquals(Decision.INCLUDE, result.components().get(0).decision());
        assertEquals(Decision.EXCLUDE, result.components().get(1).decision());
        assertThrows(UnsupportedOperationException.class, () -> result.components().clear());
    }
    @Test void genericUnclassifiedKindCanOnlyBeResolvedByExplicitArticle153Review() {
        var v = fixed("Generic component"); assertNull(v.getEarningKind());
        certify(v, Decision.INCLUDE);
        assertTrue(service.resolve(owner, MAY).ready());
    }
    @Test void explicitUnclassifiedBlocksWithoutPartialFacts() {
        certify(fixed("A"), Decision.INCLUDE); certify(fixed("B"), Decision.UNCLASSIFIED);
        blocked(Article153ComponentAuthorityService.UNCLASSIFIED);
    }
    @Test void missingSecondComponentBlocksWithoutPartialFacts() {
        certify(fixed("A"), Decision.INCLUDE); fixed("B");
        blocked(Article153ComponentAuthorityService.SOURCE_REQUIRED);
    }
    @Test void newEffectiveVersionRequiresItsOwnReviewAndPreservesEarlierDate() {
        var v = fixed("A"); var old = certify(v, Decision.INCLUDE);
        versions.saveAndFlush(new CompensationComponentVersion(v.getComponent(), LocalDate.of(2026,5,1),
                "A", FIXED_AMOUNT, null, null, 60000L, "RUB", true));
        assertEquals(old, service.resolve(owner, LocalDate.of(2026,4,30)).components().get(0));
        blocked(Article153ComponentAuthorityService.SOURCE_REQUIRED);
    }
    @Test void disabledNewVersionEndsParticipationWithoutChangingEarlierAuthority() {
        var v = fixed("A"); certify(v, Decision.INCLUDE);
        versions.saveAndFlush(new CompensationComponentVersion(v.getComponent(), LocalDate.of(2026,5,1),
                "A", FIXED_AMOUNT, null, null, 50000L, "RUB", false));
        assertTrue(service.resolve(owner, MAY).components().isEmpty());
        assertEquals(1, service.resolve(owner, LocalDate.of(2026,4,30)).components().size());
    }
    @Test void futureVersionIsNotUsedForEarlierWork() {
        var v = fixed("A"); certify(v, Decision.INCLUDE);
        versions.saveAndFlush(new CompensationComponentVersion(v.getComponent(), LocalDate.of(2026,6,1),
                "A", FIXED_AMOUNT, null, null, 60000L, "RUB", true));
        assertTrue(service.resolve(owner, MAY).ready());
    }
    @Test void noEarlierVersionMeansNoInventedHistoricalComponent() {
        fixed("A"); assertTrue(service.resolve(owner, JAN.minusDays(1)).components().isEmpty());
    }
    @Test void repeatCertificationIsIdempotentAndTrimsSourceText() {
        var v = fixed("A"); var first = certify(v, Decision.INCLUDE);
        var second = certify(v, Decision.INCLUDE);
        assertEquals(first, second); assertEquals("LNA-42 §5", first.sourceReference());
        assertEquals("revision-1", first.sourceRevision());
        assertEquals(1, authorities.count());
    }
    @ParameterizedTest @ValueSource(strings={"decision","kind","reference","revision","basis","formula"})
    void recertificationCannotRewriteHistory(String field) {
        var v = fixed("A"); certify(v, Decision.INCLUDE);
        if (field.equals("formula")) v.update("A", FIXED_AMOUNT,null,null,60000L,"RUB",true);
        var ex = assertThrows(IllegalStateException.class, () -> service.certify(owner, v.getId(),
                field.equals("decision") ? Decision.EXCLUDE : Decision.INCLUDE,
                field.equals("kind") ? SourceKind.EMPLOYMENT_CONTRACT : SourceKind.LOCAL_NORMATIVE_ACT,
                field.equals("reference") ? "other" : "LNA-42 §5",
                field.equals("revision") ? "other" : "revision-1",
                field.equals("basis") ? "other" : "Reviewed applicability for this entire effective version"));
        assertEquals(Article153ComponentAuthorityService.IMMUTABLE, ex.getMessage());
    }
    @ParameterizedTest @ValueSource(strings={"amount","currency","enabled","type","kind"})
    void certifiedFixedFormulaDriftBlocks(String field) {
        var v = fixed("A"); certify(v, Decision.INCLUDE);
        switch(field) {
            case "amount" -> v.update("A",FIXED_AMOUNT,null,null,60000L,"RUB",true);
            case "currency" -> v.update("A",FIXED_AMOUNT,null,null,50000L,"EUR",true);
            case "enabled" -> v.update("A",FIXED_AMOUNT,null,null,50000L,"RUB",false);
            case "type" -> v.update("A",PERCENT_OF_BASE,EARNED_BASE_PAY,500,null,null,true);
            case "kind" -> v.updateEarningKind(PayrollEarningKind.HARMFUL_CONDITIONS);
        }
        blocked(Article153ComponentAuthorityService.POLICY_CHANGED);
    }
    @ParameterizedTest @ValueSource(strings={"rate","base"})
    void percentageFormulaDriftBlocks(String field) {
        var v = fixed("A"); v.update("A",PERCENT_OF_BASE,EARNED_BASE_PAY,500,null,null,true);
        certify(v, Decision.INCLUDE);
        v.update("A",PERCENT_OF_BASE,field.equals("base") ? NOMINAL_SALARY : EARNED_BASE_PAY,
                field.equals("rate") ? 600 : 500,null,null,true);
        blocked(Article153ComponentAuthorityService.POLICY_CHANGED);
    }
    @Test void excludedFormulaAlsoCannotDrift() {
        var v = fixed("A"); certify(v, Decision.EXCLUDE);
        v.update("A",FIXED_AMOUNT,null,null,60000L,"RUB",true);
        blocked(Article153ComponentAuthorityService.POLICY_CHANGED);
    }
    @Test void renamingAndAuditTimeDoNotInvalidateAuthority() {
        var v = fixed("A"); var before = certify(v, Decision.INCLUDE);
        v.update("Полностью новое название",FIXED_AMOUNT,null,null,50000L,"RUB",true);
        assertEquals(before, service.resolve(owner, MAY).components().get(0));
    }
    @Test void anotherWorkerCannotCertifyOrResolveOwnersComponent() {
        var v = fixed("A"); var other = users.saveAndFlush(new AppUser("b-"+UUID.randomUUID(),"unused"));
        assertThrows(IllegalStateException.class, () -> service.certify(other,v.getId(),Decision.INCLUDE,
                SourceKind.LOCAL_NORMATIVE_ACT,"LNA","rev","basis"));
        certify(v, Decision.INCLUDE);
        assertTrue(service.resolve(other,MAY).components().isEmpty());
    }
    @Test void disabledUncertifiedVersionDoesNotParticipateAndCannotBeCertified() {
        var v = fixed("A"); v.update("A",FIXED_AMOUNT,null,null,50000L,"RUB",false);
        assertTrue(service.resolve(owner,MAY).components().isEmpty());
        assertThrows(IllegalStateException.class, () -> certify(v,Decision.INCLUDE));
    }
    @ParameterizedTest @ValueSource(strings={"", " ", "\n"})
    void emptySourceFieldsAreRejected(String blank) {
        var v = fixed("A");
        assertThrows(IllegalArgumentException.class, () -> service.certify(owner,v.getId(),Decision.INCLUDE,
                SourceKind.LOCAL_NORMATIVE_ACT,blank,"rev","basis"));
        assertThrows(IllegalArgumentException.class, () -> service.certify(owner,v.getId(),Decision.EXCLUDE,
                SourceKind.LOCAL_NORMATIVE_ACT,"ref",blank,"basis"));
        assertThrows(IllegalArgumentException.class, () -> service.certify(owner,v.getId(),Decision.EXCLUDE,
                SourceKind.LOCAL_NORMATIVE_ACT,"ref","rev",blank));
    }
}
