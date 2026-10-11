package ru.daniil.shifts.service;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.daniil.shifts.model.*;
import ru.daniil.shifts.repo.*;
import ru.daniil.shifts.service.exception.ApiException;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static ru.daniil.shifts.service.Article153RemunerationDocument.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:article153-review-workspace;DB_CLOSE_DELAY=-1")
class Article153ReviewWorkspaceServiceTest {
    @Autowired Article153ReviewWorkspaceService service;
    @Autowired UserRepository users;
    @Autowired CompensationTermRepository terms;
    @Autowired PayPricingTermRepository pricing;
    @Autowired CompensationComponentRepository components;
    @Autowired CompensationComponentVersionRepository versions;
    @Autowired Article153ComponentAuthorityRepository classifications;
    @Autowired Article153RemunerationAuthorityService remuneration;
    @Autowired Article153LocalRateAuthorityRepository localAuthorities;
    @Autowired PlatformTransactionManager transactions;
    AppUser owner;
    static final String MONTH="2026-05";
    static final LocalDate DATE=LocalDate.of(2026,5,1);
    static final Source SOURCE=new Source(Article153ComponentAuthority.SourceKind.LOCAL_NORMATIVE_ACT,"Pay system §5","2026-05","Reviewed tariff and all supplements against the source");
    @BeforeEach void setup() {
        new TransactionTemplate(transactions).executeWithoutResult(t -> {
            owner=users.saveAndFlush(new AppUser("review-"+UUID.randomUUID().toString().replace("-", ""),"unused"));
            var term=new CompensationTerm(owner,DATE);term.update("HOURLY","RUB",50000L,null);terms.saveAndFlush(term);
            pricing.saveAndFlush(new PayPricingTerm(owner,DATE));
        });
    }
    CompensationComponentVersion component() {
        return new TransactionTemplate(transactions).execute(t -> versions.saveAndFlush(new CompensationComponentVersion(
            components.saveAndFlush(new CompensationComponent(owner)),DATE,"Reviewed supplement",
            CompensationComponentVersion.CalculationType.FIXED_AMOUNT,null,null,50000L,"RUB",true)));
    }
    Article153ReviewWorkspaceService.Submission submission(Article153ReviewWorkspaceService.Workspace w, List<Rule> rules) {
        return new Article153ReviewWorkspaceService.Submission(w.configurationToken(),w.revision(),null,
            w.components().stream().map(c -> new Article153ReviewWorkspaceService.ComponentInput(c.versionId(),c.fingerprint(),Article153ComponentAuthority.Decision.INCLUDE,SOURCE)).toList(),
            new Review("RUB",SOURCE,SOURCE,LocalRestDayRule.NOT_APPLICABLE,rules));
    }
    Rule rule(CompensationComponentVersion v) { return new Rule(v.getId(),Article153ComponentAuthorityService.fingerprint(v),"RUB",Period.HOUR,50000,60,10000,0,SOURCE); }
    @Test void emptySystemNeedsExplicitSourcesAndRoundTrips() {
        var before=service.read(owner,MONTH);assertFalse(before.ready());
        var after=service.save(owner,MONTH,submission(before,List.of()));
        assertTrue(after.ready());assertEquals(1,after.revision());assertEquals(SOURCE,after.savedReview().completeSystemSource());
    }
    @Test void classificationAndMonetaryRulesSaveTogether() {
        var v=component();var after=service.save(owner,MONTH,submission(service.read(owner,MONTH),List.of(rule(v))));
        assertTrue(after.ready());assertTrue(after.components().get(0).locked());assertEquals(50000,after.savedReview().rules().get(0).periodAmountMinor());
    }
    @Test void invalidMonetarySetRollsBackNewClassification() {
        var v=component();var before=service.read(owner,MONTH);
        assertThrows(ApiException.class,()->service.save(owner,MONTH,submission(before,List.of())));
        assertTrue(classifications.findByComponentVersion(v).isEmpty());
        assertEquals(0,service.read(owner,MONTH).revision());
    }
    @Test void invalidRuleSetAlsoRollsBackNewLocalAuthority() {
        var v=component();
        var pricingTerm=new TransactionTemplate(transactions).execute(t -> {
            var p=pricing.findFirstByOwnerAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(owner,DATE).orElseThrow();
            p.addRule("HOLIDAY","HOLIDAY",10000,0,null,null);return pricing.saveAndFlush(p);
        });
        var w=service.read(owner,MONTH);assertTrue(w.localSourceRequired());
        var input=submission(w,List.of());
        var withLocal=new Article153ReviewWorkspaceService.Submission(input.configurationToken(),input.expectedRevision(),new Article153ReviewWorkspaceService.LocalSource(SOURCE.kind(),SOURCE.reference(),SOURCE.revision()),input.components(),input.review());
        assertThrows(ApiException.class,()->service.save(owner,MONTH,withLocal));
        assertTrue(localAuthorities.findByOwnerAndPricingTerm(owner,pricingTerm).isEmpty());
        assertTrue(classifications.findByComponentVersion(v).isEmpty());
        assertEquals(0,service.read(owner,MONTH).revision());
    }
    @Test void changedConfigurationRejectsStaleFormBeforeWrites() {
        var before=service.read(owner,MONTH);component();
        assertThrows(ApiException.class,()->service.save(owner,MONTH,submission(before,List.of())));
        assertEquals(0,service.read(owner,MONTH).revision());assertFalse(service.read(owner,MONTH).components().get(0).locked());
    }
    @Test void ownerCannotSubmitAnotherOwnersToken() {
        var before=service.read(owner,MONTH);
        var other=users.saveAndFlush(new AppUser("other-"+UUID.randomUUID().toString().replace("-", ""),"unused"));
        assertThrows(ApiException.class,()->service.save(other,MONTH,submission(before,List.of())));
        assertEquals(0,service.read(owner,MONTH).revision());assertEquals(0,service.read(other,MONTH).revision());
    }
    @Test void correctionAppendsReviewWithoutChangingHistory() {
        service.save(owner,MONTH,submission(service.read(owner,MONTH),List.of()));
        var first=remuneration.resolve(owner,DATE).fact();var current=service.read(owner,MONTH);
        var changed=new Source(SOURCE.kind(),"Corrected source §6","2026-06","Corrected complete system review");
        service.save(owner,MONTH,new Article153ReviewWorkspaceService.Submission(current.configurationToken(),current.revision(),null,List.of(),new Review("RUB",changed,SOURCE,LocalRestDayRule.NOT_APPLICABLE,List.of())));
        assertEquals(2,service.read(owner,MONTH).revision());assertEquals(first,remuneration.load(owner,first.authorityId()).orElseThrow());
    }
    @Test void existingClassificationCannotBeRewritten() {
        var v=component();service.save(owner,MONTH,submission(service.read(owner,MONTH),List.of(rule(v))));
        var w=service.read(owner,MONTH);var c=w.components().get(0);
        var edit=new Article153ReviewWorkspaceService.Submission(w.configurationToken(),w.revision(),null,List.of(new Article153ReviewWorkspaceService.ComponentInput(c.versionId(),c.fingerprint(),Article153ComponentAuthority.Decision.EXCLUDE,SOURCE)),new Review("RUB",SOURCE,SOURCE,LocalRestDayRule.NOT_APPLICABLE,List.of()));
        assertThrows(ApiException.class,()->service.save(owner,MONTH,edit));assertEquals(1,service.read(owner,MONTH).revision());
    }
}
