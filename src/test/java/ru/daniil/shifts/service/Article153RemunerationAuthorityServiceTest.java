package ru.daniil.shifts.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import ru.daniil.shifts.model.*;
import ru.daniil.shifts.repo.*;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static ru.daniil.shifts.service.Article153RemunerationDocument.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:article153-remuneration-isolated;DB_CLOSE_DELAY=-1") @Transactional(isolation=Isolation.REPEATABLE_READ)
class Article153RemunerationAuthorityServiceTest {
    @Autowired Article153RemunerationAuthorityService service;
    @Autowired Article153ComponentAuthorityService classification;
    @Autowired CompensationComponentRepository components;
    @Autowired CompensationComponentVersionRepository versions;
    @Autowired Article153RemunerationAuthorityRepository rows;
    @Autowired UserRepository users;
    @Autowired CompensationTermRepository terms;
    @Autowired EntityManager em;
    @MockBean Article153LocalRateAuthorityService local;
    AppUser owner;CompensationTerm term;
    static final YearMonth MONTH=YearMonth.of(2026,5);
    static final LocalDate DATE=MONTH.atDay(9);
    static final Source SOURCE=new Source(Article153ComponentAuthority.SourceKind.LOCAL_NORMATIVE_ACT,"LNA §5","r1","Entire remuneration system reviewed, including tariff and all additions");
    @BeforeEach void setup() {
        owner=users.saveAndFlush(new AppUser("c4b-"+UUID.randomUUID(),"unused"));
        term=new CompensationTerm(owner,MONTH.atDay(1));term.update("HOURLY","RUB",50000L,null);terms.saveAndFlush(term);
        localPolicy(0,"a".repeat(64));
    }
    void localPolicy(int bps,String hash) {
        doAnswer(i->new Article153LocalRateAuthorityService.Resolution(i.getArgument(1),true,
                bps==0?Article153LocalRateAuthorityService.State.STATUTORY_ONLY:Article153LocalRateAuthorityService.State.SOURCE_BACKED_LOCAL_RATE,
                MONTH.atDay(1),hash,bps,bps==0?null:new Article153LocalRateAuthorityService.AuthorityFact(1,MONTH.atDay(1),
                Article153LocalRateAuthorityService.SourceKind.LOCAL_NORMATIVE_ACT,"LNA","r1",hash,Instant.parse("2026-05-01T00:00:00Z")),null)).when(local).resolve(any(),any());
    }
    CompensationComponentVersion component(boolean enabled,Article153ComponentAuthority.Decision decision) {
        var v=versions.saveAndFlush(new CompensationComponentVersion(components.saveAndFlush(new CompensationComponent(owner)),
                MONTH.atDay(1),"Name is not a rule",CompensationComponentVersion.CalculationType.FIXED_AMOUNT,null,null,50000L,"RUB",enabled));
        if(enabled && decision!=null)classification.certify(owner,v.getId(),decision,SOURCE.kind(),SOURCE.reference(),SOURCE.revision(),SOURCE.basis());return v;
    }
    Rule rule(CompensationComponentVersion v) {return new Rule(v.getId(),Article153ComponentAuthorityService.fingerprint(v),"RUB",Period.PAYROLL_MONTH,50000,9600,10000,0,SOURCE);}
    Review review(Rule...rules){return new Review("RUB",SOURCE,SOURCE,LocalRestDayRule.NOT_APPLICABLE,List.of(rules));}
    @Test @Transactional(isolation=Isolation.READ_COMMITTED)
    void weakCallerTransactionCannotCertify(){assertThrows(IllegalStateException.class,()->service.certify(owner,MONTH,0,review()));assertEquals(0,rows.count());}
    @Test void missingReviewBlocksEvenEmptyConfiguredSet(){assertEquals("SOURCE_REQUIRED",service.resolve(owner,DATE).blockingReason());}
    @Test void explicitEmptyEntireSystemReviewIsReady(){service.certify(owner,MONTH,0,review());assertTrue(service.resolve(owner,DATE).ready());}
    @Test void persistsExactRulesAndSeparateChoiceMultipliers(){var v=component(true,Article153ComponentAuthority.Decision.INCLUDE);var f=service.certify(owner,MONTH,0,review(rule(v)));em.clear();assertEquals(f,service.load(owner,f.authorityId()).orElseThrow());assertEquals(0,f.document().review().rules().get(0).restDayAdditionalBps());assertTrue(service.resolve(owner,DATE).ready());}
    @Test void missingIncludeFormulaRejectsWholeReview(){component(true,Article153ComponentAuthority.Decision.INCLUDE);assertThrows(IllegalStateException.class,()->service.certify(owner,MONTH,0,review()));assertEquals(0,rows.count());}
    @Test void excludeDoesNotAcceptMonetaryRule(){var v=component(true,Article153ComponentAuthority.Decision.EXCLUDE);assertThrows(IllegalStateException.class,()->service.certify(owner,MONTH,0,review(rule(v))));assertTrue(service.certify(owner,MONTH,0,review()).document().review().rules().isEmpty());}
    @Test void unclassifiedComponentRejectsReview(){component(true,Article153ComponentAuthority.Decision.UNCLASSIFIED);assertThrows(IllegalStateException.class,()->service.certify(owner,MONTH,0,review()));}
    @Test void missingClassificationRejectsReview(){component(true,null);assertThrows(IllegalStateException.class,()->service.certify(owner,MONTH,0,review()));}
    @Test void currencyMismatchRejectsReview(){assertThrows(IllegalStateException.class,()->service.certify(owner,MONTH,0,new Review("EUR",SOURCE,SOURCE,LocalRestDayRule.NOT_APPLICABLE,List.of())));}
    @Test void wrongComponentFingerprintRejectsReview(){var v=component(true,Article153ComponentAuthority.Decision.INCLUDE);var r=new Rule(v.getId(),"b".repeat(64),"RUB",Period.HOUR,100,60,10000,0,SOURCE);assertThrows(IllegalStateException.class,()->service.certify(owner,MONTH,0,review(r)));}
    @Test void repeatedExactCertificationIsIdempotent(){var a=service.certify(owner,MONTH,0,review());assertEquals(a,service.certify(owner,MONTH,0,review()));assertEquals(1,rows.count());}
    @Test void correctionsAreAppendOnlyAndOldReviewRemainsReadable(){var first=service.certify(owner,MONTH,0,review());var corrected=new Review("RUB",new Source(SOURCE.kind(),"corrected","r2","complete corrected system"),SOURCE,LocalRestDayRule.NOT_APPLICABLE,List.of());var second=service.certify(owner,MONTH,1,corrected);assertEquals(2,second.revision());assertEquals(first,service.load(owner,first.authorityId()).orElseThrow());assertEquals(second,service.resolve(owner,DATE).fact());}
    @Test void staleRevisionCannotOverwriteReview(){service.certify(owner,MONTH,0,review());assertThrows(IllegalStateException.class,()->service.certify(owner,MONTH,0,new Review("RUB",new Source(SOURCE.kind(),"new","r2","changed source"),SOURCE,LocalRestDayRule.NOT_APPLICABLE,List.of())));assertEquals(1,rows.count());}
    @Test void compensationEditInvalidatesReviewButNotHistory(){var f=service.certify(owner,MONTH,0,review());term.update("HOURLY","RUB",60000L,null);terms.flush();assertEquals("CONFIGURATION_CHANGED",service.resolve(owner,DATE).blockingReason());assertEquals(f,service.load(owner,f.authorityId()).orElseThrow());}
    @Test void addedDisabledVersionStillChangesInventory(){service.certify(owner,MONTH,0,review());component(false,null);assertEquals("CONFIGURATION_CHANGED",service.resolve(owner,DATE).blockingReason());}
    @Test void addedClassifiedComponentRequiresNewCompleteReview(){service.certify(owner,MONTH,0,review());var v=component(true,Article153ComponentAuthority.Decision.INCLUDE);assertFalse(service.resolve(owner,DATE).ready());assertTrue(service.certify(owner,MONTH,1,review(rule(v))).revision()==2);assertTrue(service.resolve(owner,DATE).ready());}
    @Test void missingMonthDoesNotReuseEarlierReview(){service.certify(owner,MONTH,0,review());assertEquals("SOURCE_REQUIRED",service.resolve(owner,MONTH.plusMonths(1).atDay(1)).blockingReason());}
    @Test void ownerIsolation(){var f=service.certify(owner,MONTH,0,review());var other=users.saveAndFlush(new AppUser("o-"+UUID.randomUUID(),"unused"));assertTrue(service.load(other,f.authorityId()).isEmpty());assertFalse(service.resolve(other,DATE).ready());}
    @Test void historicalReadNeverResolvesLivePricing(){var f=service.certify(owner,MONTH,0,review());clearInvocations(local);service.load(owner,f.authorityId());verifyNoInteractions(local);}
    @Test void localPolicyDriftBlocksReview(){service.certify(owner,MONTH,0,review());localPolicy(0,"c".repeat(64));assertEquals("CONFIGURATION_CHANGED",service.resolve(owner,DATE).blockingReason());}
    @Test void sourceBackedLocalRestDayApplicabilityIsExplicit(){localPolicy(15000,"a".repeat(64));var r=new Review("RUB",SOURCE,SOURCE,LocalRestDayRule.APPLIES,List.of());assertEquals(LocalRestDayRule.APPLIES,service.certify(owner,MONTH,0,r).document().review().localRestDayRule());assertTrue(service.resolve(owner,DATE).ready());}
    @Test void zeroLocalPolicyCannotClaimApplicablePremium(){assertThrows(IllegalStateException.class,()->service.certify(owner,MONTH,0,new Review("RUB",SOURCE,SOURCE,LocalRestDayRule.APPLIES,List.of())));}
    @Test void foreignRuleCannotEnterOwnersSystem(){var v=component(true,Article153ComponentAuthority.Decision.INCLUDE);var r=rule(v);var other=users.saveAndFlush(new AppUser("o-"+UUID.randomUUID(),"unused"));assertThrows(IllegalStateException.class,()->service.certify(other,MONTH,0,review(r)));}
    @Test void sourceEvidenceCannotBeMissing(){assertThrows(NullPointerException.class,()->new Review("RUB",null,SOURCE,LocalRestDayRule.NOT_APPLICABLE,List.of()));}
}
