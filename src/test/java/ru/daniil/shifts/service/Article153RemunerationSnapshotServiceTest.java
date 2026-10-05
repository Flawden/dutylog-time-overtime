package ru.daniil.shifts.service;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.*;
import org.springframework.transaction.annotation.*;
import ru.daniil.shifts.model.*;
import ru.daniil.shifts.repo.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static ru.daniil.shifts.service.Article153SnapshotFixture.draft;
import static ru.daniil.shifts.service.Article153RemunerationDocument.*;
@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:article153-b2-isolated;DB_CLOSE_DELAY=-1")
@Transactional(isolation=Isolation.REPEATABLE_READ)
class Article153RemunerationSnapshotServiceTest {
    @Autowired Article153RemunerationSnapshotService service;
    @Autowired Article153TariffSnapshotService legacy;
    @Autowired PayrollSnapshotRepository snapshots;
    @Autowired PayrollSnapshotArticle153Repository c3;
    @Autowired PayrollSnapshotArticle153TariffRepository c4a;
    @Autowired PayrollSnapshotArticle153RemunerationRepository frozen;
    @Autowired Article153RemunerationAuthorityRepository reviews;
    @Autowired UserRepository users;
    @Autowired EntityManager em;
    @MockBean Article153SnapshotCaptureService capture;
    @MockBean Article153RemunerationAuthorityService resolver;
    @SpyBean Article153RemunerationValuationService valuation;
    @SpyBean PayPricingEngine pricing;
    AppUser owner;Article153RemunerationAuthority review;
    final Source evidence=new Source(Article153ComponentAuthority.SourceKind.LOCAL_NORMATIVE_ACT,"LNA","r1","complete empty component system");
    @BeforeEach void setup(){owner=users.saveAndFlush(new AppUser("b2-"+UUID.randomUUID(),"unused"));review=review(1,"r1");
        when(capture.capture(any())).thenAnswer(i->{var f=new Article153SnapshotFixture();return f.capture.capture(f.draft());});resolve(review);}
    Article153RemunerationAuthority review(int revision,String sourceRevision){
        var doc=new Document(SCHEMA,SCOPE,owner.getId(),Article153SnapshotFixture.MONTH,"b".repeat(64),new Review("RUB",
                new Source(evidence.kind(),evidence.reference(),sourceRevision,evidence.basis()),evidence,LocalRestDayRule.NOT_APPLICABLE,List.of()));
        String json=encode(doc);return reviews.saveAndFlush(new Article153RemunerationAuthority(owner,doc.periodMonth(),revision,json,Article153SnapshotCodec.fingerprint(json)));}
    void resolve(Article153RemunerationAuthority row){var fact=new Article153RemunerationAuthorityService.Fact(row.getId(),row.getRevision(),row.getFingerprint(),row.getCertifiedAt(),read(row));
        doAnswer(i->new Article153RemunerationAuthorityService.Resolution(i.getArgument(1),true,fact,null)).when(resolver).resolve(any(),any());}
    @Test void fourSnapshotsAndExactReviewPersistAtomically(){var row=service.createRevision(draft(owner,1));Long id=row.getSnapshotId();em.clear();var d=service.load(owner,id).orElseThrow();assertEquals(review.getId(),d.reviewId());assertEquals(50000,d.statutoryTariffReferenceMinor());assertEquals(0,d.componentReferenceMinor());assertEquals(50000,snapshots.findById(id).orElseThrow().getTotalPayMinor());assertTrue(c3.existsById(id));assertTrue(c4a.existsById(id));assertFalse(d.finalPayableReady());}
    @Test void historicalReadDoesNotResolveCurrentRulesOrReprice(){var row=service.createRevision(draft(owner,1));var before=service.load(owner,row.getSnapshotId()).orElseThrow();em.clear();reset(resolver,capture);clearInvocations(valuation,pricing);doThrow(new AssertionError("reprice")).when(pricing).pricePeriodPremium(anyLong(),anyLong(),anyInt(),anyInt());assertEquals(before,service.load(owner,row.getSnapshotId()).orElseThrow());verifyNoInteractions(resolver,capture,valuation,pricing);}
    @Test void ownersCannotReadEachOthersRevision(){var row=service.createRevision(draft(owner,1));var other=users.saveAndFlush(new AppUser("o-"+UUID.randomUUID(),"unused"));assertTrue(service.load(other,row.getSnapshotId()).isEmpty());}
    @Test void legacyC4aIsAbsentNotZero(){var row=legacy.createRevision(draft(owner,1));assertTrue(service.load(owner,row.getSnapshotId()).isEmpty());}
    @Test void existingSnapshotCannotBeRepriced(){var row=service.createRevision(draft(owner,1));assertThrows(IllegalStateException.class,()->service.createRevision(row.getTariff().getAuthority().getSnapshot()));}
    @Test void newerReviewDoesNotAlterEarlierSnapshot(){var first=service.createRevision(draft(owner,1));var old=service.load(owner,first.getSnapshotId()).orElseThrow();var updated=review(2,"r2");resolve(updated);var second=service.createRevision(draft(owner,2));assertEquals(review.getId(),service.load(owner,first.getSnapshotId()).orElseThrow().reviewId());assertEquals(old,service.load(owner,first.getSnapshotId()).orElseThrow());assertEquals(updated.getId(),service.load(owner,second.getSnapshotId()).orElseThrow().reviewId());}
    @Test void corruptStoredMoneyFailsClosed(){var row=service.createRevision(draft(owner,1));em.createNativeQuery("update payroll_snapshot_article153_remuneration set payload_json='{}' where snapshot_id=:id").setParameter("id",row.getSnapshotId()).executeUpdate();em.clear();assertThrows(IllegalStateException.class,()->service.load(owner,row.getSnapshotId()));}
    @Test void invalidReadIdentityRejected(){assertThrows(IllegalStateException.class,()->service.load(owner,0L));}
    @Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void valuationFailureRollsBackAllSnapshotParents(){long count=snapshots.count(),a=c3.count(),b=c4a.count(),c=frozen.count();doThrow(new IllegalStateException("overflow")).when(valuation).value(any(),any());try{assertThrows(IllegalStateException.class,()->service.createRevision(draft(owner,1)));assertEquals(count,snapshots.count());assertEquals(a,c3.count());assertEquals(b,c4a.count());assertEquals(c,frozen.count());}finally{reviews.deleteById(review.getId());users.deleteById(owner.getId());}}
    @Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void missingCompleteReviewRollsBackAlreadyCreatedTariff(){long count=snapshots.count(),a=c3.count(),b=c4a.count();doAnswer(i->new Article153RemunerationAuthorityService.Resolution(i.getArgument(1),false,null,"SOURCE_REQUIRED")).when(resolver).resolve(any(),any());try{assertThrows(IllegalStateException.class,()->service.createRevision(draft(owner,1)));assertEquals(count,snapshots.count());assertEquals(a,c3.count());assertEquals(b,c4a.count());}finally{reviews.deleteById(review.getId());users.deleteById(owner.getId());}}
    @Test void intramonthReviewMismatchRejectsNewSnapshot(){var other=review(2,"r2");var fact=new Article153RemunerationAuthorityService.Fact(other.getId(),other.getRevision(),other.getFingerprint(),other.getCertifiedAt(),read(other));doReturn(new Article153RemunerationAuthorityService.Resolution(Article153SnapshotFixture.DATE,true,fact,null)).when(resolver).resolve(owner,Article153SnapshotFixture.DATE);assertThrows(IllegalStateException.class,()->service.createRevision(draft(owner,1)));}
}
