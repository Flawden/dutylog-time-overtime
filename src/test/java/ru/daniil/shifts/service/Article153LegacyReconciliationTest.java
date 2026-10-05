package ru.daniil.shifts.service;
import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static ru.daniil.shifts.service.Article153SnapshotFixture.*;
import static ru.daniil.shifts.service.PayPricingRuleResolver.*;
class Article153LegacyReconciliationTest {
    final Rule holiday=new Rule("HOLIDAY",Dimension.HOLIDAY,10000,0,null,null);
    ObjectNode source(){var f=new Article153SnapshotFixture();var raw=f.capture.capture(f.draft());((ObjectNode)raw.path("pieces").get(0)).set("pricingRules",Article153SnapshotCodec.value(List.of(holiday)));return raw;}
    OrdinaryWorkPremiumPricingService.MonthPremiumProjection legacy(List<Rule> rules){
        var p=piece(11,0,60,false).qualifiedPiece().sourcePiece();var slices=new PayPricingRuleResolver().resolve(new RuleSet(rules),List.of(p.consumedSlice()));
        var price=new PayPricingEngine().price(50000,slices);
        var date=new OrdinaryWorkPremiumPricingService.SourceDateValuation(DATE,p.sourceKind(),60,0,60,MONTH,java.time.YearMonth.from(MONTH),MONTH,"HOURLY","RUB",50000,null,List.of(p),slices);
        return OrdinaryWorkPremiumPricingService.MonthPremiumProjection.ready(java.time.YearMonth.from(MONTH),"RUB",60,50000,price.premiumAmountMinor(),0,price.premiumAmountMinor(),List.of(),
                List.of(new OrdinaryWorkPremiumPricingService.PricedRateBucket(50000,60,50000,price.premiumAmountMinor(),price.premiums())),List.of(date));
    }
    @Test void provenHolidayCanBeRemoved(){assertDoesNotThrow(()->Article153LegacyReconciliation.validate(source(),legacy(List.of(holiday)),Map.of(DATE,new RuleSet(List.of(holiday)))));}
    @Test void unqualifiedCalendarFlagCannotBeRemoved(){var raw=source();((ArrayNode)raw.path("pieces")).removeAll();assertThrows(IllegalStateException.class,()->Article153LegacyReconciliation.validate(raw,legacy(List.of(holiday)),Map.of(DATE,new RuleSet(List.of(holiday)))));}
    @Test void duplicateQualifiedSourceRejected(){var raw=source();((ArrayNode)raw.path("pieces")).add(raw.path("pieces").get(0).deepCopy());assertThrows(IllegalStateException.class,()->Article153LegacyReconciliation.validate(raw,legacy(List.of(holiday)),Map.of(DATE,new RuleSet(List.of(holiday)))));}
    @Test void unrelatedSourceCannotReplaceLegacyPremium(){var raw=source();((ObjectNode)raw.at("/pieces/0/norm/qualifiedPiece/sourcePiece")).put("sourceActualWorkIntervalId",99);assertThrows(IllegalStateException.class,()->Article153LegacyReconciliation.validate(raw,legacy(List.of(holiday)),Map.of(DATE,new RuleSet(List.of(holiday)))));}
    @Test void missingPolicyIsNotZeroConfirmedHoliday(){assertThrows(IllegalStateException.class,()->Article153LegacyReconciliation.validate(source(),legacy(List.of(holiday)),Map.of()));}
    @Test void legacyAndCapturedRatesMustAgree(){var raw=source();((ObjectNode)raw.at("/pieces/0/historicalRate")).put("baseHourlyRateMinor",50001);assertThrows(IllegalStateException.class,()->Article153LegacyReconciliation.validate(raw,legacy(List.of(holiday)),Map.of(DATE,new RuleSet(List.of(holiday)))));}
    @Test void frozenLocalRuleIdentityMustAgree(){var raw=source();((ArrayNode)raw.path("pieces").get(0).path("pricingRules")).removeAll();assertThrows(IllegalStateException.class,()->Article153LegacyReconciliation.validate(raw,legacy(List.of(holiday)),Map.of(DATE,new RuleSet(List.of(holiday)))));}
    @Test void conflictingEconomicKeyDimensionCannotBeRelabelled(){var night=new Rule("HOLIDAY",Dimension.NIGHT,10000,0,null,null);assertThrows(IllegalStateException.class,()->Article153LegacyReconciliation.validate(source(),legacy(List.of(holiday)),Map.of(DATE,new RuleSet(List.of(holiday,night)))));}
    @Test void mixedExclusiveGroupCannotInventNightPreservation(){var h=new Rule("HOLIDAY",Dimension.HOLIDAY,10000,0,null,"mixed");var n=new Rule("NIGHT",Dimension.NIGHT,500,0,null,"mixed");assertThrows(IllegalStateException.class,()->Article153LegacyReconciliation.validate(source(),legacy(List.of(h,n)),Map.of(DATE,new RuleSet(List.of(h,n)))));}
}
