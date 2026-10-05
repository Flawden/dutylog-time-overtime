package ru.daniil.shifts.service;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ru.daniil.shifts.model.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static ru.daniil.shifts.service.Article153RemunerationSnapshotDocument.*;
class Article153RemunerationValuationTest {
    Article153RemunerationSnapshotFixture f=new Article153RemunerationSnapshotFixture();
    Document value(){return f.service.value(f.tariff(),f.review());}
    @Test void emptyReviewedComponentSetKeepsReferencesSeparate(){var d=value();assertEquals(50000,d.statutoryTariffReferenceMinor());assertEquals(0,d.componentReferenceMinor());assertEquals(0,d.applicableLocalTariffReferenceMinor());assertFalse(d.finalPayableReady());assertEquals(BLOCKERS,d.activationBlockers());}
    @Test void hourAmountProducesAdditionalComponentOnly(){f.component(7,500,60,15000,0);assertEquals(750,value().componentReferenceMinor());assertEquals(50000,value().statutoryTariffReferenceMinor());}
    @Test void monthlyDenominatorIsNotRoundedToHour(){f.component(7,10000,9600,10000,0);assertEquals(63,value().componentReferenceMinor());}
    @Test void fragmentationCannotCreateExtraPenny(){f.pieces(30,30);f.component(7,1,60,10000,0);var d=value();assertEquals(1,d.componentReferenceMinor());var line=d.lines().stream().filter(l->l.key().kind()==Kind.COMPONENT_REFERENCE).findFirst().orElseThrow();assertEquals(2,line.sources().size());assertEquals(60,line.minutes());}
    @Test void independentComponentsRoundSeparately(){f.component(7,1,60,5000,0);f.component(8,1,60,5000,0);assertEquals(2,value().componentReferenceMinor());}
    @Test void explicitRestDayMultiplierIsUsed(){f.component(7,500,60,10000,2500);f.rest(0);assertEquals(125,value().componentReferenceMinor());assertEquals(0,value().statutoryTariffReferenceMinor());}
    @Test void differentChoicesWithDifferentBpsDoNotShareRoundingGroup(){f.pieces(30,30);f.component(7,100,60,10000,5000);f.rest(1);assertEquals(75,value().componentReferenceMinor());assertEquals(2,value().lines().stream().filter(l->l.key().kind()==Kind.COMPONENT_REFERENCE).count());}
    @Test void equalChoiceBpsDoShareEconomicRounding(){f.pieces(30,30);f.component(7,1,60,10000,10000);f.rest(1);assertEquals(1,value().componentReferenceMinor());}
    @Test void localPremiumExcludedForRestDayWhenReviewedNotApplicable(){f.source.set("/pieces/0/localRate","configuredAdditionalTariffBps",15000);f.rest(0);assertEquals(0,value().applicableLocalTariffReferenceMinor());}
    @Test void localPremiumPreservedForRestDayWhenReviewedApplicable(){f.source.set("/pieces/0/localRate","configuredAdditionalTariffBps",15000);f.localRule=Article153RemunerationDocument.LocalRestDayRule.APPLIES;f.rest(0);assertEquals(75000,value().applicableLocalTariffReferenceMinor());}
    @Test void localPremiumStillAppliesToEnhancedPay(){f.source.set("/pieces/0/localRate","configuredAdditionalTariffBps",15000);assertEquals(75000,value().applicableLocalTariffReferenceMinor());}
    @Test void missingComponentRuleFailsClosed(){f.component(7,500,60,10000,0);f.rules.clear();assertThrows(IllegalStateException.class,this::value);}
    @Test void extraRuleFailsClosed(){f.component(7,500,60,10000,0);((com.fasterxml.jackson.databind.node.ArrayNode)f.source.document.at("/pieces/0/components/components")).removeAll();assertThrows(IllegalStateException.class,this::value);}
    @Test void componentFingerprintMismatchFailsClosed(){f.component(7,500,60,10000,0);((ObjectNode)f.source.document.at("/pieces/0/components/components/0")).put("componentFingerprint","c".repeat(64));assertThrows(IllegalStateException.class,this::value);}
    @Test void excludedComponentCannotHaveRule(){f.component(7,500,60,10000,0);((ObjectNode)f.source.document.at("/pieces/0/components/components/0")).put("decision","EXCLUDE");assertThrows(IllegalStateException.class,this::value);}
    @Test void foreignOwnerCannotSupplyRule(){f.component(7,500,60,10000,0);((ObjectNode)f.source.document.at("/pieces/0/components/components/0")).put("ownerId",2);assertThrows(IllegalStateException.class,this::value);}
    @Test void reviewOwnerMismatchFailsClosed(){var r=f.review();ReflectionTestUtils.setField(r.getOwner(),"id",2L);assertThrows(IllegalStateException.class,()->f.service.value(f.tariff(),r));}
    @Test void unqualifiedMonthCannotUseReviewedRules(){f.source.set("/pieces/0/norm/qualifiedPiece","payrollDate","2026-06-09");assertThrows(IllegalStateException.class,this::value);}
    @Test void zeroAdditionalRuleIsPreservedAsExplicitLine(){f.component(7,500,60,0,0);var d=value();assertEquals(0,d.componentReferenceMinor());assertTrue(d.lines().stream().anyMatch(l->l.key().kind()==Kind.COMPONENT_REFERENCE && l.key().additionalBps()==0));}
    @Test void historicalReadDoesNotInvokeMoneyEngine(){f.component(7,500,60,10000,0);var row=f.row();var before=read(row);assertEquals(before,read(row));assertEquals(500,before.componentReferenceMinor());}
}
