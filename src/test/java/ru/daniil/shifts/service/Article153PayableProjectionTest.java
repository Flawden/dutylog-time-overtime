package ru.daniil.shifts.service;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class Article153PayableProjectionTest {
    private Article153RemunerationSnapshotFixture fixture(int...minutes){
        var f=new Article153RemunerationSnapshotFixture();f.pieces(minutes);return f;
    }
    private void bps(Article153RemunerationSnapshotFixture f,int index,int floor,int local){
        var p=f.source.document.path("pieces").get(index);
        ((ObjectNode)p.path("statutoryFloor")).put("additionalTariffBps",floor);
        ((ObjectNode)p.path("localRate")).put("configuredAdditionalTariffBps",local);
    }
    private Article153PayableProjection.Outcome value(Article153RemunerationSnapshotFixture f){
        return Article153PayableProjection.value(f.row(),f.pricing);
    }
    @Test void alternativesAreSelectedPerPieceInsteadOfMaximumOfMonthTotals(){
        var f=fixture(60,60);bps(f,0,20000,10000);bps(f,1,10000,20000);
        var refs=Article153RemunerationSnapshotDocument.read(f.row());
        assertEquals(150000,refs.statutoryTariffReferenceMinor());assertEquals(150000,refs.applicableLocalTariffReferenceMinor());
        var result=value(f);assertEquals(200000,result.tariffPremiumMinor());assertEquals(1,result.lines().size());
        assertEquals(120,result.lines().get(0).minutes());
    }
    @Test void localAndFloorAreNotAdded(){var f=fixture(60);bps(f,0,10000,15000);assertEquals(75000,value(f).holidayPayMinor());}
    @Test void floorCannotBeReducedByLocalRate(){var f=fixture(60);bps(f,0,20000,5000);assertEquals(100000,value(f).holidayPayMinor());}
    @Test void tariffChoicesAreGroupedBeforeRounding(){
        var f=fixture(30,30);f.source.live.term.update("HOURLY","RUB",1L,null);
        for(var p:f.source.document.path("pieces"))((ObjectNode)p.path("historicalRate")).put("baseHourlyRateMinor",1);
        bps(f,0,10000,0);bps(f,1,0,10000);
        assertEquals(1,value(f).holidayPayMinor());assertEquals(1,value(f).lines().size());
    }
    @Test void componentsUseTheirReviewedDenominatorAndAreAddedOnce(){
        var f=fixture(60);f.component(7,10000,9600,10000,0);var result=value(f);
        assertEquals(50000,result.tariffPremiumMinor());assertEquals(63,result.componentPremiumMinor());assertEquals(50063,result.holidayPayMinor());
    }
    @Test void equalComponentAmountsDoNotMergeDifferentVersions(){
        var f=fixture(30);f.component(7,1,60,10000,0);f.component(8,1,60,10000,0);
        assertEquals(2,value(f).componentPremiumMinor());assertEquals(3,value(f).lines().size());
    }
    @Test void explicitRestDayChoiceUsesReviewedComponentBranch(){
        var f=fixture(60);f.component(7,10000,60,20000,5000);f.rest(0);
        assertEquals(0,value(f).tariffPremiumMinor());assertEquals(5000,value(f).componentPremiumMinor());
    }
    @Test void localRestDayNotApplicableDoesNotSneakBackThroughMax(){
        var f=fixture(60);f.rest(0);bps(f,0,0,20000);assertEquals(0,value(f).holidayPayMinor());
    }
    @Test void explicitlyApplicableLocalRestDayRuleSurvives(){
        var f=fixture(60);f.localRule=Article153RemunerationDocument.LocalRestDayRule.APPLIES;f.rest(0);bps(f,0,0,15000);
        assertEquals(75000,value(f).holidayPayMinor());
    }
    @Test void referenceDocumentsRemainUnbooked(){
        var f=fixture(60);var row=f.row();value(f);assertFalse(Article153RemunerationSnapshotDocument.read(row).finalPayableReady());
    }
    @Test void invalidReferenceHashIsRejectedBeforePricing(){
        var f=fixture(60);var row=f.row();org.springframework.test.util.ReflectionTestUtils.setField(row,"fingerprint","0".repeat(64));
        var pricing=mock(PayPricingEngine.class);assertThrows(IllegalStateException.class,()->Article153PayableProjection.value(row,pricing));verifyNoInteractions(pricing);
    }
    @Test void missingComponentRuleCannotYieldPartialTariffPayment(){
        var f=fixture(60);f.component(7,10000,60,10000,0);f.rules.clear();assertThrows(IllegalStateException.class,()->value(f));
    }
}
