package ru.daniil.shifts.service;

import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.*;
import org.springframework.transaction.annotation.*;
import ru.daniil.shifts.model.*;
import ru.daniil.shifts.repo.*;
import ru.daniil.shifts.dto.Dtos.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static ru.daniil.shifts.service.Article153SnapshotFixture.*;
import static ru.daniil.shifts.service.Article153RemunerationDocument.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:article153-c4c;DB_CLOSE_DELAY=-1")
@Transactional(isolation=Isolation.REPEATABLE_READ)
class Article153PayrollIntegrationTest {
    @Autowired PayrollService payroll;
    @Autowired Article153PayrollIntegrationService integration;
    @Autowired UserRepository users;
    @Autowired CompensationTermRepository terms;
    @Autowired TimeAccountingPeriodRepository periods;
    @Autowired Article153RemunerationAuthorityRepository reviews;
    @Autowired PayrollSnapshotRepository snapshots;
    @SpyBean PayrollSnapshotArticle153PayableRepository payable;
    @Autowired PayrollSnapshotArticle153RemunerationRepository refs;
    @Autowired PayrollSnapshotEarningLineRepository lines;
    @Autowired PayrollSnapshotEarningManifestRepository manifests;
    @Autowired EntityManager em;
    @MockBean Article153SnapshotCaptureService capture;
    @MockBean Article153RemunerationAuthorityService resolver;
    @MockBean OrdinaryWorkPremiumPricingService ordinary;
    @MockBean PayPricingPolicyService policies;
    @MockBean TimeCompensationService time;
    @MockBean ProductionCalendarService calendar;
    @MockBean LedgerIntegrityService integrity;
    @SpyBean PayPricingEngine pricing;
    @Autowired CompensationComponentConfigurationService components;
    @Autowired PayrollBonusSourceFactService bonuses;
    @Autowired PayrollRegionalCoefficientSourceFactService regionalFacts;
    AppUser owner;CompensationTerm term;Article153RemunerationAuthority review;ObjectNode raw;
    OrdinaryWorkPremiumPricingService.MonthPremiumProjection legacy;
    final PayPricingRuleResolver.Rule holiday=new PayPricingRuleResolver.Rule("HOLIDAY",PayPricingRuleResolver.Dimension.HOLIDAY,10000,0,null,null);
    @BeforeEach void setup(){
        owner=users.saveAndFlush(new AppUser("c4c-"+UUID.randomUUID(),"unused"));term=new CompensationTerm(owner,MONTH);term.update("HOURLY","RUB",50000L,null);terms.saveAndFlush(term);
        var period=new TimeAccountingPeriod(owner,MONTH);period.close();periods.saveAndFlush(period);
        var source=new TimeCompensationService.PayrollSourceSnapshot(MONTH,YearMonth.from(MONTH).atEndOfMonth(),60,60,0,0,0,0,0,0,60,60,
                List.of(new TimeCompensationService.PayrollSourceDay(DATE,60,60,0,0,0,0,60)));
        when(time.payrollSource(eq(owner),any(),any())).thenReturn(source);
        when(calendar.month(owner,"2026-05")).thenReturn(new ProductionCalendarMonthDto("2026-05",0,0,0,0,0,0,0,31,true,List.of()));
        when(integrity.inspect(eq(owner),any(),any())).thenReturn(new LedgerIntegrityDto("2026-05-01","2026-05-31",true,0,0,0,0,0,List.of(),List.of(),List.of()));
        var f=new Article153SnapshotFixture();raw=f.capture.capture(f.draft());
        ((ObjectNode)raw.path("pieces").get(0)).set("pricingRules",Article153SnapshotCodec.value(List.of(holiday)));
        ((ObjectNode)raw.at("/pieces/0/localRate")).put("configuredAdditionalTariffBps",10000);
        when(capture.capture(any())).thenAnswer(i->raw.deepCopy());
        configureLegacy(List.of(holiday),false);
        review=createReview(1,"r1");resolve(review);
    }
    Article153RemunerationAuthority createReview(int revision,String revisionText){
        var evidence=new Source(Article153ComponentAuthority.SourceKind.LOCAL_NORMATIVE_ACT,"LNA §5",revisionText,"complete remuneration review");
        var doc=new Document(SCHEMA,SCOPE,owner.getId(),MONTH,"b".repeat(64),new Review("RUB",evidence,evidence,LocalRestDayRule.NOT_APPLICABLE,List.of()));
        var json=encode(doc);return reviews.saveAndFlush(new Article153RemunerationAuthority(owner,MONTH,revision,json,Article153SnapshotCodec.fingerprint(json)));
    }
    void resolve(Article153RemunerationAuthority row){
        var fact=new Article153RemunerationAuthorityService.Fact(row.getId(),row.getRevision(),row.getFingerprint(),row.getCertifiedAt(),read(row));
        doAnswer(i->new Article153RemunerationAuthorityService.Resolution(i.getArgument(1),true,fact,null)).when(resolver).resolve(any(),any());
    }
    void configureLegacy(List<PayPricingRuleResolver.Rule> ruleList,boolean night){
        ((ObjectNode)raw.at("/pieces/0/norm/qualifiedPiece/sourcePiece")).put("night",night);
        var p=piece(11,0,60,false).qualifiedPiece().sourcePiece();
        p=new OrdinaryWorkPremiumSourceService.SourcePiece(p.sourceDate(),p.sourceKind(),p.sourceActualWorkIntervalId(),p.sourceDayEntryId(),
                p.sourceEvidenceStartInstant(),p.sourceEvidenceEndInstant(),p.sourceEvidenceTimezone(),p.minutes(),night,true);
        var rules=new PayPricingRuleResolver.RuleSet(ruleList);var slices=new PayPricingRuleResolver().resolve(rules,List.of(p.consumedSlice()));
        var priced=new PayPricingEngine().price(50000,List.copyOf(slices));
        long nightMoney=night?priced.premiums().stream().filter(x->x.code().equals("NIGHT")).mapToLong(x->x.amountMinor()).sum():0;
        var valuation=new OrdinaryWorkPremiumPricingService.SourceDateValuation(DATE,p.sourceKind(),60,night?60:0,60,MONTH,YearMonth.from(MONTH),MONTH,"HOURLY","RUB",50000,null,List.of(p),slices);
        var bucket=new OrdinaryWorkPremiumPricingService.PricedRateBucket(50000,60,50000,priced.premiumAmountMinor(),priced.premiums());
        legacy=OrdinaryWorkPremiumPricingService.MonthPremiumProjection.ready(YearMonth.from(MONTH),"RUB",60,50000,priced.premiumAmountMinor(),nightMoney,
                priced.premiumAmountMinor()-nightMoney,nightMoney>0?List.of(new OrdinaryWorkPremiumPricingService.NightPremiumSourceLine(DATE,60,nightMoney)):List.of(),List.of(bucket),List.of(valuation));
        when(ordinary.priceMonth(owner,YearMonth.from(MONTH))).thenReturn(legacy);
        when(policies.resolveForSourceDate(eq(owner),eq(DATE),anyList())).thenReturn(new PayPricingPolicyService.ResolvedPricingPolicy(DATE,MONTH,rules,slices));
    }
    @Test void standardPayrollReplacesLegacyHolidayInsteadOfAddingBoth(){
        ((ObjectNode)raw.at("/pieces/0/statutoryFloor")).put("additionalTariffBps",20000);
        var result=payroll.calculate(owner,"2026-05");assertEquals(50000,result.basePayMinor());assertEquals(100000,result.ordinaryPremiumPayMinor());assertEquals(150000,result.totalPayMinor());
        var doc=integration.load(owner,result.id()).orElseThrow();assertEquals(50000,doc.replacedHolidayMinor());assertEquals(100000,doc.payable().holidayPayMinor());
        assertTrue(refs.existsById(result.id()));assertTrue(manifests.findBySnapshot(snapshots.findById(result.id()).orElseThrow()).isPresent());
    }
    @Test void nightIsPreservedAndHolidayReceivesExactSingleDateSemantics(){
        var night=new PayPricingRuleResolver.Rule("NIGHT",PayPricingRuleResolver.Dimension.NIGHT,500,0,null,null);configureLegacy(List.of(holiday,night),true);
        var result=payroll.calculate(owner,"2026-05");assertEquals(102500,result.totalPayMinor());
        var frozen=lines.findBySnapshotOrderByLineIndexAsc(snapshots.findById(result.id()).orElseThrow());
        var h=frozen.stream().filter(l->"HOLIDAY_PAY".equals(l.getEarningKind())).findFirst().orElseThrow();
        assertEquals(50000,h.getAmountMinor());assertEquals(DATE,h.getEarningPeriodFrom());assertEquals(60,h.getQualifiedQuantityValue());
        assertEquals(2500,frozen.stream().filter(l->"NIGHT_PREMIUM".equals(l.getEarningKind())).mapToLong(l->l.getAmountMinor()).sum());
    }
    @Test void previewMatchesBookedTotalAndDoesNotPersistDrafts(){
        assertEquals(100000,payroll.period(owner,"2026-05").preview().totalPayMinor());assertEquals(0,snapshots.findByOwnerAndPeriodMonthOrderByRevisionDesc(owner,MONTH).size());
    }
    @Test void sameMoneyWithNewReviewedSourceChangesCalculationHash(){
        var first=payroll.calculate(owner,"2026-05");var updated=createReview(2,"r2");resolve(updated);var second=payroll.calculate(owner,"2026-05");
        assertEquals(first.totalPayMinor(),second.totalPayMinor());assertNotEquals(first.calculationHash(),second.calculationHash());
        assertEquals(review.getFingerprint(),refs.findById(first.id()).orElseThrow().getReview().getFingerprint());
    }
    @Test void historyDoesNotResolveOrPriceAgain(){
        var result=payroll.calculate(owner,"2026-05");var before=integration.load(owner,result.id()).orElseThrow();em.clear();reset(resolver,capture,ordinary,policies);clearInvocations(pricing);
        doThrow(new AssertionError("history repricing")).when(pricing).pricePeriodPremium(anyLong(),anyLong(),anyInt(),anyInt());
        assertEquals(before,integration.load(owner,result.id()).orElseThrow());verifyNoInteractions(resolver,capture,ordinary,policies,pricing);
    }
    @Test void foreignOwnerCannotRead(){var result=payroll.calculate(owner,"2026-05");var other=users.saveAndFlush(new AppUser("o-"+UUID.randomUUID(),"unused"));assertTrue(integration.load(other,result.id()).isEmpty());}
    @Test void legacyWithoutReviewIsUnclassifiedAndHasNoPayableDocument(){reviews.delete(review);reviews.flush();var result=payroll.calculate(owner,"2026-05");assertEquals(100000,result.totalPayMinor());assertTrue(integration.load(owner,result.id()).isEmpty());verifyNoInteractions(resolver,capture,policies);}
    @Test void existingReviewDriftBlocksInsteadOfFallingBackToLegacy(){doAnswer(i->new Article153RemunerationAuthorityService.Resolution(i.getArgument(1),false,null,"CONFIGURATION_CHANGED")).when(resolver).resolve(any(),any());assertThrows(ru.daniil.shifts.service.exception.ApiException.class,()->payroll.calculate(owner,"2026-05"));assertEquals(0,snapshots.findByOwnerAndPeriodMonthOrderByRevisionDesc(owner,MONTH).size());}
    @Test void mixedNightHolidayExclusivityRequiresExplicitReconciliation(){
        var h=new PayPricingRuleResolver.Rule("HOLIDAY",PayPricingRuleResolver.Dimension.HOLIDAY,10000,0,null,"one");var n=new PayPricingRuleResolver.Rule("NIGHT",PayPricingRuleResolver.Dimension.NIGHT,500,0,null,"one");
        configureLegacy(List.of(h,n),true);assertFalse(payroll.period(owner,"2026-05").canCalculate());assertThrows(ru.daniil.shifts.service.exception.ApiException.class,()->payroll.calculate(owner,"2026-05"));
    }
    @Test void genericHolidayCannotDoubleBookTheNativeAmount(){
        var c=((com.fasterxml.jackson.databind.node.ArrayNode)raw.at("/pieces/0/components/components")).addObject();c.putObject("formula").put("earningKind","HOLIDAY_PAY");
        assertThrows(IllegalStateException.class,()->payroll.calculate(owner,"2026-05"));
    }
    @Test void corruptPayableDocumentFailsClosed(){var result=payroll.calculate(owner,"2026-05");em.createNativeQuery("update payroll_snapshot_article153_payable set payload_json='{}' where snapshot_id=:id").setParameter("id",result.id()).executeUpdate();em.clear();assertThrows(IllegalStateException.class,()->integration.load(owner,result.id()));}
    @Test void holidayFlowsIntoMonthlyBonusThenRegionalCoefficientBases(){
        var monthly=components.create(owner,new PayrollCompensationComponentCreateRequest("2026-05",
                new PayrollCompensationComponentVersionRequest("monthly","PERCENT_OF_BASE","LOCAL_ELIGIBLE_EARNINGS",4000,null,null,"MONTHLY_BONUS",true)));
        var regional=components.create(owner,new PayrollCompensationComponentCreateRequest("2026-05",
                new PayrollCompensationComponentVersionRequest("regional","PERCENT_OF_BASE","LOCAL_ELIGIBLE_EARNINGS",1500,null,null,"REGIONAL_COEFFICIENT",true)));
        for(var version:List.of(monthly,regional)){
            var c=((com.fasterxml.jackson.databind.node.ArrayNode)raw.at("/pieces/0/components/components")).addObject();
            c.put("ownerId",owner.getId());c.put("versionId",version.versionId());c.put("componentFingerprint","c".repeat(64));c.put("decision","EXCLUDE");
            c.putObject("formula").put("earningKind",version.earningKind());
        }
        bonuses.create(owner,monthly.componentId(),PayrollEarningKind.MONTHLY_BONUS,DATE,DATE,40000L,"RUB");
        regionalFacts.create(owner,regional.componentId(),DATE,DATE,21000L,"RUB");
        var preview=payroll.period(owner,"2026-05").preview();assertTrue(preview.compensationComponentCalculationReady());
        var ml=preview.compensationComponentLines().stream().filter(l->"MONTHLY_BONUS".equals(l.earningKind())).findFirst().orElseThrow();
        var rl=preview.compensationComponentLines().stream().filter(l->"REGIONAL_COEFFICIENT".equals(l.earningKind())).findFirst().orElseThrow();
        assertEquals(100000,ml.referenceBaseMinor());assertEquals(40000,ml.amountMinor());assertEquals(140000,rl.referenceBaseMinor());assertEquals(21000,rl.amountMinor());
        assertEquals(161000,payroll.calculate(owner,"2026-05").totalPayMinor());
    }
    private void cleanupCommittedSetup(){
        periods.findByOwnerAndPeriodMonth(owner,MONTH).ifPresent(periods::delete);reviews.deleteById(review.getId());terms.deleteById(term.getId());users.deleteById(owner.getId());
    }
    @Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void finalDocumentFailureRollsBackWholeParentChain(){
        long before=snapshots.count(),beforeRefs=refs.count(),beforePayable=payable.count();
        doThrow(new IllegalStateException("final write failure")).when(payable).saveAndFlush(any());
        try{assertThrows(IllegalStateException.class,()->payroll.calculate(owner,"2026-05"));assertEquals(before,snapshots.count());assertEquals(beforeRefs,refs.count());assertEquals(beforePayable,payable.count());}
        finally{cleanupCommittedSetup();}
    }
    @Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void captureDriftBeforeFreezeRollsBackAllParents(){
        var calls=new java.util.concurrent.atomic.AtomicInteger();
        when(capture.capture(any())).thenAnswer(i->{var next=raw.deepCopy();if(calls.incrementAndGet()>1)((ObjectNode)next.at("/pieces/0/localRate")).put("configuredAdditionalTariffBps",15000);return next;});
        long before=snapshots.count(),beforeRefs=refs.count();
        try{assertThrows(IllegalStateException.class,()->payroll.calculate(owner,"2026-05"));assertEquals(before,snapshots.count());assertEquals(beforeRefs,refs.count());}
        finally{cleanupCommittedSetup();}
    }
    @Test @Transactional(isolation=Isolation.READ_COMMITTED)
    void reviewedPayrollCannotJoinWeakerRootTransaction(){assertThrows(IllegalStateException.class,()->payroll.calculate(owner,"2026-05"));assertEquals(0,snapshots.findByOwnerAndPeriodMonthOrderByRevisionDesc(owner,MONTH).size());}
    @Test void sourceDateReviewDriftBlocksEntireCalculation(){
        var updated=createReview(2,"r2");var fact=new Article153RemunerationAuthorityService.Fact(updated.getId(),updated.getRevision(),updated.getFingerprint(),updated.getCertifiedAt(),read(updated));
        doReturn(new Article153RemunerationAuthorityService.Resolution(DATE,true,fact,null)).when(resolver).resolve(owner,DATE);
        assertFalse(payroll.period(owner,"2026-05").canCalculate());assertThrows(ru.daniil.shifts.service.exception.ApiException.class,()->payroll.calculate(owner,"2026-05"));
    }
    @Test void invalidHistoryIdentityIsRejected(){assertThrows(IllegalStateException.class,()->integration.load(owner,0L));}
}
