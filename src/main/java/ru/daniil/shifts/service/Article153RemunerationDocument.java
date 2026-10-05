package ru.daniil.shifts.service;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import ru.daniil.shifts.model.Article153ComponentAuthority.SourceKind;
import ru.daniil.shifts.model.Article153RemunerationAuthority;
import java.time.LocalDate;
import java.util.*;

/** Typed source contract. Amounts are reviewed inputs, never inferred from a label. */
public final class Article153RemunerationDocument {
    public static final String SCHEMA="ARTICLE153_REMUNERATION_AUTHORITY_V1";
    public static final String SCOPE="SOURCE_REVIEW_ONLY";
    private static final ObjectMapper JSON=JsonMapper.builder().addModule(new JavaTimeModule())
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION).build();
    public enum Period { HOUR, PAYROLL_MONTH }
    public enum LocalRestDayRule { NOT_APPLICABLE, APPLIES }
    public record Source(SourceKind kind,String reference,String revision,String basis) {
        public Source { Objects.requireNonNull(kind);reference=text(reference,500);revision=text(revision,160);basis=text(basis,2000); }
    }
    /** Exactly this amount over this period; BPS are ADDITIONAL, separately for each employee choice. */
    public record Rule(long versionId,String componentFingerprint,String currencyCode,Period period,
            long periodAmountMinor,long periodMinutes,int enhancedAdditionalBps,int restDayAdditionalBps,Source source) {
        public Rule {
            check(versionId>0 && hash(componentFingerprint) && currencyCode!=null && currencyCode.matches("[A-Z]{3}"),"RULE_IDENTITY");
            Objects.requireNonNull(period);Objects.requireNonNull(source);
            check(periodAmountMinor>0 && periodAmountMinor<=1_000_000_000_000L && periodMinutes>0
                    && periodMinutes<=46_080 && (period!=Period.HOUR || periodMinutes==60)
                    && enhancedAdditionalBps>=0 && enhancedAdditionalBps<=10_000_000
                    && restDayAdditionalBps>=0 && restDayAdditionalBps<=10_000_000,"RULE_FORMULA");
        }
    }
    public record Review(String currencyCode,Source completeSystemSource,Source localRestDaySource,
            LocalRestDayRule localRestDayRule,List<Rule> rules) {
        public Review {
            check(currencyCode!=null && currencyCode.matches("[A-Z]{3}"),"CURRENCY");
            Objects.requireNonNull(completeSystemSource);Objects.requireNonNull(localRestDaySource);Objects.requireNonNull(localRestDayRule);
            Objects.requireNonNull(rules);
            rules=rules.stream().sorted(Comparator.comparingLong(Rule::versionId)).toList();
            Set<Long> seen=new HashSet<>();
            for(var rule:rules) check(rule.currencyCode().equals(currencyCode) && seen.add(rule.versionId()),"RULE_SET");
        }
    }
    public record Document(String schema,String scope,long ownerId,LocalDate periodMonth,String configurationFingerprint,Review review) {
        public Document {
            check(SCHEMA.equals(schema) && SCOPE.equals(scope) && ownerId>0 && periodMonth!=null
                    && periodMonth.getDayOfMonth()==1 && hash(configurationFingerprint),"DOCUMENT");
            Objects.requireNonNull(review);
        }
    }
    static String encode(Document document) {
        try{return JSON.writeValueAsString(document);}catch(Exception e){throw new IllegalStateException("ARTICLE153_REMUNERATION_ENCODING",e);}
    }
    public static Document read(Article153RemunerationAuthority row) {
        row.validate();
        check(Article153SnapshotCodec.fingerprint(row.getPayloadJson()).equals(row.getFingerprint()),"FINGERPRINT");
        try {
            var d=JSON.readValue(row.getPayloadJson(),Document.class);
            check(d.ownerId()==row.getOwner().getId() && d.periodMonth().equals(row.getPeriodMonth()),"BINDING");return d;
        }catch(Exception e){throw new IllegalStateException("ARTICLE153_REMUNERATION_INVALID",e);}
    }
    static boolean hash(String value){return value!=null && value.matches("[0-9a-f]{64}");}
    static String text(String value,int max){return ru.daniil.shifts.model.Article153ComponentAuthority.requireText(value,max);}
    static void check(boolean condition,String reason){if(!condition)throw new IllegalStateException("ARTICLE153_REMUNERATION_"+reason);}
    private Article153RemunerationDocument(){}
}
