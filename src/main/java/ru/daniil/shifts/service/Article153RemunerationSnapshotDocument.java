package ru.daniil.shifts.service;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import ru.daniil.shifts.model.*;
import java.time.LocalDate;
import java.util.*;
import static ru.daniil.shifts.service.Article153RemunerationDocument.check;
import static ru.daniil.shifts.service.Article153RemunerationDocument.Period;
import static ru.daniil.shifts.service.Article153TariffDocument.Share;
import static ru.daniil.shifts.service.Article153TariffValuationService.number;
import static ru.daniil.shifts.service.Article153TariffValuationService.text;

/** Complete unbooked money references. Reads bind evidence and sums without rerunning pricing. */
public final class Article153RemunerationSnapshotDocument {
    public static final String SCHEMA="ARTICLE153_REMUNERATION_SNAPSHOT_V1";
    public static final String SCOPE="REMUNERATION_REFERENCE_ONLY";
    public static final String ROUNDING="PAY_PRICING_PERIOD_BPS_HALF_UP_V1";
    public static final List<String> BLOCKERS=List.of("FINAL_PAYROLL_INTEGRATION_REQUIRED",
            "LEGACY_HOLIDAY_RECONCILIATION_REQUIRED","DOWNSTREAM_EARNING_BASES_REQUIRED");
    private static final ObjectMapper JSON=JsonMapper.builder()
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION).build();
    public enum Kind { STATUTORY_TARIFF_REFERENCE, APPLICABLE_LOCAL_TARIFF_REFERENCE, COMPONENT_REFERENCE }
    public record Key(Kind kind,long versionId,Period period,long periodAmountMinor,long periodMinutes,int additionalBps) {
        public Key {
            Objects.requireNonNull(kind);Objects.requireNonNull(period);
            check((kind==Kind.COMPONENT_REFERENCE ? versionId>0 : versionId==0 && period==Period.HOUR && periodMinutes==60)
                    && periodAmountMinor>0 && periodMinutes>0 && additionalBps>=0,"LINE_KEY");
        }
    }
    public record Line(Key key,int minutes,long amountMinor,List<Share> sources) {
        public Line {
            Objects.requireNonNull(key);sources=List.copyOf(sources);
            int sum=0;var seen=new HashSet<Integer>();
            for(var share:sources){check(seen.add(share.sourcePieceIndex()),"DUPLICATE_SHARE");sum=Math.addExact(sum,share.minutes());}
            check(minutes>0 && sum==minutes && amountMinor>=0,"LINE");
        }
    }
    public record Document(String schema,String scope,String roundingContract,long snapshotId,String calculationHash,
            String tariffFingerprint,long reviewId,int reviewRevision,String reviewFingerprint,String currencyCode,
            int pieceCount,long qualifiedMinutes,long statutoryTariffReferenceMinor,long applicableLocalTariffReferenceMinor,
            long componentReferenceMinor,boolean finalPayableReady,List<String> activationBlockers,List<Line> lines) {
        public Document {
            check(SCHEMA.equals(schema) && SCOPE.equals(scope) && ROUNDING.equals(roundingContract),"SCHEMA");
            check(snapshotId>0 && reviewId>0 && reviewRevision>0 && Article153RemunerationDocument.hash(calculationHash)
                    && Article153RemunerationDocument.hash(tariffFingerprint) && Article153RemunerationDocument.hash(reviewFingerprint)
                    && currencyCode!=null && currencyCode.matches("[A-Z]{3}") && pieceCount>=0 && qualifiedMinutes>=0,"HEADER");
            activationBlockers=List.copyOf(activationBlockers);lines=List.copyOf(lines);
            check(!finalPayableReady && activationBlockers.equals(BLOCKERS),"ACTIVATION");
            var keys=new HashSet<Key>();long floor=0,local=0,component=0;
            for(var line:lines){
                check(keys.add(line.key()),"DUPLICATE_ECONOMIC_GROUP");
                for(var share:line.sources())check(share.sourcePieceIndex()<pieceCount,"SOURCE_INDEX");
                switch(line.key().kind()){
                    case STATUTORY_TARIFF_REFERENCE->floor=Math.addExact(floor,line.amountMinor());
                    case APPLICABLE_LOCAL_TARIFF_REFERENCE->local=Math.addExact(local,line.amountMinor());
                    case COMPONENT_REFERENCE->component=Math.addExact(component,line.amountMinor());
                }
            }
            check(floor==statutoryTariffReferenceMinor && local==applicableLocalTariffReferenceMinor && component==componentReferenceMinor,"TOTAL");
        }
    }
    public static String encode(Document doc){try{return JSON.writeValueAsString(doc);}catch(Exception e){throw new IllegalStateException("ARTICLE153_REMUNERATION_ENCODING",e);}}
    public static Document read(PayrollSnapshotArticle153Remuneration row) {
        check(SCHEMA.equals(row.getSchemaVersion()) && Article153SnapshotCodec.fingerprint(row.getPayloadJson()).equals(row.getFingerprint()),"FINGERPRINT_OR_SCHEMA");
        var tariff=row.getTariff();var authority=tariff.getAuthority();var snapshot=authority.getSnapshot();var review=row.getReview();
        var expected=groups(tariff,review);
        try {
            var d=JSON.readValue(row.getPayloadJson(),Document.class);
            check(d.snapshotId()==snapshot.getId() && d.calculationHash().equals(snapshot.getCalculationHash())
                    && d.tariffFingerprint().equals(tariff.getFingerprint()) && d.reviewId()==review.getId()
                    && d.reviewRevision()==review.getRevision() && d.reviewFingerprint().equals(review.getFingerprint())
                    && d.currencyCode().equals(snapshot.getCurrencyCode()) && d.pieceCount()==authority.getPieceCount()
                    && d.qualifiedMinutes()==authority.getQualifiedMinutes(),"BINDING");
            check(d.lines().size()==expected.size(),"GROUP_COVERAGE");
            for(var line:d.lines())check(line.sources().equals(expected.get(line.key())),"SOURCE_BINDING");
            return d;
        }catch(Exception e){throw new IllegalStateException("ARTICLE153_REMUNERATION_SNAPSHOT_INVALID",e);}
    }
    /** One deterministic economic grouping, shared by initial pricing and historical binding only. */
    static Map<Key,List<Share>> groups(PayrollSnapshotArticle153Tariff tariff,Article153RemunerationAuthority review) {
        Article153TariffDocument.read(tariff);
        var contract=Article153RemunerationDocument.read(review);
        var snapshot=tariff.getAuthority().getSnapshot();
        check(review.getId()!=null && review.getId()>0 && contract.ownerId()==snapshot.getOwner().getId()
                && contract.periodMonth().equals(snapshot.getPeriodMonth())
                && contract.review().currencyCode().equals(snapshot.getCurrencyCode()),"REVIEW_BINDING");
        return groups(Article153SnapshotCodec.read(tariff.getAuthority()), snapshot.getOwner().getId(), snapshot.getPeriodMonth(), snapshot.getCurrencyCode(), contract);
    }
    static Map<Key,List<Share>> groups(JsonNode source, long ownerId, LocalDate periodMonth, String currencyCode,
            Article153RemunerationDocument.Document contract) {
        check(contract.ownerId()==ownerId && contract.periodMonth().equals(periodMonth)
                && contract.review().currencyCode().equals(currencyCode),"REVIEW_BINDING");
        var rules=new HashMap<Long,Article153RemunerationDocument.Rule>();
        for(var rule:contract.review().rules())rules.put(rule.versionId(),rule);
        var result=new TreeMap<Key,List<Share>>(Comparator.comparing(Key::kind).thenComparingLong(Key::versionId)
                .thenComparing(Key::period).thenComparingLong(Key::periodAmountMinor).thenComparingLong(Key::periodMinutes).thenComparingInt(Key::additionalBps));
        int index=0;
        for(var piece:source.path("pieces")){
            Article153TariffValuationService.validatePiece(piece,currencyCode);
            check(LocalDate.parse(text(piece.at("/norm/qualifiedPiece/payrollDate"))).withDayOfMonth(1).equals(periodMonth),"PIECE_MONTH");
            int minutes=Math.toIntExact(number(piece.at("/norm/qualifiedPiece/sourcePiece/minutes")));
            var share=new Share(index++,minutes);
            boolean rest="OTHER_REST_DAY".equals(text(piece.at("/statutoryFloor/compensationChoice")));
            long rate=number(piece.at("/historicalRate/baseHourlyRateMinor"));
            int floor=Math.toIntExact(number(piece.at("/statutoryFloor/additionalTariffBps")));
            int local=Math.toIntExact(number(piece.at("/localRate/configuredAdditionalTariffBps")));
            if(rest && contract.review().localRestDayRule()==Article153RemunerationDocument.LocalRestDayRule.NOT_APPLICABLE)local=0;
            add(result,new Key(Kind.STATUTORY_TARIFF_REFERENCE,0,Period.HOUR,rate,60,floor),share);
            add(result,new Key(Kind.APPLICABLE_LOCAL_TARIFF_REFERENCE,0,Period.HOUR,rate,60,local),share);
            var included=new HashSet<Long>();var allVersions=new HashSet<Long>();
            for(var c:piece.at("/components/components")){
                long id=number(c.path("versionId"));
                check(id>0 && allVersions.add(id) && number(c.path("ownerId"))==ownerId,"COMPONENT_IDENTITY");
                if("EXCLUDE".equals(text(c.path("decision")))){check(!rules.containsKey(id),"EXCLUDED_RULE");continue;}
                var r=rules.get(id);
                check(r!=null && r.componentFingerprint().equals(text(c.path("componentFingerprint"))),"COMPONENT_RULE_REQUIRED");
                included.add(id);
                add(result,new Key(Kind.COMPONENT_REFERENCE,id,r.period(),r.periodAmountMinor(),r.periodMinutes(),
                        rest?r.restDayAdditionalBps():r.enhancedAdditionalBps()),share);
            }
            check(included.equals(rules.keySet()),"COMPLETE_COMPONENT_RULE_SET");
        }
        return result;
    }
    private static void add(Map<Key,List<Share>> map,Key key,Share share){map.computeIfAbsent(key,k->new ArrayList<>()).add(share);}
    private Article153RemunerationSnapshotDocument(){}
}
