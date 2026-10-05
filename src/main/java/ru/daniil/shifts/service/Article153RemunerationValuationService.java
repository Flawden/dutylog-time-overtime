package ru.daniil.shifts.service;
import org.springframework.stereotype.Service;
import ru.daniil.shifts.model.*;
import java.util.*;
import static ru.daniil.shifts.service.Article153RemunerationSnapshotDocument.*;
@Service
public class Article153RemunerationValuationService {
    private final PayPricingEngine pricing;
    public Article153RemunerationValuationService(PayPricingEngine pricing){this.pricing=pricing;}
    public Document value(PayrollSnapshotArticle153Tariff tariff,Article153RemunerationAuthority review) {
        var groups=groups(tariff,review);var lines=new ArrayList<Line>();long floor=0,local=0,component=0;
        for(var entry:groups.entrySet()){
            var key=entry.getKey();int minutes=0;
            for(var share:entry.getValue())minutes=Math.addExact(minutes,share.minutes());
            long amount=pricing.pricePeriodPremium(key.periodAmountMinor(),key.periodMinutes(),minutes,key.additionalBps());
            lines.add(new Line(key,minutes,amount,entry.getValue()));
            switch(key.kind()){
                case STATUTORY_TARIFF_REFERENCE->floor=Math.addExact(floor,amount);
                case APPLICABLE_LOCAL_TARIFF_REFERENCE->local=Math.addExact(local,amount);
                case COMPONENT_REFERENCE->component=Math.addExact(component,amount);
            }
        }
        var authority=tariff.getAuthority();var snapshot=authority.getSnapshot();
        return new Document(SCHEMA,SCOPE,ROUNDING,snapshot.getId(),snapshot.getCalculationHash(),tariff.getFingerprint(),
                review.getId(),review.getRevision(),review.getFingerprint(),snapshot.getCurrencyCode(),authority.getPieceCount(),
                authority.getQualifiedMinutes(),floor,local,component,false,BLOCKERS,lines);
    }
}
