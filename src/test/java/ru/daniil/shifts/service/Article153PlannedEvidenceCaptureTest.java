package ru.daniil.shifts.service;

import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import ru.daniil.shifts.model.PayrollQualifiedQuantity;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static ru.daniil.shifts.service.Article153SnapshotFixture.*;

class Article153PlannedEvidenceCaptureTest {
    private Article153SnapshotFixture fixture(int...minutes){
        var f=new Article153SnapshotFixture();var pieces=new ArrayList<Article153MonthlyNormPositionAuthorityService.NormPositionPiece>();
        for(int i=0;i<minutes.length;i++){
            var template=piece(11,0,minutes[i],false);var q=template.qualifiedPiece();
            var start=DATE.atStartOfDay().toInstant(ZoneOffset.UTC);
            var source=new OrdinaryWorkPremiumSourceService.SourcePiece(DATE,OrdinaryWorkPremiumSourceService.SourceKind.PLAN_DERIVED,null,21L,
                    start,start.plusSeconds(180*60L),"UTC",minutes[i],i%2==1,true);
            var qualified=new HolidayPayQualifiedCauseAuthorityService.QualifiedPiece(DATE,q.cause(),source,q.statutoryResolution(),q.restDayResolution());
            pieces.add(new Article153MonthlyNormPositionAuthorityService.NormPositionPiece(qualified,template.payMode(),template.normPosition(),
                    template.compensationEffectiveFrom(),template.productionNormMinutes(),template.workedMinutesBeforeDate(),template.workedMinutesOnDate(),template.decisionFingerprint()));
        }
        when(f.norms.resolve(f.owner,YearMonth.from(MONTH))).thenReturn(new Article153MonthlyNormPositionAuthorityService.Resolution(YearMonth.from(MONTH),true,
                PayrollQualifiedQuantity.minutes(Arrays.stream(minutes).sum()),pieces,List.of()));
        var event=pieces.stream().map(p->p.qualifiedPiece()).toList();
        String fp=f.elections.fingerprint(DATE,"PLAN_DERIVED:21",event.get(0).cause(),event);
        when(f.elections.resolve(f.owner,DATE,OrdinaryWorkPremiumSourceService.SourceKind.PLAN_DERIVED,21))
                .thenReturn(Article153RestDayElectionAuthorityService.Resolution.none(DATE,"PLAN_DERIVED:21",fp));
        return f;
    }
    @Test void dayAndNightFragmentsMayShareOnePlannedEvidenceInterval(){
        var f=fixture(120,60);var doc=f.capture.capture(f.draft());
        assertEquals(180,doc.path("qualifiedMinutes").asInt());assertEquals(2,doc.path("pieces").size());
        assertEquals(doc.at("/pieces/0/norm/qualifiedPiece/sourcePiece/sourceEvidenceStartInstant"),
                doc.at("/pieces/1/norm/qualifiedPiece/sourcePiece/sourceEvidenceStartInstant"));
    }
    @Test void plannedQuantityMayBeSmallerWithoutInventingWhichClockMinutesSurvive(){
        var f=fixture(120);assertEquals(120,f.capture.capture(f.draft()).path("qualifiedMinutes").asInt());
    }
    @Test void sharedPlannedEvidenceCannotCarryMoreMinutesThanItsClockSpan(){
        var f=fixture(120,61);assertThrows(IllegalStateException.class,()->f.capture.capture(f.draft()));
    }
    @Test void explicitPhysicalOverlapStillFails(){
        var f=new Article153SnapshotFixture();f.ready(piece(11,0,120,false),piece(11,60,60,false));
        assertThrows(IllegalStateException.class,()->f.capture.capture(f.draft()));
    }
}
