package ru.daniil.shifts.service;

import com.fasterxml.jackson.databind.JsonNode;
import ru.daniil.shifts.model.PayrollSnapshotArticle153Remuneration;
import java.time.LocalDate;
import java.util.*;
import static ru.daniil.shifts.service.Article153RemunerationDocument.*;
import static ru.daniil.shifts.service.Article153RemunerationSnapshotDocument.*;
import static ru.daniil.shifts.service.Article153TariffDocument.Share;

/** Source-piece selection precedes economic grouping and the existing money engine's rounding. */
public final class Article153PayableProjection {
    public enum Part { TARIFF_PREMIUM, COMPONENT_PREMIUM }
    public record EconomicKey(Part part,long versionId,Period period,long amountMinor,long denominatorMinutes,int additionalBps) {
        public EconomicKey {
            Objects.requireNonNull(part);Objects.requireNonNull(period);
            check((part==Part.COMPONENT_PREMIUM ? versionId>0 : versionId==0 && period==Period.HOUR && denominatorMinutes==60)
                    && amountMinor>0 && denominatorMinutes>0 && additionalBps>=0,"PAYABLE_KEY");
        }
    }
    public record PayableLine(EconomicKey key,int minutes,long amountMinor,List<Share> sources) {
        public PayableLine {
            Objects.requireNonNull(key);sources=List.copyOf(sources);int sum=0;var indexes=new HashSet<Integer>();
            for(var s:sources){check(indexes.add(s.sourcePieceIndex()),"PAYABLE_DUPLICATE_SOURCE");sum=Math.addExact(sum,s.minutes());}
            check(minutes>0 && sum==minutes && amountMinor>=0,"PAYABLE_LINE");
        }
    }
    public record Outcome(long tariffPremiumMinor,long componentPremiumMinor,List<PayableLine> lines) {
        public Outcome {
            lines=List.copyOf(lines);long tariff=0,component=0;var keys=new HashSet<EconomicKey>();
            for(var line:lines){check(keys.add(line.key()),"PAYABLE_DUPLICATE_GROUP");
                if(line.key().part()==Part.TARIFF_PREMIUM)tariff=Math.addExact(tariff,line.amountMinor());
                else component=Math.addExact(component,line.amountMinor());}
            check(tariff==tariffPremiumMinor && component==componentPremiumMinor,"PAYABLE_TOTAL");
        }
        public long holidayPayMinor(){return Math.addExact(tariffPremiumMinor,componentPremiumMinor);}
    }
    public static Outcome value(PayrollSnapshotArticle153Remuneration frozen,PayPricingEngine pricing) {
        read(frozen);var snapshot=frozen.getTariff().getAuthority().getSnapshot();
        return value(Article153SnapshotCodec.read(frozen.getTariff().getAuthority()),snapshot.getOwner().getId(),
                snapshot.getPeriodMonth(),snapshot.getCurrencyCode(),Article153RemunerationDocument.read(frozen.getReview()),pricing);
    }
    static Outcome value(JsonNode source,long ownerId,LocalDate month,String currency,
            Article153RemunerationDocument.Document review,PayPricingEngine pricing) {
        var groups=groups(source,ownerId,month,currency,review);var lines=new ArrayList<PayableLine>();long tariff=0,component=0;
        for(var entry:groups.entrySet()){
            var key=entry.getKey();int minutes=0;for(var s:entry.getValue())minutes=Math.addExact(minutes,s.minutes());
            long money=pricing.pricePeriodPremium(key.amountMinor(),key.denominatorMinutes(),minutes,key.additionalBps());
            lines.add(new PayableLine(key,minutes,money,entry.getValue()));
            if(key.part()==Part.TARIFF_PREMIUM)tariff=Math.addExact(tariff,money);else component=Math.addExact(component,money);
        }
        return new Outcome(tariff,component,lines);
    }
    static Map<EconomicKey,List<Share>> groups(JsonNode source,long ownerId,LocalDate month,String currency,
            Article153RemunerationDocument.Document review) {
        // The B2 binder proves component coverage, choice, source dates and monetary contracts.
        var references=Article153RemunerationSnapshotDocument.groups(source,ownerId,month,currency,review);
        var floors=new HashMap<Integer,Key>();var locals=new HashMap<Integer,Key>();
        var result=new TreeMap<EconomicKey,List<Share>>(Comparator.comparing(EconomicKey::part)
                .thenComparingLong(EconomicKey::versionId).thenComparing(EconomicKey::period)
                .thenComparingLong(EconomicKey::amountMinor).thenComparingLong(EconomicKey::denominatorMinutes)
                .thenComparingInt(EconomicKey::additionalBps));
        for(var entry:references.entrySet()){
            var k=entry.getKey();
            if(k.kind()==Kind.COMPONENT_REFERENCE){
                var key=new EconomicKey(Part.COMPONENT_PREMIUM,k.versionId(),k.period(),k.periodAmountMinor(),k.periodMinutes(),k.additionalBps());
                result.put(key,List.copyOf(entry.getValue()));
            }else for(var s:entry.getValue()){
                var map=k.kind()==Kind.STATUTORY_TARIFF_REFERENCE?floors:locals;
                check(map.put(s.sourcePieceIndex(),k)==null,"PAYABLE_DUPLICATE_TARIFF_SOURCE");
            }
        }
        int index=0;
        for(var piece:source.path("pieces")){
            var floor=floors.get(index);var local=locals.get(index);
            check(floor!=null && local!=null && floor.periodAmountMinor()==local.periodAmountMinor()
                    && floor.periodMinutes()==local.periodMinutes(),"PAYABLE_ALTERNATIVE_BINDING");
            int minutes=Math.toIntExact(Article153TariffValuationService.number(piece.at("/norm/qualifiedPiece/sourcePiece/minutes")));
            var key=new EconomicKey(Part.TARIFF_PREMIUM,0,Period.HOUR,floor.periodAmountMinor(),60,
                    Math.max(floor.additionalBps(),local.additionalBps()));
            result.computeIfAbsent(key,k->new ArrayList<>()).add(new Share(index++,minutes));
        }
        var immutable=new LinkedHashMap<EconomicKey,List<Share>>();result.forEach((k,v)->immutable.put(k,List.copyOf(v)));
        return Collections.unmodifiableMap(immutable);
    }
    private Article153PayableProjection(){}
}
