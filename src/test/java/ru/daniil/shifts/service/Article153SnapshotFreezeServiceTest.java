package ru.daniil.shifts.service;

import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import ru.daniil.shifts.model.*;
import ru.daniil.shifts.repo.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static ru.daniil.shifts.service.Article153SnapshotFixture.*;

@SpringBootTest
@Transactional(isolation=Isolation.REPEATABLE_READ)
class Article153SnapshotFreezeServiceTest {
    @Autowired Article153SnapshotFreezeService service;
    @Autowired PayrollSnapshotRepository snapshots;
    @Autowired PayrollSnapshotArticle153Repository frozen;
    @Autowired UserRepository users;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager transactions;
    @MockBean Article153SnapshotCaptureService capture;
    AppUser owner;
    @BeforeEach void setup(){
        owner=users.saveAndFlush(new AppUser("c3-"+UUID.randomUUID(),"unused"));
        when(capture.capture(any())).thenAnswer(invocation->{var fixture=new Article153SnapshotFixture();return fixture.capture.capture(fixture.draft());});
    }
    @Test void persistedDocumentIsBoundToRevision(){
        var row=service.createRevision(draft(owner,1));Long id=row.getSnapshotId();em.clear();
        var view=service.load(owner,id).orElseThrow();assertEquals(1,view.pieceCount());assertEquals(60,view.qualifiedMinutes());
        var reread=frozen.findById(id).orElseThrow();var doc=Article153SnapshotCodec.read(reread);
        assertEquals(owner.getId().longValue(),doc.at("/snapshot/ownerId").asLong());assertEquals(id.longValue(),doc.at("/snapshot/snapshotId").asLong());
    }
    @Test void historicalReadDoesNotInvokeLiveCapture(){
        var row=service.createRevision(draft(owner,1));String json=row.getPayloadJson();em.clear();reset(capture);
        when(capture.capture(any())).thenThrow(new AssertionError("Live history read"));
        assertEquals(json,service.load(owner,row.getSnapshotId()).orElseThrow().payloadJson());verifyNoInteractions(capture);
    }
    @Test void ownerCannotReadAnotherOwnersRevision(){var row=service.createRevision(draft(owner,1));var other=users.saveAndFlush(new AppUser("o-"+UUID.randomUUID(),"unused"));assertTrue(service.load(other,row.getSnapshotId()).isEmpty());}
    @Test void legacyMissingEvidenceIsUnknown(){var old=snapshots.saveAndFlush(draft(owner,1));assertTrue(service.load(owner,old.getId()).isEmpty());verifyNoInteractions(capture);}
    @Test void existingRevisionCannotBeBackfilled(){var old=snapshots.saveAndFlush(draft(owner,1));assertThrows(IllegalStateException.class,()->service.createRevision(old));verifyNoInteractions(capture);}
    @Test void separateRevisionsHaveSeparateBindings(){var first=service.createRevision(draft(owner,1));var second=service.createRevision(draft(owner,2));assertNotEquals(first.getSnapshotId(),second.getSnapshotId());assertNotEquals(first.getFingerprint(),second.getFingerprint());assertEquals(1,Article153SnapshotCodec.read(first).at("/snapshot/revision").asInt());}
    @Test void blockedCaptureWritesNothing(){long count=snapshots.count();when(capture.capture(any())).thenThrow(new IllegalStateException("blocked"));assertThrows(IllegalStateException.class,()->service.createRevision(draft(owner,1)));assertEquals(count,snapshots.count());}
    @Test void invalidReadIdentityIsRejected(){assertThrows(IllegalStateException.class,()->service.load(owner,0L));}
    @Test void persistedTamperingIsDetected(){var row=service.createRevision(draft(owner,1));em.createNativeQuery("update payroll_snapshot_article153 set payload_json='{}' where snapshot_id=:id").setParameter("id",row.getSnapshotId()).executeUpdate();em.clear();assertThrows(IllegalStateException.class,()->service.load(owner,row.getSnapshotId()));}
    @Test void positiveEmptyEvidenceDiffersFromMissing(){var doc=Article153SnapshotCodec.object();doc.put("schema",Article153SnapshotCodec.SCHEMA);doc.put("scope","AUTHORITY_ONLY");doc.put("qualifiedMinutes",0);doc.putArray("pieces");when(capture.capture(any())).thenReturn(doc);var row=service.createRevision(draft(owner,1));assertEquals(0,service.load(owner,row.getSnapshotId()).orElseThrow().qualifiedMinutes());}
    @Test void closedPeriodEvidenceRequired(){var d=draft(owner,1);org.springframework.test.util.ReflectionTestUtils.setField(d,"sourcePeriodClosedAt",null);assertThrows(IllegalStateException.class,()->service.createRevision(d));verifyNoInteractions(capture);}
    @Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void malformedCaptureRollsBackAlreadyInsertedParent(){
        long before=snapshots.count();var malformed=Article153SnapshotCodec.object();malformed.put("schema",Article153SnapshotCodec.SCHEMA);malformed.put("scope","AUTHORITY_ONLY");malformed.put("qualifiedMinutes",99);malformed.putArray("pieces");when(capture.capture(any())).thenReturn(malformed);
        try {assertThrows(IllegalStateException.class,()->service.createRevision(draft(owner,1)));assertEquals(before,snapshots.count());}
        finally {users.deleteById(owner.getId());}
    }
    @Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void weakerJoinedTransactionIsRejected(){
        var tx=new TransactionTemplate(transactions);tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        try {assertThrows(IllegalStateException.class,()->tx.execute(status->service.createRevision(draft(owner,1))));verifyNoInteractions(capture);}
        finally {users.deleteById(owner.getId());}
    }
    @Test void subMicrosecondSourceTimestampsSurviveDatabaseRoundTrip(){
        var d=draft(owner,1);
        org.springframework.test.util.ReflectionTestUtils.setField(d,"sourcePeriodClosedAt",java.time.Instant.parse("2026-06-01T00:00:00.123456789Z"));
        org.springframework.test.util.ReflectionTestUtils.setField(d,"sourceIntegrityCheckedAt",java.time.Instant.parse("2026-06-01T00:01:00.999999789Z"));
        var row=service.createRevision(d);Long id=row.getSnapshotId();em.clear();
        assertTrue(service.load(owner,id).isPresent());
    }
}
