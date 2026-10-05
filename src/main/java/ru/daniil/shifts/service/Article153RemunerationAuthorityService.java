package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.sql.Connection;
import ru.daniil.shifts.model.*;
import ru.daniil.shifts.repo.*;
import java.time.*;
import java.util.*;
import static ru.daniil.shifts.service.Article153RemunerationDocument.*;

/** Entire-system review gate. It does not certify legal truth or activate HOLIDAY_PAY. */
@Service
public class Article153RemunerationAuthorityService {
    private final Article153RemunerationAuthorityRepository authorities;
    private final UserRepository users;
    private final CompensationComponentResolverService inventory;
    private final Article153ComponentAuthorityService classifications;
    private final Article153LocalRateAuthorityService localRates;
    private final CompensationTermRepository compensation;
    public Article153RemunerationAuthorityService(Article153RemunerationAuthorityRepository authorities,UserRepository users,
            CompensationComponentResolverService inventory,Article153ComponentAuthorityService classifications,
            Article153LocalRateAuthorityService localRates,CompensationTermRepository compensation) {
        this.authorities=authorities;this.users=users;this.inventory=inventory;this.classifications=classifications;
        this.localRates=localRates;this.compensation=compensation;
    }
    /** expectedRevision=0 for first review; append-only corrections guarded by locked owner. */
    @Transactional(isolation=Isolation.REPEATABLE_READ)
    public Fact certify(AppUser owner,YearMonth month,int expectedRevision,Review review) {
        identity(owner,month);Objects.requireNonNull(review);check(expectedRevision>=0,"REVISION");
        Integer isolation=TransactionSynchronizationManager.getCurrentTransactionIsolationLevel();
        check(TransactionSynchronizationManager.isActualTransactionActive() && isolation!=null
                && isolation>=Connection.TRANSACTION_REPEATABLE_READ,"REPEATABLE_READ_REQUIRED");
        users.findForUpdateById(owner.getId()).orElseThrow(()->new IllegalStateException("ARTICLE153_REMUNERATION_OWNER"));
        var inputs=inputs(owner,month.atDay(1));
        validateReview(review,inputs);
        var d=new Document(SCHEMA,SCOPE,owner.getId(),month.atDay(1),inputs.fingerprint(),review);
        String json=encode(d);
        var previous=authorities.findFirstByOwnerAndPeriodMonthOrderByRevisionDesc(owner,month.atDay(1));
        if(previous.isPresent() && read(previous.get()).equals(d))return fact(previous.get());
        int current=previous.map(Article153RemunerationAuthority::getRevision).orElse(0);
        check(current==expectedRevision,"REVISION_CONFLICT");
        var row=new Article153RemunerationAuthority(owner,month.atDay(1),Math.incrementExact(current),json,Article153SnapshotCodec.fingerprint(json));
        return fact(authorities.saveAndFlush(row));
    }
    @Transactional(readOnly=true)
    public Resolution resolve(AppUser owner,LocalDate sourceDate) {
        Objects.requireNonNull(sourceDate);identity(owner,YearMonth.from(sourceDate));
        var row=authorities.findFirstByOwnerAndPeriodMonthOrderByRevisionDesc(owner,sourceDate.withDayOfMonth(1));
        if(row.isEmpty())return new Resolution(sourceDate,false,null,"SOURCE_REQUIRED");
        var f=fact(row.get());
        try {
            var current=inputs(owner,sourceDate);
            if(!current.fingerprint().equals(f.document().configurationFingerprint()))return new Resolution(sourceDate,false,null,"CONFIGURATION_CHANGED");
            validateReview(f.document().review(),current);
        } catch(IllegalStateException e) {return new Resolution(sourceDate,false,null,"DEPENDENCY_BLOCKED:"+e.getMessage());}
        return new Resolution(sourceDate,true,f,null);
    }
    /** Historical read has no live configuration lookup. */
    @Transactional(readOnly=true)
    public Optional<Fact> load(AppUser owner,Long authorityId) {
        identity(owner,YearMonth.of(2000,1));check(authorityId!=null && authorityId>0,"READ_IDENTITY");
        return authorities.findByIdAndOwner(authorityId,owner).map(Article153RemunerationAuthorityService::fact);
    }
    private Inputs inputs(AppUser owner,LocalDate date) {
        var month=YearMonth.from(date);
        var classified=classifications.resolve(owner,date);check(classified.ready(),"CLASSIFICATION_REQUIRED");
        var local=localRates.resolve(owner,date);check(local.ready(),"LOCAL_RATE_REQUIRED");
        var term=compensation.findFirstByOwnerAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(owner,month.atDay(1))
                .orElseThrow(()->new IllegalStateException("ARTICLE153_REMUNERATION_COMPENSATION_REQUIRED"));
        check(term.getId()!=null && term.getOwner().getId().equals(owner.getId()),"COMPENSATION_IDENTITY");
        var versions=inventory.resolve(owner,month);
        var tuples=new ArrayList<String>();var seen=new HashSet<Long>();var enabled=new HashSet<Long>();
        for(var v:versions) {
            check(!v.getEffectiveFrom().isAfter(date) && seen.add(v.getComponent().getId()) && v.getComponent().getOwner().getId().equals(owner.getId()),"INVENTORY");
            tuples.add(Article153ComponentAuthorityService.fingerprint(v));if(v.isEnabled())enabled.add(v.getId());
        }
        check(enabled.equals(classified.components().stream().map(Article153ComponentAuthorityService.AuthorityFact::versionId).collect(java.util.stream.Collectors.toSet())),"INVENTORY_CLASSIFICATION");
        Collections.sort(tuples);
        var canonical=Article153SnapshotCodec.object();
        canonical.put("schema","ARTICLE153_REMUNERATION_CONFIGURATION_V1");canonical.put("ownerId",owner.getId());canonical.put("month",month.toString());
        canonical.set("inventory",Article153SnapshotCodec.value(tuples));canonical.set("classifications",Article153SnapshotCodec.value(classified.components()));
        // Deliberately excludes sourceDate: one monthly contract may cover all dates with the same inputs.
        canonical.put("localPolicy",local.holidayPolicyFingerprint());canonical.put("localBps",local.configuredAdditionalTariffBps());
        canonical.put("localState",local.state().name());canonical.set("localAuthority",Article153SnapshotCodec.value(local.authority()));
        canonical.put("compensationId",term.getId());canonical.put("compensationEffectiveFrom",term.getEffectiveFrom().toString());
        canonical.put("payMode",term.getPayMode());canonical.put("currency",term.getCurrencyCode());
        canonical.set("hourlyRate",Article153SnapshotCodec.value(term.getHourlyRateMinor()));
        canonical.set("monthlySalary",Article153SnapshotCodec.value(term.getMonthlySalaryMinor()));
        return new Inputs(term.getCurrencyCode(),classified.components(),local.configuredAdditionalTariffBps(),
                Article153SnapshotCodec.fingerprint(Article153SnapshotCodec.encode(canonical)));
    }
    private static void validateReview(Review review,Inputs inputs) {
        check(review.currencyCode().equals(inputs.currency()),"CURRENCY_MISMATCH");
        var included=new HashMap<Long,Article153ComponentAuthorityService.AuthorityFact>();
        for(var c:inputs.components())if(c.decision()==Article153ComponentAuthority.Decision.INCLUDE)included.put(c.versionId(),c);
        check(review.rules().size()==included.size(),"COMPLETE_RULE_SET_REQUIRED");
        for(var rule:review.rules()) {
            var c=included.remove(rule.versionId());
            check(c!=null && c.componentFingerprint().equals(rule.componentFingerprint()),"RULE_BINDING");
        }
        check(included.isEmpty(),"COMPLETE_RULE_SET_REQUIRED");
        // A zero local policy does not need to claim applicability to a rest day.
        check(inputs.localBps()!=0 || review.localRestDayRule()==LocalRestDayRule.NOT_APPLICABLE,"ZERO_LOCAL_RULE");
    }
    private static Fact fact(Article153RemunerationAuthority row){return new Fact(row.getId(),row.getRevision(),row.getFingerprint(),row.getCertifiedAt(),read(row));}
    private static void identity(AppUser owner,YearMonth month){Objects.requireNonNull(month);check(owner!=null && owner.getId()!=null && owner.getId()>0,"OWNER_IDENTITY");}
    private record Inputs(String currency,List<Article153ComponentAuthorityService.AuthorityFact> components,int localBps,String fingerprint){}
    public record Fact(long authorityId,int revision,String fingerprint,Instant certifiedAt,Document document){}
    public record Resolution(LocalDate sourceDate,boolean ready,Fact fact,String blockingReason) {
        public Resolution {check(sourceDate!=null && (ready ? fact!=null && blockingReason==null : fact==null && blockingReason!=null),"RESOLUTION");}
    }
}
