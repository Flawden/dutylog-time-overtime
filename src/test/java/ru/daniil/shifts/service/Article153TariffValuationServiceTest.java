package ru.daniil.shifts.service;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import com.fasterxml.jackson.databind.node.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static ru.daniil.shifts.service.Article153TariffDocument.*;

class Article153TariffValuationServiceTest {
    Article153TariffFixture f;
    final Article153TariffValuationService service=new Article153TariffValuationService(new PayPricingEngine());
    @BeforeEach void setup(){f=new Article153TariffFixture();}
    @Test void pricesOnlyAdditionalTariffAndKeepsTwoReferencesSeparate(){
        f.set("/pieces/0/localRate","configuredAdditionalTariffBps",15000);
        var d=service.value(f.frozen());
        assertEquals(50000,d.statutoryTariffReferenceMinor()); assertEquals(75000,d.configuredLocalReferenceMinor());
        assertEquals(2,d.lines().size()); assertFalse(d.finalPayableReady());
        assertEquals(50000,f.snapshot.getTotalPayMinor());
    }
    @ParameterizedTest @ValueSource(ints={0,10000,20000})
    void consumesFrozenFloorWithoutRunningCurrentPolicy(int bps){f.set("/pieces/0/statutoryFloor","additionalTariffBps",bps);assertEquals(50000L*bps/10000,service.value(f.frozen()).statutoryTariffReferenceMinor());}
    @Test void usesAlreadyFrozenHourlyRate(){f.set("/pieces/0/historicalRate","baseHourlyRateMinor",33333);assertEquals(33333,service.value(f.frozen()).statutoryTariffReferenceMinor());}
    @Test void splitMinutesRoundOncePerEconomicIdentity(){
        f.set("/pieces/0/historicalRate","baseHourlyRateMinor",1);
        long whole=service.value(f.frozen()).statutoryTariffReferenceMinor();
        var first=(ObjectNode)f.document.at("/pieces/0");
        ((ObjectNode)first.at("/norm/qualifiedPiece/sourcePiece")).put("minutes",30);
        ((ArrayNode)f.document.path("pieces")).add(first.deepCopy());
        var split=service.value(f.frozen());assertEquals(whole,split.statutoryTariffReferenceMinor());assertEquals(1,whole);
        assertEquals(2,split.lines().get(0).sources().size());
    }
    @Test void differentBasisPointsNeverMerge(){
        var second=(ObjectNode)f.document.at("/pieces/0").deepCopy();((ObjectNode)second.path("statutoryFloor")).put("additionalTariffBps",20000);
        ((ArrayNode)f.document.path("pieces")).add(second);f.document.put("qualifiedMinutes",120);
        var d=service.value(f.frozen());assertEquals(150000,d.statutoryTariffReferenceMinor());assertEquals(3,d.lines().size());
    }
    @Test void halfMinorUnitRoundsUp(){f.set("/pieces/0/historicalRate","baseHourlyRateMinor",1);f.set("/pieces/0/norm/qualifiedPiece/sourcePiece","minutes",30);f.document.put("qualifiedMinutes",30);assertEquals(1,service.value(f.frozen()).statutoryTariffReferenceMinor());}
    @Test void bothCauseDoesNotDoubleQuantity(){f.set("/pieces/0/norm/qualifiedPiece","cause","BOTH");var d=service.value(f.frozen());assertEquals(60,d.qualifiedMinutes());assertEquals(50000,d.statutoryTariffReferenceMinor());}
    @Test void zeroQualifiedMonthHasExplicitReferencesAndRemainsUnactivated(){f.document.putArray("pieces");f.document.put("qualifiedMinutes",0);var d=service.value(f.frozen());assertTrue(d.lines().isEmpty());assertEquals(0,d.statutoryTariffReferenceMinor());assertFalse(d.finalPayableReady());}
    @Test void emptyComponentSetDoesNotProveEmployerCompleteness(){assertTrue(service.value(f.frozen()).activationBlockers().contains("REMUNERATION_COMPLETENESS_REQUIRED"));}
    @Test void includeRequiresMonetaryRuleInsteadOfInventedMultiplier(){((ArrayNode)f.document.at("/pieces/0/components/components")).addObject().put("decision","INCLUDE");assertTrue(service.value(f.frozen()).activationBlockers().contains("COMPONENT_MONETARY_RULES_REQUIRED"));}
    @Test void excludeDoesNotBecomeMoney(){((ArrayNode)f.document.at("/pieces/0/components/components")).addObject().put("decision","EXCLUDE");var d=service.value(f.frozen());assertEquals(50000,d.statutoryTariffReferenceMinor());assertFalse(d.activationBlockers().contains("COMPONENT_MONETARY_RULES_REQUIRED"));}
    @Test void restDayChoiceKeepsLocalApplicabilityUnresolved(){f.set("/pieces/0/election","state","ELECTED");f.set("/pieces/0/statutoryFloor","compensationChoice","OTHER_REST_DAY");f.set("/pieces/0/statutoryFloor","additionalTariffBps",0);f.set("/pieces/0/localRate","configuredAdditionalTariffBps",10000);var d=service.value(f.frozen());assertEquals(0,d.statutoryTariffReferenceMinor());assertTrue(d.activationBlockers().contains("LOCAL_RATE_REST_DAY_APPLICABILITY_REQUIRED"));}
    @Test void deterministicProjectionIncludesSourceFingerprint(){var row=f.frozen();assertEquals(encode(service.value(row)),encode(service.value(row)));assertEquals(row.getFingerprint(),service.value(row).authorityFingerprint());}
    @Test void corruptedAuthorityNeverProducesMoney(){var row=f.frozen();org.springframework.test.util.ReflectionTestUtils.setField(row,"payloadJson","{}");assertThrows(IllegalStateException.class,()->service.value(row));}
    @Test void currencyMismatchFailsClosed(){f.set("/pieces/0/historicalRate","currencyCode","USD");assertThrows(IllegalStateException.class,()->service.value(f.frozen()));}
    @Test void sourceDateMismatchFailsClosed(){f.set("/pieces/0/localRate","sourceDate","2026-05-10");assertThrows(IllegalStateException.class,()->service.value(f.frozen()));}
    @Test void choiceMismatchFailsClosed(){f.set("/pieces/0/statutoryFloor","compensationChoice","OTHER_REST_DAY");assertThrows(IllegalStateException.class,()->service.value(f.frozen()));}
    @Test void payModeMismatchFailsClosed(){f.set("/pieces/0/statutoryFloor","payMode","SALARY");assertThrows(IllegalStateException.class,()->service.value(f.frozen()));}
    @ParameterizedTest @ValueSource(strings={"statutoryFloor","localRate"})
    void missingBasisPointsAreNotDefaultedToZero(String section){((ObjectNode)f.document.at("/pieces/0/"+section)).remove(section.equals("statutoryFloor")?"additionalTariffBps":"configuredAdditionalTariffBps");assertThrows(IllegalStateException.class,()->service.value(f.frozen()));}
    @ParameterizedTest @ValueSource(longs={0,-1})
    void nonPositiveRateRejected(long rate){f.set("/pieces/0/historicalRate","baseHourlyRateMinor",rate);assertThrows(IllegalStateException.class,()->service.value(f.frozen()));}
    @Test void numericTextCannotSilentlyBecomeMoney(){f.set("/pieces/0/historicalRate","baseHourlyRateMinor","50000");assertThrows(IllegalStateException.class,()->service.value(f.frozen()));}
    @Test void negativePremiumRejected(){f.set("/pieces/0/statutoryFloor","additionalTariffBps",-1);assertThrows(IllegalStateException.class,()->service.value(f.frozen()));}
    @Test void overflowStopsProjection(){f.set("/pieces/0/historicalRate","baseHourlyRateMinor",Long.MAX_VALUE);assertThrows(RuntimeException.class,()->service.value(f.frozen()));}
    @Test void blockedComponentsRejected(){((ObjectNode)f.document.at("/pieces/0/components")).put("ready",false);assertThrows(IllegalStateException.class,()->service.value(f.frozen()));}
    @Test void unclassifiedComponentRejected(){((ArrayNode)f.document.at("/pieces/0/components/components")).addObject().put("decision","UNCLASSIFIED");assertThrows(IllegalStateException.class,()->service.value(f.frozen()));}
    @ParameterizedTest @CsvSource({"HOURLY,NOT_APPLICABLE,NONE,ENHANCED_PAY,10000", "HOURLY,NOT_APPLICABLE,ELECTED,OTHER_REST_DAY,0", "SALARY,WITHIN_MONTHLY_NORM,NONE,ENHANCED_PAY,10000", "SALARY,ABOVE_MONTHLY_NORM,NONE,ENHANCED_PAY,20000", "SALARY,WITHIN_MONTHLY_NORM,ELECTED,OTHER_REST_DAY,0", "SALARY,ABOVE_MONTHLY_NORM,ELECTED,OTHER_REST_DAY,10000"})
    void frozenBranchesDriveArithmetic(String mode,String norm,String election,String choice,int bps){
        f.set("/pieces/0/norm","payMode",mode);f.set("/pieces/0/norm","normPosition",norm);
        f.set("/pieces/0/statutoryFloor","payMode",mode);f.set("/pieces/0/statutoryFloor","normPosition",norm);
        f.set("/pieces/0/election","state",election);f.set("/pieces/0/statutoryFloor","compensationChoice",choice);
        f.set("/pieces/0/statutoryFloor","additionalTariffBps",bps);
        assertEquals(50000L*bps/10000,service.value(f.frozen()).statutoryTariffReferenceMinor());
    }
}
