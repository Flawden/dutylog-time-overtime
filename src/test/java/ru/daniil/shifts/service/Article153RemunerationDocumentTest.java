package ru.daniil.shifts.service;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import ru.daniil.shifts.model.*;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static ru.daniil.shifts.service.Article153RemunerationDocument.*;
class Article153RemunerationDocumentTest {
    final AppUser owner=new AppUser("unit","unused");
    final Source source=new Source(Article153ComponentAuthority.SourceKind.LOCAL_NORMATIVE_ACT," LNA ","r1","basis");
    Article153RemunerationDocumentTest(){ReflectionTestUtils.setField(owner,"id",7L);}
    Rule rule(long id){return new Rule(id,"a".repeat(64),"RUB",Period.HOUR,100,60,10000,0,source);}
    Document doc(){return new Document(SCHEMA,SCOPE,7,LocalDate.of(2026,5,1),"b".repeat(64),new Review("RUB",source,source,LocalRestDayRule.NOT_APPLICABLE,List.of(rule(1))));}
    Article153RemunerationAuthority row(String json){return new Article153RemunerationAuthority(owner,LocalDate.of(2026,5,1),1,json,Article153SnapshotCodec.fingerprint(json));}
    @Test void exactRoundTrip(){assertEquals(doc(),read(row(encode(doc()))));assertEquals("LNA",source.reference());}
    @Test void deterministicOrderingAndDefensiveCopy(){var list=new ArrayList<>(List.of(rule(2),rule(1)));var r=new Review("RUB",source,source,LocalRestDayRule.NOT_APPLICABLE,list);list.clear();assertEquals(List.of(rule(1),rule(2)),r.rules());assertThrows(UnsupportedOperationException.class,()->r.rules().clear());}
    @Test void duplicateVersionRejected(){assertThrows(IllegalStateException.class,()->new Review("RUB",source,source,LocalRestDayRule.NOT_APPLICABLE,List.of(rule(1),rule(1))));}
    @ParameterizedTest @ValueSource(longs={-1,0,1000000000001L}) void invalidAmount(long n){assertThrows(IllegalStateException.class,()->new Rule(1,"a".repeat(64),"RUB",Period.HOUR,n,60,10000,0,source));}
    @ParameterizedTest @ValueSource(longs={-1,0,46081}) void invalidPeriod(long n){assertThrows(IllegalStateException.class,()->new Rule(1,"a".repeat(64),"RUB",Period.PAYROLL_MONTH,100,n,10000,0,source));}
    @ParameterizedTest @ValueSource(ints={-1,10000001}) void invalidMultiplier(int n){assertThrows(IllegalStateException.class,()->new Rule(1,"a".repeat(64),"RUB",Period.HOUR,100,60,n,0,source));assertThrows(IllegalStateException.class,()->new Rule(1,"a".repeat(64),"RUB",Period.HOUR,100,60,0,n,source));}
    @Test void hourRequiresExactlySixtyMinutes(){assertThrows(IllegalStateException.class,()->new Rule(1,"a".repeat(64),"RUB",Period.HOUR,100,30,10000,0,source));}
    @Test void tamperedHashRejected(){var r=row(encode(doc()));ReflectionTestUtils.setField(r,"fingerprint","c".repeat(64));assertThrows(IllegalStateException.class,()->read(r));}
    @Test void wrongOwnerRejectedEvenWithNewHash(){assertThrows(IllegalStateException.class,()->read(row(encode(doc()).replace("\"ownerId\":7","\"ownerId\":8"))));}
    @Test void wrongMonthRejectedEvenWithNewHash(){assertThrows(IllegalStateException.class,()->read(row(encode(doc()).replace("2026-05-01","2026-06-01"))));}
    @ParameterizedTest @ValueSource(strings={"unknown","numericString","fractional","nullSource","missingField","duplicateKey","trailing","schema"})
    void invalidWireContract(String mutation){String json=encode(doc());json=switch(mutation){
        case "unknown"->json.replaceFirst("\\{","{\"unexpected\":1,");
        case "numericString"->json.replace("\"periodAmountMinor\":100","\"periodAmountMinor\":\"100\"");
        case "fractional"->json.replace("\"periodAmountMinor\":100","\"periodAmountMinor\":100.5");
        case "nullSource"->json.replace("\"localRestDaySource\":{"+"\"basis\":\"basis\",\"kind\":\"LOCAL_NORMATIVE_ACT\",\"reference\":\"LNA\",\"revision\":\"r1\"}","\"localRestDaySource\":null");
        case "missingField"->json.replace("\"ownerId\":7,","");
        case "duplicateKey"->json.replace("\"ownerId\":7","\"ownerId\":7,\"ownerId\":7");
        case "trailing"->json+" {}";default->json.replace(SCHEMA,"OTHER");};
        assertNotEquals(encode(doc()),json);String invalid=json;assertThrows(IllegalStateException.class,()->read(row(invalid)));}
}
