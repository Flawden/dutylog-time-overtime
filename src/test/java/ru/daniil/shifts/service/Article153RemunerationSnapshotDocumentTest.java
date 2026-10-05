package ru.daniil.shifts.service;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import ru.daniil.shifts.model.*;
import static org.junit.jupiter.api.Assertions.*;
import static ru.daniil.shifts.service.Article153RemunerationSnapshotDocument.*;
class Article153RemunerationSnapshotDocumentTest {
    Article153RemunerationSnapshotFixture f=new Article153RemunerationSnapshotFixture();
    @Test void completeHistoryRoundTrips(){f.component(7,500,60,10000,0);var row=f.row();assertEquals(500,read(row).componentReferenceMinor());assertEquals(501,read(row).reviewId());assertEquals(1,read(row).reviewRevision());}
    @Test void corruptionWithoutRehashIsRejected(){var row=f.row();ReflectionTestUtils.setField(row,"payloadJson","{}");assertThrows(IllegalStateException.class,()->read(row));}
    @Test void changedReviewPayloadCannotRepriceHistory(){var row=f.row();ReflectionTestUtils.setField(row.getReview(),"payloadJson","{}");assertThrows(IllegalStateException.class,()->read(row));}
    @Test void changedParentTariffIsRejected(){var row=f.row();ReflectionTestUtils.setField(row.getTariff(),"payloadJson","{}");assertThrows(IllegalStateException.class,()->read(row));}
    @ParameterizedTest @ValueSource(strings={"unknown","duplicate","fractional","numericString","missing","null","trailing","activation","binding","sum","source","bps"})
    void invalidDocumentEvenWithRehash(String mutation){f.component(7,500,60,10000,0);var row=f.row();String original=row.getPayloadJson();String json=switch(mutation){
        case "unknown"->original.replaceFirst("\\{","{\"unknown\":1,");
        case "duplicate"->original.replace("\"snapshotId\":41","\"snapshotId\":41,\"snapshotId\":41");
        case "fractional"->original.replace("\"componentReferenceMinor\":500","\"componentReferenceMinor\":500.5");
        case "numericString"->original.replace("\"componentReferenceMinor\":500","\"componentReferenceMinor\":\"500\"");
        case "missing"->original.replace("\"reviewId\":501,","");
        case "null"->original.replace("\"reviewRevision\":1","\"reviewRevision\":null");
        case "trailing"->original+" {}";
        case "activation"->original.replace("\"finalPayableReady\":false","\"finalPayableReady\":true");
        case "binding"->original.replace("\"reviewId\":501","\"reviewId\":502");
        case "sum"->original.replace("\"componentReferenceMinor\":500","\"componentReferenceMinor\":501");
        case "source"->original.replace("\"sourcePieceIndex\":0","\"sourcePieceIndex\":1");
        default->original.replace("\"additionalBps\":10000","\"additionalBps\":10001");};
        assertNotEquals(original,json);ReflectionTestUtils.setField(row,"payloadJson",json);ReflectionTestUtils.setField(row,"fingerprint",Article153SnapshotCodec.fingerprint(json));assertThrows(IllegalStateException.class,()->read(row));}
}
