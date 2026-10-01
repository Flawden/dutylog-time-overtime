package ru.daniil.shifts.service;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.test.util.ReflectionTestUtils;
import ru.daniil.shifts.model.*;
import static org.junit.jupiter.api.Assertions.*;
import static ru.daniil.shifts.service.Article153SnapshotCodec.*;

class Article153SnapshotCodecTest {
    PayrollSnapshot snapshot; ObjectNode doc;
    @BeforeEach void setup(){var f=new Article153SnapshotFixture();snapshot=f.draft();ReflectionTestUtils.setField(snapshot,"id",41L);doc=f.capture.capture(snapshot);var h=doc.putObject("snapshot");h.put("snapshotId",41);h.put("ownerId",1);h.put("revision",1);h.put("periodMonth",snapshot.getPeriodMonth().toString());h.put("currencyCode","RUB");h.put("calculationHash",snapshot.getCalculationHash());h.put("sourcePeriodClosedAt",snapshot.getSourcePeriodClosedAt().toString());h.put("sourceIntegrityCheckedAt",snapshot.getSourceIntegrityCheckedAt().toString());}
    PayrollSnapshotArticle153 row(String json){return new PayrollSnapshotArticle153(snapshot,SCHEMA,1,60,json,fingerprint(json));}
    @Test void roundTripKeepsAllValues(){assertEquals(encode(doc),read(row(encode(doc))).toString());}
    @Test void hashRejectsChangedPayload(){var r=row(encode(doc));ReflectionTestUtils.setField(r,"payloadJson","{}");assertThrows(IllegalStateException.class,()->read(r));}
    @ParameterizedTest @ValueSource(strings={"snapshotId","ownerId","revision","periodMonth","currencyCode","calculationHash","sourcePeriodClosedAt","sourceIntegrityCheckedAt"})
    void headerMismatchIsRejected(String field){((ObjectNode)doc.path("snapshot")).put(field,"wrong");assertThrows(IllegalStateException.class,()->read(row(encode(doc))));}
    @Test void unsupportedSchemaIsRejected(){var r=row(encode(doc));ReflectionTestUtils.setField(r,"schemaVersion","FUTURE");assertThrows(IllegalStateException.class,()->read(r));}
    @Test void embeddedSchemaIsRequired(){doc.remove("schema");assertThrows(IllegalStateException.class,()->read(row(encode(doc))));}
    @Test void moneyScopeCannotMasqueradeAsAuthority(){doc.put("scope","FINAL_MONEY");assertThrows(IllegalStateException.class,()->read(row(encode(doc))));}
    @Test void mismatchedPieceCountRejected(){var r=row(encode(doc));ReflectionTestUtils.setField(r,"pieceCount",2);assertThrows(IllegalStateException.class,()->read(r));}
    @Test void mismatchedMinuteTotalRejected(){doc.put("qualifiedMinutes",61);assertThrows(IllegalStateException.class,()->read(row(encode(doc))));}
    @Test void nonPositivePieceMinutesRejected(){((ObjectNode)doc.at("/pieces/0/norm/qualifiedPiece/sourcePiece")).put("minutes",0);assertThrows(IllegalStateException.class,()->read(row(encode(doc))));}
    @ParameterizedTest @ValueSource(strings={"norm","statutoryFloor","compensationTerm","historicalRate","election","localRate","components","pricingRules"})
    void missingEvidenceIsRejected(String field){((ObjectNode)doc.at("/pieces/0")).remove(field);assertThrows(IllegalStateException.class,()->read(row(encode(doc))));}
    @Test void duplicateJsonKeysRejected(){String json=encode(doc).replaceFirst("\\{","{\"schema\":\"duplicate\",");assertThrows(IllegalStateException.class,()->read(row(json)));}
    @Test void trailingJsonRejected(){assertThrows(IllegalStateException.class,()->read(row(encode(doc)+" {}")));}
    @Test void malformedJsonRejected(){assertThrows(IllegalStateException.class,()->read(row("{")));}
    @Test void callersCannotMutateStoredDocument(){var r=row(encode(doc));((ObjectNode)read(r)).put("scope","changed");assertEquals("AUTHORITY_ONLY",read(r).path("scope").asText());}
}
