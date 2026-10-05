package ru.daniil.shifts.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import static ru.daniil.shifts.service.Article153RemunerationDocument.check;
import static ru.daniil.shifts.service.PayPricingRuleResolver.*;

/** A legacy aggregate is removable only after its dimension and canonical source coverage are proven. */
final class Article153LegacyReconciliation {
    static void validate(JsonNode captured,OrdinaryWorkPremiumPricingService.MonthPremiumProjection legacy,
            Map<java.time.LocalDate,RuleSet> rules) {
        check(legacy.ready(),"LEGACY_NOT_READY");
        var qualified=new HashSet<JsonNode>();
        for(var p:captured.path("pieces"))check(qualified.add(p.at("/norm/qualifiedPiece/sourcePiece")),"LEGACY_DUPLICATE_SOURCE");
        var seen=new HashSet<JsonNode>();var dimensions=new HashMap<String,Set<Dimension>>();
        for(var source:legacy.sources()){
            for(var p:source.sourcePieces()){
                var json=Article153SnapshotCodec.value(p);check(seen.add(json),"LEGACY_DUPLICATE_SOURCE");
                if(p.holiday())check(qualified.contains(json),"LEGACY_HOLIDAY_NOT_QUALIFIED");
            }
            var ruleSet=rules.get(source.sourceDate());
            if(ruleSet==null){check(source.pricingSlices().stream().allMatch(s->s.components().isEmpty()),"LEGACY_RULES_REQUIRED");continue;}
            var exclusive=new HashMap<String,Set<Dimension>>();
            for(var r:ruleSet.rules()){
                if(r.exclusiveGroup()!=null)exclusive.computeIfAbsent(r.exclusiveGroup(),g->new HashSet<>()).add(r.dimension());
                dimensions.computeIfAbsent(source.baseHourlyRateMinor()+":"+r.code()+":"+r.premiumBps(),k->new HashSet<>()).add(r.dimension());
            }
            for(var kinds:exclusive.values())check(!(kinds.contains(Dimension.NIGHT)&&kinds.contains(Dimension.HOLIDAY)),"LEGACY_MIXED_EXCLUSIVE_GROUP");
        }
        check(seen.containsAll(qualified),"QUALIFIED_SOURCE_NOT_IN_LEGACY");
        var valuations=new HashMap<java.time.LocalDate,OrdinaryWorkPremiumPricingService.SourceDateValuation>();
        for(var v:legacy.sources())check(valuations.put(v.sourceDate(),v)==null,"LEGACY_DUPLICATE_DATE");
        for(var piece:captured.path("pieces")){
            var date=java.time.LocalDate.parse(piece.at("/historicalRate/sourceDate").asText());var v=valuations.get(date);var policy=rules.get(date);
            check(v!=null&&v.baseHourlyRateMinor()==piece.at("/historicalRate/baseHourlyRateMinor").asLong()
                &&v.currencyCode().equals(piece.at("/historicalRate/currencyCode").asText()),"LEGACY_RATE_BINDING");
            check(policy!=null,"LEGACY_RULES_REQUIRED");
            var holidayRules=policy.rules().stream().filter(r->r.dimension()==Dimension.HOLIDAY).sorted(Comparator.comparing(Rule::code)).toList();
            check(Article153SnapshotCodec.value(holidayRules).equals(piece.path("pricingRules")),"LEGACY_HOLIDAY_POLICY_BINDING");
        }
        long night=0,holiday=0;
        for(var bucket:legacy.rateBuckets())for(var p:bucket.premiums()){
            var kinds=dimensions.get(bucket.baseHourlyRateMinor()+":"+p.code()+":"+p.premiumBps());
            check(kinds!=null && kinds.size()==1,"LEGACY_AMBIGUOUS_PREMIUM_KEY");
            if(kinds.contains(Dimension.NIGHT))night=Math.addExact(night,p.amountMinor());
            else {check(kinds.contains(Dimension.HOLIDAY),"LEGACY_OTHER_PREMIUM");holiday=Math.addExact(holiday,p.amountMinor());}
        }
        check(night==legacy.nightPremiumAmountMinor() && holiday==legacy.unclassifiedPremiumAmountMinor()
                && Math.addExact(night,holiday)==legacy.premiumAmountMinor(),"LEGACY_BREAKDOWN");
    }
    private Article153LegacyReconciliation(){}
}
