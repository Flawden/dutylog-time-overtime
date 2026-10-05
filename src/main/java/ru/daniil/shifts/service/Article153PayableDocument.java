package ru.daniil.shifts.service;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import ru.daniil.shifts.model.*;
import static ru.daniil.shifts.service.Article153RemunerationDocument.check;
/** Booked HOLIDAY_PAY delta, bound to a new Payroll revision. History never reruns pricing. */
public final class Article153PayableDocument {
    public static final String SCHEMA="ARTICLE153_PAYABLE_SNAPSHOT_V1";
    private static final ObjectMapper JSON=JsonMapper.builder().enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
        .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES).disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
        .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS).enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION).build();
    public record Document(String schema,long snapshotId,String calculationHash,String remunerationFingerprint,String integrationFingerprint,
            long legacyPremiumMinor,long preservedNightMinor,long replacedHolidayMinor,String legacyPricingFingerprint,Article153PayableProjection.Outcome payable) {
        public Document {
            check(SCHEMA.equals(schema)&&snapshotId>0 && Article153RemunerationDocument.hash(calculationHash)
                && Article153RemunerationDocument.hash(remunerationFingerprint)&&Article153RemunerationDocument.hash(integrationFingerprint),"PAYABLE_HEADER");
            check(legacyPremiumMinor>=0&&preservedNightMinor>=0&&replacedHolidayMinor>=0
                &&Math.addExact(preservedNightMinor,replacedHolidayMinor)==legacyPremiumMinor,"PAYABLE_RECONCILIATION");
            check(legacyPricingFingerprint==null || Article153RemunerationDocument.hash(legacyPricingFingerprint),"PAYABLE_LEGACY_FINGERPRINT");
            java.util.Objects.requireNonNull(payable);
        }
    }
    static String encode(Document d){try{return JSON.writeValueAsString(d);}catch(Exception e){throw new IllegalStateException("PAYABLE_ENCODING",e);}}
    public static Document read(PayrollSnapshotArticle153Payable row){
        check(SCHEMA.equals(row.getSchemaVersion())&&Article153SnapshotCodec.fingerprint(row.getPayloadJson()).equals(row.getFingerprint()),"PAYABLE_HASH");
        var refs=row.getRemuneration();Article153RemunerationSnapshotDocument.read(refs);
        var snapshot=refs.getTariff().getAuthority().getSnapshot();
        var groups=Article153PayableProjection.groups(Article153SnapshotCodec.read(refs.getTariff().getAuthority()),snapshot.getOwner().getId(),
                snapshot.getPeriodMonth(),snapshot.getCurrencyCode(),Article153RemunerationDocument.read(refs.getReview()));
        try{
            var d=JSON.readValue(row.getPayloadJson(),Document.class);
            check(d.snapshotId()==snapshot.getId()&&d.calculationHash().equals(snapshot.getCalculationHash())
                &&d.remunerationFingerprint().equals(refs.getFingerprint())&&d.integrationFingerprint().equals(snapshot.getOrdinaryPremiumPricingFingerprint()),"PAYABLE_BINDING");
            var raw=(com.fasterxml.jackson.databind.node.ObjectNode)Article153SnapshotCodec.read(refs.getTariff().getAuthority()).deepCopy();raw.remove("snapshot");
            check(d.integrationFingerprint().equals(Article153PayrollIntegrationService.integrationFingerprint(Article153SnapshotCodec.encode(raw),
                    refs.getReview().getFingerprint(),d.legacyPricingFingerprint(),d.legacyPremiumMinor(),d.preservedNightMinor(),d.replacedHolidayMinor(),d.payable())),"PAYABLE_INTEGRATION_FINGERPRINT");
            check(d.payable().lines().size()==groups.size(),"PAYABLE_GROUP_COVERAGE");
            for(var line:d.payable().lines())check(line.sources().equals(groups.get(line.key())),"PAYABLE_SOURCE_BINDING");
            check(snapshot.getOrdinaryPremiumPayMinor()==Math.addExact(d.preservedNightMinor(),d.payable().holidayPayMinor()),"PAYABLE_BOOKED_TOTAL");
            return d;
        }catch(Exception e){throw new IllegalStateException("ARTICLE153_PAYABLE_INVALID",e);}
    }
    private Article153PayableDocument(){}
}
