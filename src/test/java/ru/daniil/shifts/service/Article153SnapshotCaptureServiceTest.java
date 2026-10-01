package ru.daniil.shifts.service;
import org.junit.jupiter.api.*;
import java.time.*;
import java.util.*;
import ru.daniil.shifts.model.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static ru.daniil.shifts.service.Article153SnapshotFixture.*;

class Article153SnapshotCaptureServiceTest {
    Article153SnapshotFixture f;
    @BeforeEach void setup(){ f=new Article153SnapshotFixture(); }
    @Test void capturesPhysicalLegalAndEconomicEvidence(){
        var doc=f.capture.capture(f.draft()); var p=doc.path("pieces").get(0);
        assertEquals(60,doc.path("qualifiedMinutes").asInt());
        assertEquals("AUTHORITY_ONLY",doc.path("scope").asText());
        assertEquals(11,p.at("/norm/qualifiedPiece/sourcePiece/sourceActualWorkIntervalId").asLong());
        assertEquals("UTC",p.at("/norm/qualifiedPiece/sourcePiece/sourceEvidenceTimezone").asText());
        assertEquals("fixture-law",p.at("/norm/qualifiedPiece/statutoryResolution/provenance/sourceReference").asText());
        assertEquals(21,p.at("/norm/qualifiedPiece/restDayResolution/dayEntryId").asInt());
        assertEquals(2,p.at("/compensationTerm/termId").asInt());
        assertEquals(50000,p.at("/historicalRate/baseHourlyRateMinor").asLong());
        assertFalse(p.path("statutoryFloor").isEmpty()); assertEquals("NONE",p.at("/election/state").asText());
        assertEquals("STATUTORY_ONLY",p.at("/localRate/state").asText());
        assertTrue(p.at("/components/components").isEmpty());
    }
    @Test void bothCausesKeepSingleMinuteQuantity(){f.ready(piece(11,0,60,true));var d=f.capture.capture(f.draft());assertEquals(60,d.path("qualifiedMinutes").asInt());assertEquals(1,d.path("pieces").size());assertEquals("BOTH",d.at("/pieces/0/norm/qualifiedPiece/cause").asText());}
    @Test void deterministicOrderingIgnoresResolverOrder(){var a=piece(12,120,60,false);var b=piece(11,0,60,false);f.ready(a,b);String first=f.capture.capture(f.draft()).toString();f.ready(b,a);assertEquals(first,f.capture.capture(f.draft()).toString());}
    @Test void splitEventUsesAllPiecesForElectionFingerprint(){f.ready(piece(11,0,30,false),piece(11,30,30,false));assertEquals(2,f.capture.capture(f.draft()).path("pieces").size());}
    @Test void overlappingEventIsRejected(){f.ready(piece(11,0,60,false),piece(11,30,60,false));assertThrows(IllegalStateException.class,()->f.capture.capture(f.draft()));}
    @Test void emptyProvenMonthDoesNotConsultEconomicInputs(){f.ready();clearInvocations(f.rates,f.elections,f.locals,f.components,f.terms,f.pricing);assertEquals(0,f.capture.capture(f.draft()).path("pieces").size());verifyNoInteractions(f.rates,f.elections,f.locals,f.components,f.terms,f.pricing);}
    @Test void blockedNormCannotProducePartialDocument(){when(f.norms.resolve(f.owner,YearMonth.from(MONTH))).thenReturn(Article153MonthlyNormPositionAuthorityService.Resolution.blocked(YearMonth.from(MONTH),List.of(new Article153MonthlyNormPositionAuthorityService.BlockingFact(DATE,Article153MonthlyNormPositionAuthorityService.BlockerKind.COMPENSATION,"missing"))));assertThrows(IllegalStateException.class,()->f.capture.capture(f.draft()));}
    @Test void missingCompensationFailsClosed(){when(f.terms.findFirstByOwnerAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(f.owner,MONTH)).thenReturn(Optional.empty());assertThrows(IllegalStateException.class,()->f.capture.capture(f.draft()));}
    @Test void changedDraftRateFailsClosed(){var s=f.draft();org.springframework.test.util.ReflectionTestUtils.setField(s,"hourlyRateMinor",1L);assertThrows(IllegalStateException.class,()->f.capture.capture(s));}
    @Test void changedConfiguredRateFailsClosed(){f.term.update("HOURLY","RUB",1L,null);assertThrows(IllegalStateException.class,()->f.capture.capture(f.draft()));}
    @Test void changedEffectiveDateFailsClosed(){when(f.rates.resolve(f.owner,DATE)).thenReturn(new HistoricalCompensationRateService.HistoricalBaseRate(DATE,YearMonth.from(MONTH),MONTH.minusMonths(1),"HOURLY","RUB",50000L,null));assertThrows(IllegalStateException.class,()->f.capture.capture(f.draft()));}
    @Test void missingPricingFailsClosed(){when(f.pricing.findFirstByOwnerAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(f.owner,DATE)).thenReturn(Optional.empty());assertThrows(IllegalStateException.class,()->f.capture.capture(f.draft()));}
    @Test void pricingMutationIsDetected(){f.price.addRule("LOCAL","HOLIDAY",10000,0,null,null);assertThrows(IllegalStateException.class,()->f.capture.capture(f.draft()));}
    @Test void blockedElectionFailsClosed(){when(f.elections.resolve(f.owner,DATE,OrdinaryWorkPremiumSourceService.SourceKind.EXPLICIT,11)).thenReturn(Article153RestDayElectionAuthorityService.Resolution.blocked(DATE,"EXPLICIT:11","stale"));assertThrows(IllegalStateException.class,()->f.capture.capture(f.draft()));}
    @Test void staleElectionSourceFailsClosed(){when(f.elections.resolve(f.owner,DATE,OrdinaryWorkPremiumSourceService.SourceKind.EXPLICIT,11)).thenReturn(Article153RestDayElectionAuthorityService.Resolution.none(DATE,"EXPLICIT:11",HASH));assertThrows(IllegalStateException.class,()->f.capture.capture(f.draft()));}
    @Test void blockedLocalRateFailsClosed(){when(f.locals.resolve(f.owner,DATE)).thenReturn(Article153LocalRateAuthorityService.Resolution.blocked(DATE,"missing"));assertThrows(IllegalStateException.class,()->f.capture.capture(f.draft()));}
    @Test void blockedComponentCannotBeDropped(){when(f.components.resolve(f.owner,DATE)).thenReturn(new Article153ComponentAuthorityService.Resolution(DATE,false,List.of(),"missing",4L));assertThrows(IllegalStateException.class,()->f.capture.capture(f.draft()));}
    @Test void snapshotHasNoReferencesToMutableTerm(){var frozen=f.capture.capture(f.draft());f.term.update("HOURLY","USD",1L,null);assertEquals("RUB",frozen.at("/pieces/0/compensationTerm/currencyCode").asText());assertEquals(50000,frozen.at("/pieces/0/compensationTerm/configuredHourlyRateMinor").asLong());}

    @Test void classifiedComponentIncludesFormulaAndSource(){
        var formula=new Article153ComponentAuthorityService.Formula(PayrollEarningKind.MONTHLY_BONUS,CompensationComponentVersion.CalculationType.FIXED_AMOUNT,null,null,30000L,"RUB",true);
        var fact=new Article153ComponentAuthorityService.AuthorityFact(5L,1L,6L,7L,MONTH,Article153ComponentAuthority.Decision.INCLUDE,Article153ComponentAuthority.SourceKind.LOCAL_NORMATIVE_ACT,"LNA-42","rev1","reviewed basis",HASH,Instant.parse("2026-05-01T00:00:00Z"),formula);
        when(f.components.resolve(f.owner,DATE)).thenReturn(new Article153ComponentAuthorityService.Resolution(DATE,true,List.of(fact),null,null));
        var line=f.capture.capture(f.draft()).at("/pieces/0/components/components/0");assertEquals("LNA-42",line.path("sourceReference").asText());assertEquals(30000,line.at("/formula/amountMinor").asLong());assertEquals("INCLUDE",line.path("decision").asText());
    }
    @Test void foreignComponentIsRejected(){
        var formula=new Article153ComponentAuthorityService.Formula(PayrollEarningKind.MONTHLY_BONUS,CompensationComponentVersion.CalculationType.FIXED_AMOUNT,null,null,30000L,"RUB",true);
        var fact=new Article153ComponentAuthorityService.AuthorityFact(5L,99L,6L,7L,MONTH,Article153ComponentAuthority.Decision.EXCLUDE,Article153ComponentAuthority.SourceKind.LOCAL_NORMATIVE_ACT,"LNA-42","rev1","basis",HASH,Instant.now(),formula);
        when(f.components.resolve(f.owner,DATE)).thenReturn(new Article153ComponentAuthorityService.Resolution(DATE,true,List.of(fact),null,null));assertThrows(IllegalStateException.class,()->f.capture.capture(f.draft()));
    }
    @Test void electedRestDayPreservesChoiceAndApplication(){
        var current=f.elections.resolve(f.owner,DATE,OrdinaryWorkPremiumSourceService.SourceKind.EXPLICIT,11);
        var now=Instant.parse("2026-05-09T00:00:00Z");
        var fact=new Article153RestDayElectionAuthorityService.ElectionFact(8,DATE,"EXPLICIT","EXPLICIT:11",11L,null,now,now.plusSeconds(3600),"UTC","PUBLIC_HOLIDAY",60,current.currentSourceFingerprint(),"ACTIVE",now,null,null,now,now);
        when(f.elections.resolve(f.owner,DATE,OrdinaryWorkPremiumSourceService.SourceKind.EXPLICIT,11)).thenReturn(Article153RestDayElectionAuthorityService.Resolution.elected(DATE,"EXPLICIT:11",current.currentSourceFingerprint(),fact));
        var p=f.capture.capture(f.draft()).at("/pieces/0");assertEquals(8,p.at("/election/fact/electionId").asInt());assertTrue(p.path("statutoryFloor").toString().contains("OTHER_REST_DAY"));
    }
    @Test void salaryNormAndMonthlyAmountAreCaptured(){
        var original=piece(11,0,60,false);
        f.ready(new Article153MonthlyNormPositionAuthorityService.NormPositionPiece(original.qualifiedPiece(),Article153EconomicLegalPolicy.PayMode.SALARY,Article153EconomicLegalPolicy.NormPosition.WITHIN_MONTHLY_NORM,MONTH,9600,100,60,HASH));
        f.term.update("SALARY","RUB",null,8000000L);
        when(f.rates.resolve(f.owner,DATE)).thenReturn(new HistoricalCompensationRateService.HistoricalBaseRate(DATE,YearMonth.from(MONTH),MONTH,"SALARY","RUB",50000L,9600));
        var d=f.draft();org.springframework.test.util.ReflectionTestUtils.setField(d,"payMode","SALARY");org.springframework.test.util.ReflectionTestUtils.setField(d,"configuredHourlyRateMinor",null);org.springframework.test.util.ReflectionTestUtils.setField(d,"monthlySalaryMinor",8000000L);org.springframework.test.util.ReflectionTestUtils.setField(d,"productionNormMinutes",9600);
        assertEquals(8000000,f.capture.capture(d).at("/pieces/0/compensationTerm/monthlySalaryMinor").asLong());
        org.springframework.test.util.ReflectionTestUtils.setField(d,"productionNormMinutes",9500);assertThrows(IllegalStateException.class,()->f.capture.capture(d));
    }
}
