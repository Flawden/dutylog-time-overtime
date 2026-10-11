package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import ru.daniil.shifts.model.*;
import ru.daniil.shifts.repo.*;
import ru.daniil.shifts.service.exception.ApiException;
import java.time.*;
import java.util.*;
import static ru.daniil.shifts.service.Article153RemunerationDocument.*;

/** Owner-scoped source entry. No inferred sources, rates or classification decisions. */
@Service
public class Article153ReviewWorkspaceService {
    private final UserRepository users;
    private final CompensationComponentResolverService inventory;
    private final Article153ComponentAuthorityRepository componentRows;
    private final Article153ComponentAuthorityService components;
    private final Article153LocalRateAuthorityService local;
    private final PayPricingTermRepository pricing;
    private final CompensationTermRepository compensation;
    private final Article153RemunerationAuthorityRepository reviews;
    private final Article153RemunerationAuthorityService remuneration;
    public Article153ReviewWorkspaceService(UserRepository users, CompensationComponentResolverService inventory,
            Article153ComponentAuthorityRepository componentRows, Article153ComponentAuthorityService components,
            Article153LocalRateAuthorityService local, PayPricingTermRepository pricing,
            CompensationTermRepository compensation, Article153RemunerationAuthorityRepository reviews,
            Article153RemunerationAuthorityService remuneration) {
        this.users=users; this.inventory=inventory; this.componentRows=componentRows; this.components=components;
        this.local=local; this.pricing=pricing; this.compensation=compensation; this.reviews=reviews; this.remuneration=remuneration;
    }
    public record LocalSource(Article153ComponentAuthority.SourceKind kind,String reference,String revision) {}
    public record Component(long versionId,String name,String fingerprint,String currencyCode,
            Article153ComponentAuthority.Decision decision,Source source,boolean locked) {}
    public record Workspace(String month,String configurationToken,String currencyCode,int revision,
            LocalDate pricingEffectiveFrom,boolean localSourceRequired,LocalSource localSource,
            List<Component> components,Review savedReview,boolean ready,String blockingReason) {}
    public record ComponentInput(long versionId,String fingerprint,Article153ComponentAuthority.Decision decision,Source source) {}
    public record Submission(String configurationToken,int expectedRevision,LocalSource localSource,
            List<ComponentInput> components,Review review) {}

    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Workspace read(AppUser owner,String month) {
        YearMonth m=month(month); var date=m.atDay(1);
        var term=compensation.findFirstByOwnerAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(owner,date).orElse(null);
        var p=pricing.findFirstByOwnerAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(owner,date).orElse(null);
        var rate=local.resolve(owner,date);
        var entries=new ArrayList<Component>();
        for(var v:inventory.resolve(owner,m)) {
            if(!v.isEnabled())continue;
            var row=componentRows.findByComponentVersion(v).orElse(null);
            entries.add(new Component(v.getId(),v.getDisplayName(),Article153ComponentAuthorityService.fingerprint(v),
                    v.getCurrencyCode(),row==null?null:row.getDecision(),row==null?null:
                    new Source(row.getSourceKind(),row.getSourceReference(),row.getSourceRevision(),row.getClassificationBasis()),row!=null));
        }
        var saved=reviews.findFirstByOwnerAndPeriodMonthOrderByRevisionDesc(owner,date).orElse(null);
        var doc=saved==null?null:Article153RemunerationDocument.read(saved);
        var resolution=remuneration.resolve(owner,date);
        String currency=term==null?null:term.getCurrencyCode();
        // Full effective inventory (including disabled), compensation, pricing and authority revisions bind the form.
        var identity=Article153SnapshotCodec.object();
        identity.put("owner",owner.getId()); identity.put("month",month);
        identity.set("inventory",Article153SnapshotCodec.value(inventory.resolve(owner,m).stream()
                .map(Article153ComponentAuthorityService::fingerprint).toList()));
        identity.set("components",Article153SnapshotCodec.value(entries));
        identity.set("pricing",Article153SnapshotCodec.value(p==null?null:Map.of("id",p.getId(),"fingerprint",local.fingerprint(p,p.getRules().stream().filter(r->"HOLIDAY".equals(r.getDimension())).toList()))));
        if(term!=null) {identity.put("compensationId",term.getId());identity.put("currency",currency);identity.put("mode",term.getPayMode());
            identity.set("hourly",Article153SnapshotCodec.value(term.getHourlyRateMinor()));identity.set("salary",Article153SnapshotCodec.value(term.getMonthlySalaryMinor()));}
        identity.put("revision",saved==null?0:saved.getRevision());
        var authority=rate.authority();
        LocalSource localSource=authority==null?null:new LocalSource(Article153ComponentAuthority.SourceKind.valueOf(authority.sourceKind().name()),authority.sourceReference(),authority.sourceRevision());
        identity.set("localAuthority",Article153SnapshotCodec.value(authority));
        return new Workspace(month,Article153SnapshotCodec.fingerprint(Article153SnapshotCodec.encode(identity)),currency,
                saved==null?0:saved.getRevision(),p==null?null:p.getEffectiveFrom(),
                p!=null && p.getRules().stream().anyMatch(r->"HOLIDAY".equals(r.getDimension())),localSource,
                List.copyOf(entries),doc==null?null:doc.review(),resolution.ready(),resolution.blockingReason());
    }
    @Transactional(isolation=Isolation.REPEATABLE_READ)
    public Workspace save(AppUser owner,String month,Submission input) {
        if(input==null || input.components()==null || input.review()==null || input.configurationToken()==null || input.expectedRevision()<0)
            throw ApiException.badRequest("ARTICLE153_REVIEW_INPUT","Заполни все основания и правила оплаты");
        users.findForUpdateById(owner.getId()).orElseThrow(()->ApiException.notFound("Пользователь не найден"));
        var state=read(owner,month);
        if(!state.configurationToken().equals(input.configurationToken()) || state.revision()!=input.expectedRevision())
            throw ApiException.conflict("ARTICLE153_REVIEW_STALE","Настройки изменились. Обнови форму перед сохранением");
        if(state.currencyCode()==null || state.pricingEffectiveFrom()==null)
            throw ApiException.conflict("ARTICLE153_REVIEW_SETUP","Сначала сохрани условия и правила оплаты");
        var submitted=new HashMap<Long,ComponentInput>();
        for(var c:input.components())if(c==null || submitted.put(c.versionId(),c)!=null)
            throw ApiException.badRequest("Повторная или пустая строка компонента");
        if(submitted.size()!=state.components().size())throw ApiException.conflict("Набор компонентов изменился");
        for(var c:state.components()) {
            var v=submitted.remove(c.versionId());
            if(v==null || !c.fingerprint().equals(v.fingerprint()) || v.decision()==null || v.decision()==Article153ComponentAuthority.Decision.UNCLASSIFIED || v.source()==null)
                throw ApiException.badRequest("Для каждой доплаты нужны решение и источник");
            if(c.locked() && (c.decision()!=v.decision() || !c.source().equals(v.source())))
                throw ApiException.conflict("Сохранённое основание компонента неизменяемо. Создай новую версию компонента для исправления");
        }
        if(state.localSource()!=null && !state.localSource().equals(input.localSource()))
            throw ApiException.conflict("Источник местной ставки уже сохранён. Для исправления создай новую версию правил оплаты");
        if(!state.localSourceRequired() && input.localSource()!=null)
            throw ApiException.badRequest("Для текущих правил источник местной праздничной ставки не требуется");
        try {
            if(state.localSourceRequired() && state.localSource()==null) {
                if(input.localSource()==null || input.localSource().kind()==null || input.localSource().reference()==null || input.localSource().reference().isBlank() || input.localSource().revision()==null || input.localSource().revision().isBlank())throw ApiException.badRequest("Укажи источник местной праздничной ставки");
                var s=input.localSource();local.certify(owner,state.pricingEffectiveFrom(),Article153LocalRateAuthorityService.SourceKind.valueOf(s.kind().name()),s.reference(),s.revision());
            }
            for(var v:input.components()) {var s=v.source();components.certify(owner,v.versionId(),v.decision(),s.kind(),s.reference(),s.revision(),s.basis());}
            remuneration.certify(owner,month(month),input.expectedRevision(),input.review());
        } catch(IllegalStateException e) {
            // Input/dependency conflicts only; corrupted persisted documents must retain server-error semantics.
            String code=e.getMessage();
            if(code!=null && (Set.of("ARTICLE153_REMUNERATION_CLASSIFICATION_REQUIRED", "ARTICLE153_REMUNERATION_LOCAL_RATE_REQUIRED", "ARTICLE153_REMUNERATION_COMPENSATION_REQUIRED", "ARTICLE153_REMUNERATION_CURRENCY_MISMATCH", "ARTICLE153_REMUNERATION_COMPLETE_RULE_SET_REQUIRED", "ARTICLE153_REMUNERATION_RULE_BINDING", "ARTICLE153_REMUNERATION_ZERO_LOCAL_RULE", "ARTICLE153_REMUNERATION_REVISION_CONFLICT").contains(code)
                    || code.equals(Article153ComponentAuthorityService.IMMUTABLE)
                    || code.startsWith(Article153LocalRateAuthorityService.AUTHORITY_ALREADY_CERTIFIED_IMMUTABLE)))
                throw ApiException.conflict("ARTICLE153_REVIEW_BLOCKED",switch(code) {
                    case "ARTICLE153_REMUNERATION_COMPLETE_RULE_SET_REQUIRED" -> "Для каждой включённой доплаты нужно денежное правило; исключённые доплаты не должны иметь денежных правил";
                    case "ARTICLE153_REMUNERATION_CURRENCY_MISMATCH" -> "Валюта оснований должна совпадать с валютой условий оплаты";
                    case "ARTICLE153_REMUNERATION_ZERO_LOCAL_RULE" -> "Нулевая местная ставка не может применяться при выборе другого дня отдыха";
                    default -> "Основания не согласованы с настройками. Обнови форму; для изменения сохранённого источника компонента или местной ставки создай новую версию настроек";
                });
            throw e;
        }
        return read(owner,month);
    }
    private static YearMonth month(String value) {
        if(value==null || !value.matches("\\d{4}-(0[1-9]|1[0-2])"))throw ApiException.badRequest("Укажи месяц в формате YYYY-MM");
        try{return YearMonth.parse(value);}catch(RuntimeException e){throw ApiException.badRequest("Некорректный месяц");}
    }
}
