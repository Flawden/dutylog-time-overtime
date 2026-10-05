package ru.daniil.shifts.service;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.test.util.ReflectionTestUtils;
import ru.daniil.shifts.model.*;

final class Article153TariffFixture {
    final Article153SnapshotFixture live = new Article153SnapshotFixture();
    final PayrollSnapshot snapshot = live.draft();
    ObjectNode document;
    Article153TariffFixture() {
        ReflectionTestUtils.setField(snapshot, "id", 41L);
        document = live.capture.capture(snapshot);
    }
    PayrollSnapshotArticle153 frozen() {
        var h=document.putObject("snapshot");
        h.put("snapshotId", snapshot.getId()); h.put("ownerId", snapshot.getOwner().getId());
        h.put("revision", snapshot.getRevision()); h.put("periodMonth",snapshot.getPeriodMonth().toString());
        h.put("currencyCode", snapshot.getCurrencyCode()); h.put("calculationHash",snapshot.getCalculationHash());
        h.put("sourcePeriodClosedAt",snapshot.getSourcePeriodClosedAt().toString());
        h.put("sourceIntegrityCheckedAt",snapshot.getSourceIntegrityCheckedAt().toString());
        String json=Article153SnapshotCodec.encode(document);
        return new PayrollSnapshotArticle153(snapshot,Article153SnapshotCodec.SCHEMA,
                document.path("pieces").size(),document.path("qualifiedMinutes").asLong(),json,Article153SnapshotCodec.fingerprint(json));
    }
    void set(String path,String field,long value) { ((ObjectNode)document.at(path)).put(field,value); }
    void set(String path,String field,String value) { ((ObjectNode)document.at(path)).put(field,value); }
}
