package ru.daniil.shifts.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import ru.daniil.shifts.model.*;
import ru.daniil.shifts.repo.*;
import java.sql.Connection;
import java.util.*;
import static ru.daniil.shifts.service.Article153SnapshotCodec.*;

/** Reuses PayrollSnapshot revision identity; no live historical append/backfill API. */
@Service
public class Article153SnapshotFreezeService {
    private final Article153SnapshotCaptureService capture;
    private final PayrollSnapshotRepository snapshots;
    private final PayrollSnapshotArticle153Repository frozen;
    public Article153SnapshotFreezeService(Article153SnapshotCaptureService capture,
            PayrollSnapshotRepository snapshots, PayrollSnapshotArticle153Repository frozen) {
        this.capture = capture; this.snapshots = snapshots; this.frozen = frozen;
    }

    /** Root transaction must be repeatable-read (or serializable) when joined. */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public PayrollSnapshotArticle153 createRevision(PayrollSnapshot draft) {
        Objects.requireNonNull(draft);
        require(draft.getId() == null && draft.getSupersededBy() == null, "NEW_REVISION_REQUIRED");
        require(draft.getOwner() != null && draft.getOwner().getId() != null
                && draft.getOwner().getId() > 0 && draft.getPeriodMonth() != null
                && draft.getPeriodMonth().getDayOfMonth() == 1 && draft.getRevision() > 0
                && draft.getCalculationHash() != null && draft.getCalculationHash().matches("[0-9a-f]{64}")
                && draft.getSourcePeriodClosedAt() != null && draft.getSourceIntegrityCheckedAt() != null, "DRAFT_IDENTITY");
        Integer isolation = TransactionSynchronizationManager.getCurrentTransactionIsolationLevel();
        require(isolation != null && (isolation == Connection.TRANSACTION_REPEATABLE_READ
                || isolation == Connection.TRANSACTION_SERIALIZABLE), "TRANSACTION_ISOLATION");
        var document = capture.capture(draft); // All blockers precede writes.
        PayrollSnapshot saved = snapshots.saveAndFlush(draft);
        var header = document.putObject("snapshot");
        header.put("snapshotId", saved.getId()); header.put("ownerId", saved.getOwner().getId());
        header.put("periodMonth", saved.getPeriodMonth().toString()); header.put("revision", saved.getRevision());
        header.put("calculationHash", saved.getCalculationHash()); header.put("currencyCode", saved.getCurrencyCode());
        header.put("sourcePeriodClosedAt", saved.getSourcePeriodClosedAt().toString());
        header.put("sourceIntegrityCheckedAt", saved.getSourceIntegrityCheckedAt().toString());
        String json = encode(document);
        var row = new PayrollSnapshotArticle153(saved, SCHEMA, document.path("pieces").size(),
                document.path("qualifiedMinutes").asLong(), json, fingerprint(json));
        Article153SnapshotCodec.read(row);
        return frozen.saveAndFlush(row); // Failure rolls back parent and authority document together.
    }

    /** Missing means unknown/legacy, never an empty proven month. Only frozen data is read. */
    @Transactional(readOnly = true)
    public Optional<FrozenView> load(AppUser owner, Long snapshotId) {
        require(owner != null && owner.getId() != null && owner.getId() > 0
                && snapshotId != null && snapshotId > 0, "READ_IDENTITY");
        return frozen.findBySnapshot_IdAndSnapshot_Owner(snapshotId, owner).map(row -> {
            Article153SnapshotCodec.read(row);
            return new FrozenView(row.getSnapshotId(), row.getSchemaVersion(), row.getPieceCount(),
                    row.getQualifiedMinutes(), row.getFingerprint(), row.getPayloadJson());
        });
    }
    public record FrozenView(Long snapshotId, String schema, int pieceCount, long qualifiedMinutes,
            String fingerprint, String payloadJson) {}
}
