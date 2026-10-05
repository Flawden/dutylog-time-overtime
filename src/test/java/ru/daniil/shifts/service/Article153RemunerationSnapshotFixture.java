package ru.daniil.shifts.service;
import com.fasterxml.jackson.databind.node.*;
import org.springframework.test.util.ReflectionTestUtils;
import ru.daniil.shifts.model.*;
import java.util.*;
import static ru.daniil.shifts.service.Article153RemunerationDocument.*;
final class Article153RemunerationSnapshotFixture {
    final Article153TariffFixture source=new Article153TariffFixture();
    final Source evidence=new Source(Article153ComponentAuthority.SourceKind.LOCAL_NORMATIVE_ACT,"LNA §5","r1","whole system and component monetary denominator reviewed");
    final PayPricingEngine pricing=new PayPricingEngine();
    final Article153RemunerationValuationService service=new Article153RemunerationValuationService(pricing);
    List<Rule> rules=new ArrayList<>();
    LocalRestDayRule localRule=LocalRestDayRule.NOT_APPLICABLE;
    void pieces(int...minutes){var pieces=new Article153MonthlyNormPositionAuthorityService.NormPositionPiece[minutes.length];int offset=0;
        for(int i=0;i<minutes.length;i++){pieces[i]=Article153SnapshotFixture.piece(11,offset,minutes[i],false);offset+=minutes[i];}
        source.live.ready(pieces);source.document=source.live.capture.capture(source.snapshot);
    }
    void component(long id,long amount,long periodMinutes,int enhanced,int rest){
        var r=new Rule(id,"a".repeat(64),"RUB",periodMinutes==60?Period.HOUR:Period.PAYROLL_MONTH,amount,periodMinutes,enhanced,rest,evidence);rules.add(r);
        for(var piece:source.document.path("pieces")){
            var c=((ArrayNode)piece.at("/components/components")).addObject();c.put("ownerId",1);c.put("versionId",id);c.put("componentFingerprint",r.componentFingerprint());c.put("decision","INCLUDE");
        }
    }
    void rest(int index){var p=(ObjectNode)source.document.path("pieces").get(index);((ObjectNode)p.path("election")).put("state","ELECTED");((ObjectNode)p.path("statutoryFloor")).put("compensationChoice","OTHER_REST_DAY");((ObjectNode)p.path("statutoryFloor")).put("additionalTariffBps",0);}
    PayrollSnapshotArticle153Tariff tariff(){var a=source.frozen();var d=new Article153TariffValuationService(pricing).value(a);String json=Article153TariffDocument.encode(d);return new PayrollSnapshotArticle153Tariff(a,Article153TariffDocument.SCHEMA,json,Article153SnapshotCodec.fingerprint(json));}
    Article153RemunerationAuthority review(){var d=new Document(SCHEMA,SCOPE,1,Article153SnapshotFixture.MONTH,"b".repeat(64),new Review("RUB",evidence,evidence,localRule,rules));String json=encode(d);var r=new Article153RemunerationAuthority(source.live.owner,Article153SnapshotFixture.MONTH,1,json,Article153SnapshotCodec.fingerprint(json));ReflectionTestUtils.setField(r,"id",501L);return r;}
    PayrollSnapshotArticle153Remuneration row(){var t=tariff();var r=review();var d=service.value(t,r);String json=Article153RemunerationSnapshotDocument.encode(d);return new PayrollSnapshotArticle153Remuneration(t,r,Article153RemunerationSnapshotDocument.SCHEMA,json,Article153SnapshotCodec.fingerprint(json));}
}
