package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import ru.daniil.shifts.model.*;
import ru.daniil.shifts.repo.PayrollSnapshotArticle153TariffRepository;
import java.util.Optional;
import static ru.daniil.shifts.service.Article153TariffDocument.*;

@Service
public class Article153TariffSnapshotService {
    private final Article153SnapshotFreezeService authorityFreeze;
    private final Article153TariffValuationService valuation;
    private final PayrollSnapshotArticle153TariffRepository tariffs;
    public Article153TariffSnapshotService(Article153SnapshotFreezeService authorityFreeze,
            Article153TariffValuationService valuation, PayrollSnapshotArticle153TariffRepository tariffs) {
        this.authorityFreeze = authorityFreeze; this.valuation = valuation; this.tariffs = tariffs;
    }
    /** Only new revisions; parent, C3 evidence and tariff references share one transaction. */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public PayrollSnapshotArticle153Tariff createRevision(PayrollSnapshot draft) {
        var authority = authorityFreeze.createRevision(draft);
        String json = encode(valuation.value(authority));
        var row = new PayrollSnapshotArticle153Tariff(authority, SCHEMA, json, Article153SnapshotCodec.fingerprint(json));
        read(row);
        return tariffs.saveAndFlush(row);
    }
    /** No live source queries, policy resolution or pricing engine on history reads. */
    @Transactional(readOnly = true)
    public Optional<Document> load(AppUser owner, Long snapshotId) {
        check(owner != null && owner.getId() != null && owner.getId() > 0
                && snapshotId != null && snapshotId > 0, "READ_IDENTITY");
        return tariffs.findBySnapshotIdAndAuthority_Snapshot_Owner(snapshotId, owner).map(Article153TariffDocument::read);
    }
}
