package ru.daniil.shifts.service;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import ru.daniil.shifts.dto.Dtos.ProductionCalendarMonthDto;
import ru.daniil.shifts.model.*;
import ru.daniil.shifts.repo.*;
import java.time.*;
import java.util.*;
import static ru.daniil.shifts.service.Article153RemunerationDocument.check;

/** New reviewed revisions opt into proven HOLIDAY money; historical/uncertified legacy money is never relabelled. */
@Service
public class Article153PayrollIntegrationService {
    private final Article153RemunerationAuthorityRepository reviews;
    private final Article153RemunerationAuthorityService authority;
    private final Article153SnapshotCaptureService capture;
    private final CompensationCalculationService base;
    private final OrdinaryWorkPremiumPricingService ordinary;
    private final PayPricingPolicyService policies;
    private final PayPricingEngine pricing;
    private final Article153RemunerationSnapshotService freeze;
    private final PayrollSnapshotArticle153PayableRepository saved;
    public Article153PayrollIntegrationService(Article153RemunerationAuthorityRepository reviews,Article153RemunerationAuthorityService authority,
            Article153SnapshotCaptureService capture,CompensationCalculationService base,OrdinaryWorkPremiumPricingService ordinary,
            PayPricingPolicyService policies,PayPricingEngine pricing,Article153RemunerationSnapshotService freeze,PayrollSnapshotArticle153PayableRepository saved){
        this.reviews=reviews;this.authority=authority;this.capture=capture;this.base=base;this.ordinary=ordinary;
        this.policies=policies;this.pricing=pricing;this.freeze=freeze;this.saved=saved;
    }
    public record Prepared(boolean active,boolean ready,String blockingReason,String capturedJson,long reviewId,String reviewFingerprint,
            long legacyPremiumMinor,long nightMinor,long replacedHolidayMinor,String legacyPricingFingerprint,String fingerprint,Article153PayableProjection.Outcome payable) {
        public static Prepared legacy(){return new Prepared(false,true,null,null,0,null,0,0,0,null,null,null);}
        static Prepared blocked(String reason){return new Prepared(true,false,reason,null,0,null,0,0,0,null,null,null);}
        public long premiumMinor(){check(active&&ready,"PREPARED_NOT_READY");return Math.addExact(nightMinor,payable.holidayPayMinor());}
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Prepared prepare(AppUser owner,YearMonth month,CompensationTerm term,ProductionCalendarMonthDto production,
            TimeCompensationService.PayrollSourceSnapshot source,PayrollOrdinaryPremiumPreviewService.OrdinaryPremiumPreview legacy){
        // No certificate means the legacy path remains explicitly unclassified; it never acquires HOLIDAY_PAY identity.
        if(reviews.findFirstByOwnerAndPeriodMonthOrderByRevisionDesc(owner,month.atDay(1)).isEmpty())return Prepared.legacy();
        Integer isolation=org.springframework.transaction.support.TransactionSynchronizationManager.getCurrentTransactionIsolationLevel();
        check(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive() && isolation!=null
                && isolation>=java.sql.Connection.TRANSACTION_REPEATABLE_READ,"REPEATABLE_READ_REQUIRED");
        if(term==null)return Prepared.blocked("PAYROLL_COMPENSATION_REQUIRED");
        if(!legacy.ready())return Prepared.blocked(legacy.blockingReason());
        if("SALARY".equals(term.getPayMode())&&(!production.scheduleCoverageComplete()||production.productionNormMinutes()<=0))return Prepared.blocked("PAYROLL_PRODUCTION_NORM_INCOMPLETE");
        var resolved=authority.resolve(owner,month.atDay(1));
        if(!resolved.ready())return Prepared.blocked("PAYROLL_ARTICLE153_"+(resolved.blockingReason().startsWith("DEPENDENCY_BLOCKED:") ? "SOURCE_BLOCKED" : resolved.blockingReason()));
        var fact=resolved.fact();
        var result=base.calculate(term,source,production.productionNormMinutes());
        var draft=new PayrollSnapshot(owner,month.atDay(1),1,term.getCurrencyCode(),result.effectiveHourlyRateMinor(),term.getPayMode(),
                term.getEffectiveFrom(),term.getHourlyRateMinor(),term.getMonthlySalaryMinor(),production.productionNormMinutes(),result.salaryCoveredMinutes(),
                source.plannedMinutes(),source.workedMinutes(),source.vacationMinutes(),source.sickMinutes(),source.overtimeCompensatedMinutes(),source.unpaidMinutes(),
                source.timeAdjustmentMinutes(),source.paidAbsenceMinutes(),source.payableMinutes(),source.hourlyBasePayableMinutes(),result.basePayMinor(),
                0,0,0L,0L,0L,null,0L,0L,result.basePayMinor(),Instant.EPOCH,Instant.EPOCH,"0".repeat(64));
        ObjectNode captured;
        try{captured=capture.capture(draft);}catch(IllegalStateException e){
            String message=e.getMessage();
            if(message!=null && (message.startsWith("ARTICLE153_SNAPSHOT_NORM_BLOCKED:")||message.startsWith("ARTICLE153_SNAPSHOT_ELECTION_BLOCKED:")
                    ||message.startsWith("ARTICLE153_SNAPSHOT_LOCAL_RATE_BLOCKED:")||message.startsWith("ARTICLE153_SNAPSHOT_COMPONENT_BLOCKED:")))
                return Prepared.blocked("PAYROLL_ARTICLE153_SOURCE_BLOCKED");
            throw e;
        }
        for(var p:captured.path("pieces")){
            var current=authority.resolve(owner,LocalDate.parse(p.at("/norm/qualifiedPiece/payrollDate").asText()));
            if(!current.ready()||!fact.equals(current.fact()))return Prepared.blocked("PAYROLL_ARTICLE153_REVIEW_DRIFT");
            for(var c:p.at("/components/components"))check(!"HOLIDAY_PAY".equals(c.at("/formula/earningKind").asText()),"DUPLICATE_GENERIC_HOLIDAY_PAY");
        }
        var legacyProjection=ordinary.priceMonth(owner,month);
        check(PayrollOrdinaryPremiumPreviewService.OrdinaryPremiumPreview.ready(legacyProjection).equals(legacy),"LEGACY_PREVIEW_DRIFT");
        var ruleSets=new HashMap<LocalDate,PayPricingRuleResolver.RuleSet>();
        for(var s:legacyProjection.sources())if(s.pricingEffectiveFrom()!=null){
            var policy=policies.resolveForSourceDate(owner,s.sourceDate(),s.sourcePieces().stream().map(p->p.consumedSlice()).toList());
            check(policy.pricingSlices().equals(s.pricingSlices()),"LEGACY_POLICY_DRIFT");ruleSets.put(s.sourceDate(),policy.rules());
        }
        try{Article153LegacyReconciliation.validate(captured,legacyProjection,ruleSets);}catch(IllegalStateException e){
            if(e.getMessage()!=null && (e.getMessage().startsWith("ARTICLE153_REMUNERATION_LEGACY_")
                ||e.getMessage().equals("ARTICLE153_REMUNERATION_QUALIFIED_SOURCE_NOT_IN_LEGACY")))return Prepared.blocked("PAYROLL_ARTICLE153_LEGACY_RECONCILIATION_REQUIRED");
            throw e;
        }
        var payable=Article153PayableProjection.value(captured,owner.getId(),month.atDay(1),term.getCurrencyCode(),fact.document(),pricing);
        String json=Article153SnapshotCodec.encode(captured);
        String fp=integrationFingerprint(json,fact.fingerprint(),legacy.pricingFingerprint(),legacy.premiumAmountMinor(),legacy.nightPremiumAmountMinor(),legacy.unclassifiedPremiumAmountMinor(),payable);
        return new Prepared(true,true,null,json,fact.authorityId(),fact.fingerprint(),legacy.premiumAmountMinor(),legacy.nightPremiumAmountMinor(),
                legacy.unclassifiedPremiumAmountMinor(),legacy.pricingFingerprint(),fp,payable);
    }
    static String integrationFingerprint(String source,String reviewFingerprint,String legacyFingerprint,long legacyPremium,long night,long replaced,Article153PayableProjection.Outcome payable) {
        return Article153SnapshotCodec.fingerprint("ARTICLE153_PAYROLL_INTEGRATION_V1|"+source+"|"+reviewFingerprint+"|"+legacyFingerprint+"|"+legacyPremium+"|"+night+"|"+replaced+"|"+Article153SnapshotCodec.value(payable));
    }
    static List<PayrollSemanticFreezeProjection.SemanticLine> semanticLines(Prepared prepared) {
        if (!prepared.active() || !prepared.ready() || prepared.payable().holidayPayMinor()==0) return List.of();
        try {
            var source = new com.fasterxml.jackson.databind.ObjectMapper().readTree(prepared.capturedJson());
            var dates = new TreeSet<LocalDate>();
            for(var piece:source.path("pieces"))dates.add(LocalDate.parse(piece.at("/norm/qualifiedPiece/payrollDate").asText()));
            check(!dates.isEmpty(),"PAYABLE_DATES");
            LocalDate date=dates.size()==1?dates.first():null;
            return List.of(new PayrollSemanticFreezeProjection.SemanticLine(PayrollEarningKind.HOLIDAY_PAY,prepared.payable().holidayPayMinor(),
                PayrollQualifiedQuantity.minutes(source.path("qualifiedMinutes").asLong()),date,date,null,null));
        } catch (java.io.IOException e) {throw new IllegalStateException("PAYABLE_SEMANTIC_SOURCE",e);}
    }
    @Transactional(isolation=Isolation.REPEATABLE_READ)
    public PayrollSnapshot createRevision(PayrollSnapshot draft,Prepared prepared){
        check(prepared.active()&&prepared.ready()&&draft.getId()==null,"PREPARED_NOT_READY");
        var refs=freeze.createRevision(draft);var captured=(ObjectNode)Article153SnapshotCodec.read(refs.getTariff().getAuthority()).deepCopy();captured.remove("snapshot");
        check(Article153SnapshotCodec.encode(captured).equals(prepared.capturedJson())&&refs.getReview().getId()==prepared.reviewId()
                &&refs.getReview().getFingerprint().equals(prepared.reviewFingerprint()),"PREPARED_FREEZE_DRIFT");
        var doc=new Article153PayableDocument.Document(Article153PayableDocument.SCHEMA,draft.getId(),draft.getCalculationHash(),refs.getFingerprint(),prepared.fingerprint(),
                prepared.legacyPremiumMinor(),prepared.nightMinor(),prepared.replacedHolidayMinor(),prepared.legacyPricingFingerprint(),prepared.payable());
        String json=Article153PayableDocument.encode(doc);
        var row=new PayrollSnapshotArticle153Payable(refs,Article153PayableDocument.SCHEMA,json,Article153SnapshotCodec.fingerprint(json));
        Article153PayableDocument.read(row);saved.saveAndFlush(row);return draft;
    }
    public static ru.daniil.shifts.dto.Dtos.PayrollArticle153Dto summary(Prepared prepared) {
        if (!prepared.active()) return legacySummary();
        if (!prepared.ready()) return new ru.daniil.shifts.dto.Dtos.PayrollArticle153Dto(
                "REVIEW_BLOCKED", prepared.blockingReason(), null, null, null, null);
        var source = Article153SnapshotCodec.tree(prepared.capturedJson());
        return reviewedSummary(source.path("qualifiedMinutes").asLong(), prepared.payable(), prepared.nightMinor());
    }
    public static ru.daniil.shifts.dto.Dtos.PayrollArticle153Dto legacySummary() {
        return new ru.daniil.shifts.dto.Dtos.PayrollArticle153Dto("LEGACY_UNREVIEWED", null, null, null, null, null);
    }
    private static ru.daniil.shifts.dto.Dtos.PayrollArticle153Dto reviewedSummary(long minutes, Article153PayableProjection.Outcome outcome, long night) {
        return new ru.daniil.shifts.dto.Dtos.PayrollArticle153Dto("REVIEWED", null, minutes,
                outcome.tariffPremiumMinor(), outcome.componentPremiumMinor(), night);
    }
    /** Frozen history reads only validated sidecars; live authorities and pricing are never used. */
    @Transactional(readOnly=true)
    public ru.daniil.shifts.dto.Dtos.PayrollArticle153Dto summary(AppUser owner, Long snapshotId) {
        check(owner!=null && owner.getId()!=null && owner.getId()>0 && snapshotId!=null && snapshotId>0,"READ_IDENTITY");
        return saved.findBySnapshotIdAndRemuneration_Tariff_Authority_Snapshot_Owner(snapshotId,owner).map(row -> {
            var document=Article153PayableDocument.read(row);
            var source=Article153SnapshotCodec.read(row.getRemuneration().getTariff().getAuthority());
            return reviewedSummary(source.path("qualifiedMinutes").asLong(), document.payable(), document.preservedNightMinor());
        }).orElseGet(Article153PayrollIntegrationService::legacySummary);
    }
    @Transactional(readOnly=true)
    public Optional<Article153PayableDocument.Document> load(AppUser owner,Long snapshotId){
        check(owner!=null&&owner.getId()!=null&&owner.getId()>0&&snapshotId!=null&&snapshotId>0,"READ_IDENTITY");
        return saved.findBySnapshotIdAndRemuneration_Tariff_Authority_Snapshot_Owner(snapshotId,owner).map(Article153PayableDocument::read);
    }
}
