package ru.daniil.shifts.service;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.transaction.annotation.*;
import ru.daniil.shifts.model.*;
import ru.daniil.shifts.repo.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static ru.daniil.shifts.service.Article153SnapshotFixture.draft;

@SpringBootTest
@Transactional(isolation=Isolation.REPEATABLE_READ)
class Article153TariffSnapshotServiceTest {
    @Autowired Article153TariffSnapshotService service;
    @Autowired Article153SnapshotFreezeService c3;
    @Autowired PayrollSnapshotRepository snapshots;
    @Autowired PayrollSnapshotArticle153Repository authorities;
    @Autowired PayrollSnapshotArticle153TariffRepository tariffs;
    @Autowired UserRepository users;
    @Autowired EntityManager em;
    @MockBean Article153SnapshotCaptureService capture;
    @SpyBean Article153TariffValuationService valuation;
    AppUser owner;
    @BeforeEach void setup(){owner=users.saveAndFlush(new AppUser("c4-"+UUID.randomUUID(),"unused"));when(capture.capture(any())).thenAnswer(i->{var f=new Article153SnapshotFixture();return f.capture.capture(f.draft());});}
    @Test void parentEvidenceAndMoneyReferencesPersistTogether(){var row=service.createRevision(draft(owner,1));Long id=row.getSnapshotId();em.clear();var d=service.load(owner,id).orElseThrow();assertEquals(50000,d.statutoryTariffReferenceMinor());assertTrue(authorities.existsById(id));assertEquals(50000,snapshots.findById(id).orElseThrow().getTotalPayMinor());}
    @Test void historyCallsNeitherLiveCaptureNorPricing(){var row=service.createRevision(draft(owner,1));var before=service.load(owner,row.getSnapshotId()).orElseThrow();em.clear();reset(capture);clearInvocations(valuation);doThrow(new AssertionError("repricing")).when(valuation).value(any());assertEquals(before,service.load(owner,row.getSnapshotId()).orElseThrow());verifyNoInteractions(capture,valuation);}
    @Test void ownerIsolation(){var row=service.createRevision(draft(owner,1));var other=users.saveAndFlush(new AppUser("o-"+UUID.randomUUID(),"unused"));assertTrue(service.load(other,row.getSnapshotId()).isEmpty());}
    @Test void c3HistoryIsNotInventedAsZero(){var old=c3.createRevision(draft(owner,1));assertTrue(service.load(owner,old.getSnapshotId()).isEmpty());}
    @Test void existingSnapshotCannotBeRepriced(){var old=snapshots.saveAndFlush(draft(owner,1));assertThrows(IllegalStateException.class,()->service.createRevision(old));}
    @Test void revisionsRemainSeparate(){var first=service.createRevision(draft(owner,1));var second=service.createRevision(draft(owner,2));assertNotEquals(first.getFingerprint(),second.getFingerprint());assertTrue(service.load(owner,first.getSnapshotId()).isPresent());}
    @Test void corruptStoredReferenceFailsClosed(){var row=service.createRevision(draft(owner,1));em.createNativeQuery("update payroll_snapshot_article153_tariff set payload_json='{}' where snapshot_id=:id").setParameter("id",row.getSnapshotId()).executeUpdate();em.clear();assertThrows(IllegalStateException.class,()->service.load(owner,row.getSnapshotId()));}
    @Test void invalidReadIdentityRejected(){assertThrows(IllegalStateException.class,()->service.load(owner,0L));}
    @Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void valuationFailureRollsBackBothParents(){long count=snapshots.count(),sourceCount=authorities.count(),tariffCount=tariffs.count();doThrow(new IllegalStateException("overflow")).when(valuation).value(any());try{assertThrows(IllegalStateException.class,()->service.createRevision(draft(owner,1)));assertEquals(count,snapshots.count());assertEquals(sourceCount,authorities.count());assertEquals(tariffCount,tariffs.count());}finally{users.deleteById(owner.getId());}}
}
