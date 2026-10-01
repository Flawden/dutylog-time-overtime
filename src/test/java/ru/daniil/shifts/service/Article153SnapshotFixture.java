package ru.daniil.shifts.service;

import java.time.*;
import java.util.*;
import org.springframework.test.util.ReflectionTestUtils;
import ru.daniil.shifts.model.*;
import ru.daniil.shifts.repo.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

final class Article153SnapshotFixture {
    static final LocalDate DATE = LocalDate.of(2026, 5, 9), MONTH = DATE.withDayOfMonth(1);
    static final String HASH = "a".repeat(64);
    final AppUser owner = new AppUser("fixture", "unused");
    final Article153MonthlyNormPositionAuthorityService norms = mock(Article153MonthlyNormPositionAuthorityService.class);
    final HistoricalCompensationRateService rates = mock(HistoricalCompensationRateService.class);
    final Article153RestDayElectionAuthorityService elections = mock(Article153RestDayElectionAuthorityService.class);
    final Article153LocalRateAuthorityService locals = mock(Article153LocalRateAuthorityService.class);
    final Article153ComponentAuthorityService components = mock(Article153ComponentAuthorityService.class);
    final CompensationTermRepository terms = mock(CompensationTermRepository.class);
    final PayPricingTermRepository pricing = mock(PayPricingTermRepository.class);
    final CompensationTerm term;
    final PayPricingTerm price;
    final Article153SnapshotCaptureService capture;
    Article153SnapshotFixture() {
        ReflectionTestUtils.setField(owner,"id",1L);
        term = new CompensationTerm(owner, MONTH); term.update("HOURLY","RUB",50000L,null);
        ReflectionTestUtils.setField(term,"id",2L);
        price = new PayPricingTerm(owner, MONTH); ReflectionTestUtils.setField(price,"id",3L);
        when(terms.findFirstByOwnerAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(owner,MONTH)).thenReturn(Optional.of(term));
        when(pricing.findFirstByOwnerAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(owner,DATE)).thenReturn(Optional.of(price));
        when(rates.resolve(owner,DATE)).thenReturn(new HistoricalCompensationRateService.HistoricalBaseRate(DATE,YearMonth.from(DATE),MONTH,"HOURLY","RUB",50000L,null));
        when(elections.fingerprint(any(),anyString(),any(),anyList())).thenCallRealMethod();
        when(locals.fingerprint(any(),anyList())).thenCallRealMethod();
        String pricingFingerprint=locals.fingerprint(price,List.of());
        when(locals.resolve(owner,DATE)).thenReturn(new Article153LocalRateAuthorityService.Resolution(DATE,true,Article153LocalRateAuthorityService.State.STATUTORY_ONLY,MONTH,pricingFingerprint,0,null,null));
        when(components.resolve(owner,DATE)).thenReturn(new Article153ComponentAuthorityService.Resolution(DATE,true,List.of(),null,null));
        ready(piece(11,0,60,false));
        capture = new Article153SnapshotCaptureService(norms,rates,elections,locals,components,terms,pricing);
    }
    static PayrollSnapshot draft(AppUser user,int revision) {
        return new PayrollSnapshot(user,MONTH,revision,"RUB",50000L,"HOURLY",MONTH,50000L,null,
            0,0,0,60,0,0,0,0,0,0,60,60,50000L,0,0,0L,0L,0L,null,0L,0L,50000L,
            Instant.parse("2026-06-01T00:00:00Z"),Instant.parse("2026-06-01T00:01:00Z"),HASH);
    }
    PayrollSnapshot draft() {return draft(owner,1);}
    static Article153MonthlyNormPositionAuthorityService.NormPositionPiece piece(long id,int offset,int minutes,boolean rest) {
        var start=DATE.atStartOfDay().toInstant(ZoneOffset.UTC).plusSeconds(offset*60L);
        var source=new OrdinaryWorkPremiumSourceService.SourcePiece(DATE,OrdinaryWorkPremiumSourceService.SourceKind.EXPLICIT,id,null,start,start.plusSeconds(minutes*60L),"UTC",minutes,false,true);
        var provenance=new StatutoryPublicHolidayAuthorityService.Provenance(7,"RU",null,StatutoryPublicHolidayAuthorityService.AuthorityKind.FEDERAL_ARTICLE_112,"RU_TK","Article 112","2026","fixture-law","VICTORY_DAY",null,null,null,null,null,null,null);
        var statutory=new StatutoryPublicHolidayAuthorityService.Resolution(DATE,StatutoryPublicHolidayAuthorityService.Status.NON_WORKING_PUBLIC_HOLIDAY,null,provenance);
        var roster=EmployeeRestDayAuthorityService.Resolution.roster(rest?EmployeeRestDayAuthorityService.Status.REST_DAY:EmployeeRestDayAuthorityService.Status.WORKING_DAY,DATE,21L,31L);
        var qualified=new HolidayPayQualifiedCauseAuthorityService.QualifiedPiece(DATE,rest?HolidayPayQualifiedCauseAuthorityService.Cause.BOTH:HolidayPayQualifiedCauseAuthorityService.Cause.PUBLIC_HOLIDAY,source,statutory,roster);
        return new Article153MonthlyNormPositionAuthorityService.NormPositionPiece(qualified,Article153EconomicLegalPolicy.PayMode.HOURLY,Article153EconomicLegalPolicy.NormPosition.NOT_APPLICABLE,MONTH,null,0,0,HASH);
    }
    void ready(Article153MonthlyNormPositionAuthorityService.NormPositionPiece... pieces) {
        var list=List.of(pieces);
        when(norms.resolve(owner,YearMonth.from(MONTH))).thenReturn(new Article153MonthlyNormPositionAuthorityService.Resolution(YearMonth.from(MONTH),true,PayrollQualifiedQuantity.minutes(list.stream().mapToLong(p->p.minutes()).sum()),list,List.of()));
        for(var p:list){
            long id=p.qualifiedPiece().sourcePiece().sourceActualWorkIntervalId();
            var event=list.stream().map(q->q.qualifiedPiece()).filter(q->q.sourcePiece().sourceActualWorkIntervalId()==id).toList();
            String identity="EXPLICIT:"+id;
            String fp=elections.fingerprint(DATE,identity,p.qualifiedPiece().cause(),event);
            when(elections.resolve(owner,DATE,OrdinaryWorkPremiumSourceService.SourceKind.EXPLICIT,id)).thenReturn(Article153RestDayElectionAuthorityService.Resolution.none(DATE,identity,fp));
        }
    }
}
