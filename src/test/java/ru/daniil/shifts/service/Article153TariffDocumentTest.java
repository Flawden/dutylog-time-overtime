package ru.daniil.shifts.service;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import ru.daniil.shifts.model.*;
import static org.junit.jupiter.api.Assertions.*;
import static ru.daniil.shifts.service.Article153TariffDocument.*;

class Article153TariffDocumentTest {
    Article153TariffFixture f; ObjectNode json;
    @BeforeEach void setup() throws Exception {f=new Article153TariffFixture();json=(ObjectNode)new ObjectMapper().readTree(encode(new Article153TariffValuationService(new PayPricingEngine()).value(f.frozen())));}
    PayrollSnapshotArticle153Tariff row(String text){return new PayrollSnapshotArticle153Tariff(f.frozen(),SCHEMA,text,Article153SnapshotCodec.fingerprint(text));}
    void reject(){assertThrows(IllegalStateException.class,()->read(row(json.toString())));}
    @Test void roundTripKeepsFrozenAmounts(){var d=read(row(json.toString()));assertEquals(50000,d.statutoryTariffReferenceMinor());assertEquals(60,d.qualifiedMinutes());}
    @ParameterizedTest @ValueSource(strings={"schema","scope","roundingContract","calculationHash","authorityFingerprint","currencyCode"})
    void identityChangeRejected(String field){json.put(field,"invalid");reject();}
    @Test void otherValidSnapshotIdRejected(){json.put("snapshotId",42);reject();}
    @Test void validButWrongSourceHashRejected(){json.put("authorityFingerprint","b".repeat(64));reject();}
    @Test void validButWrongCurrencyRejected(){json.put("currencyCode","USD");reject();}
    @Test void inconsistentTotalRejected(){json.put("statutoryTariffReferenceMinor",50001);reject();}
    @Test void inconsistentMinutesRejected(){json.put("qualifiedMinutes",61);reject();}
    @Test void activationCannotBeClaimed(){json.put("finalPayableReady",true);reject();}
    @Test void blockersCannotBeOmitted(){json.putArray("activationBlockers");reject();}
    @Test void missingEconomicLineRejected(){((ArrayNode)json.path("lines")).remove(0);reject();}
    @Test void duplicateEconomicLineRejected(){((ArrayNode)json.path("lines")).add(json.at("/lines/0").deepCopy());reject();}
    @Test void wrongSourceShareRejected(){((ObjectNode)json.at("/lines/0/sources/0")).put("sourcePieceIndex",3);reject();}
    @Test void sourceRateMismatchRejected(){((ObjectNode)json.at("/lines/0")).put("hourlyRateMinor",123);reject();}
    @Test void sourceBpsMismatchRejected(){((ObjectNode)json.at("/lines/0")).put("additionalBps",123);reject();}
    @Test void payloadHashMismatchRejected(){var row=row(json.toString());org.springframework.test.util.ReflectionTestUtils.setField(row,"payloadJson","{}");assertThrows(IllegalStateException.class,()->read(row));}
    @Test void unknownFieldsRejected(){json.put("payableAmountMinor",50000);reject();}
    @Test void malformedJsonRejected(){assertThrows(IllegalStateException.class,()->read(row("{")));}
    @Test void trailingJsonRejected(){assertThrows(IllegalStateException.class,()->read(row(json+" {}")));}
    @Test void duplicateJsonKeysRejected(){assertThrows(IllegalStateException.class,()->read(row("{\"scope\":\"bad\","+json.toString().substring(1))));}
    @Test void explicitNullAmountRejected(){json.putNull("statutoryTariffReferenceMinor");reject();}
    @Test void returnedListsAreImmutable(){var d=read(row(json.toString()));assertThrows(UnsupportedOperationException.class,()->d.lines().clear());assertThrows(UnsupportedOperationException.class,()->d.lines().get(0).sources().clear());}
    @Test void missingZeroAmountIsNotDefaulted(){((ObjectNode)json.at("/lines/1")).remove("amountMinor");reject();}
    @Test void fractionalMoneyCannotBeTruncated(){json.put("statutoryTariffReferenceMinor",50000.4);reject();}
}
