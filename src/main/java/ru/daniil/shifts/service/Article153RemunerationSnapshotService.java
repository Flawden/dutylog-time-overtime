package ru.daniil.shifts.service;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import ru.daniil.shifts.model.*;
import ru.daniil.shifts.repo.*;
import java.time.*;
import java.util.*;
import static ru.daniil.shifts.service.Article153RemunerationSnapshotDocument.*;
import static ru.daniil.shifts.service.Article153RemunerationDocument.check;
@Service
public class Article153RemunerationSnapshotService {
    private final Article153TariffSnapshotService tariffs;
    private final Article153RemunerationAuthorityService reviews;
    private final Article153RemunerationAuthorityRepository reviewRows;
    private final Article153RemunerationValuationService valuation;
    private final PayrollSnapshotArticle153RemunerationRepository frozen;
    public Article153RemunerationSnapshotService(Article153TariffSnapshotService tariffs,Article153RemunerationAuthorityService reviews,
            Article153RemunerationAuthorityRepository reviewRows,Article153RemunerationValuationService valuation,
            PayrollSnapshotArticle153RemunerationRepository frozen){this.tariffs=tariffs;this.reviews=reviews;this.reviewRows=reviewRows;this.valuation=valuation;this.frozen=frozen;}
    @Transactional(isolation=Isolation.REPEATABLE_READ)
    public PayrollSnapshotArticle153Remuneration createRevision(PayrollSnapshot draft){
        Objects.requireNonNull(draft);
        var tariff=tariffs.createRevision(draft); // C3 enforces new revision and root transaction isolation.
        var owner=draft.getOwner();
        var resolved=reviews.resolve(owner,draft.getPeriodMonth());check(resolved.ready(),"REVIEW_REQUIRED");
        var fact=resolved.fact();
        var review=reviewRows.findByIdAndOwner(fact.authorityId(),owner).orElseThrow(()->new IllegalStateException("ARTICLE153_REMUNERATION_REVIEW_MISSING"));
        check(review.getFingerprint().equals(fact.fingerprint()) && review.getRevision()==fact.revision()
                && Article153RemunerationDocument.read(review).equals(fact.document()),"REVIEW_DRIFT");
        var source=Article153SnapshotCodec.read(tariff.getAuthority());
        for(var piece:source.path("pieces")){
            var date=LocalDate.parse(Article153TariffValuationService.text(piece.at("/norm/qualifiedPiece/payrollDate")));
            var current=reviews.resolve(owner,date);
            check(current.ready() && current.fact().equals(fact),"SOURCE_DATE_REVIEW_DRIFT");
        }
        String json=encode(valuation.value(tariff,review));
        var row=new PayrollSnapshotArticle153Remuneration(tariff,review,SCHEMA,json,Article153SnapshotCodec.fingerprint(json));
        read(row);return frozen.saveAndFlush(row);
    }
    @Transactional(readOnly=true)
    public Optional<Document> load(AppUser owner,Long snapshotId){
        check(owner!=null && owner.getId()!=null && owner.getId()>0 && snapshotId!=null && snapshotId>0,"READ_IDENTITY");
        return frozen.findBySnapshotIdAndTariff_Authority_Snapshot_Owner(snapshotId,owner).map(Article153RemunerationSnapshotDocument::read);
    }
}
