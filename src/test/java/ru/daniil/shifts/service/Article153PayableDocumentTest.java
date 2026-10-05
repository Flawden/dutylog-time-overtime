package ru.daniil.shifts.service;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import ru.daniil.shifts.model.*;
import static org.junit.jupiter.api.Assertions.*;

class Article153PayableDocumentTest {
    PayrollSnapshotArticle153Payable row(){
        var f=new Article153RemunerationSnapshotFixture();f.pieces(60);var refs=f.row();
        var source=(ObjectNode)Article153SnapshotCodec.read(refs.getTariff().getAuthority()).deepCopy();source.remove("snapshot");
        var outcome=Article153PayableProjection.value(refs,new PayPricingEngine());var snapshot=refs.getTariff().getAuthority().getSnapshot();
        String fp=Article153PayrollIntegrationService.integrationFingerprint(Article153SnapshotCodec.encode(source),refs.getReview().getFingerprint(),"a".repeat(64),50000,0,50000,outcome);
        ReflectionTestUtils.setField(snapshot,"ordinaryPremiumPayMinor",50000L);ReflectionTestUtils.setField(snapshot,"ordinaryPremiumPricingFingerprint",fp);
        var doc=new Article153PayableDocument.Document(Article153PayableDocument.SCHEMA,snapshot.getId(),snapshot.getCalculationHash(),refs.getFingerprint(),fp,50000,0,50000,"a".repeat(64),outcome);
        var json=Article153PayableDocument.encode(doc);return new PayrollSnapshotArticle153Payable(refs,Article153PayableDocument.SCHEMA,json,Article153SnapshotCodec.fingerprint(json));
    }
    @Test void completeDocumentBindsBookedDeltaAndNeverChangesReferenceScope(){var r=row();assertEquals(50000,Article153PayableDocument.read(r).payable().holidayPayMinor());assertFalse(Article153RemunerationSnapshotDocument.read(r.getRemuneration()).finalPayableReady());}
    @ParameterizedTest @ValueSource(strings={"snapshot","hash","integration","source","legacy","money","key","unknown","numericString","null","missing","duplicate","trailing"})
    void typedIntegrityAndSourceBindingRejectMutatedDocuments(String mutation)throws Exception{
        var r=row();var json=(ObjectNode)new com.fasterxml.jackson.databind.ObjectMapper().readTree(r.getPayloadJson());
        switch(mutation){
            case "snapshot"->json.put("snapshotId",999);
            case "hash"->json.put("calculationHash","0".repeat(64));
            case "integration"->json.put("integrationFingerprint","0".repeat(64));
            case "source"->json.put("remunerationFingerprint","0".repeat(64));
            case "legacy"->{json.put("legacyPremiumMinor",50001);json.put("replacedHolidayMinor",50001);}
            case "money"->{((ObjectNode)json.path("payable")).put("tariffPremiumMinor",50001);((ObjectNode)json.at("/payable/lines/0")).put("amountMinor",50001);}
            case "key"->((ObjectNode)json.at("/payable/lines/0/key")).put("additionalBps",20000);
            case "unknown"->json.put("extra",true);
            case "numericString"->json.put("snapshotId",Long.toString(json.path("snapshotId").asLong()));
            case "null"->json.putNull("legacyPremiumMinor");
            case "missing"->json.remove("preservedNightMinor");
        }
        String text=json.toString();if(mutation.equals("duplicate"))text=text.replaceFirst("\\{","{\"legacyPremiumMinor\":50000,");if(mutation.equals("trailing"))text+=" {}";
        var changed=new PayrollSnapshotArticle153Payable(r.getRemuneration(),Article153PayableDocument.SCHEMA,text,Article153SnapshotCodec.fingerprint(text));
        assertThrows(IllegalStateException.class,()->Article153PayableDocument.read(changed));
    }
    @Test void hashMismatchRejected(){var r=row();ReflectionTestUtils.setField(r,"fingerprint","0".repeat(64));assertThrows(IllegalStateException.class,()->Article153PayableDocument.read(r));}
    @Test void bookedAggregateMismatchRejected(){var r=row();ReflectionTestUtils.setField(r.getRemuneration().getTariff().getAuthority().getSnapshot(),"ordinaryPremiumPayMinor",100000L);assertThrows(IllegalStateException.class,()->Article153PayableDocument.read(r));}
}
