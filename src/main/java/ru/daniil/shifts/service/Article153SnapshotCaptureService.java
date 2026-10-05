package ru.daniil.shifts.service;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import ru.daniil.shifts.model.*;
import ru.daniil.shifts.repo.*;
import java.time.*;
import java.util.*;
import static ru.daniil.shifts.service.Article153SnapshotCodec.*;

/** Internal live capture; all dependencies are resolved in the freeze transaction. */
@Service
public class Article153SnapshotCaptureService {
    private final Article153MonthlyNormPositionAuthorityService norms;
    private final HistoricalCompensationRateService rates;
    private final Article153RestDayElectionAuthorityService elections;
    private final Article153LocalRateAuthorityService localRates;
    private final Article153ComponentAuthorityService components;
    private final CompensationTermRepository compensationTerms;
    private final PayPricingTermRepository pricingTerms;

    public Article153SnapshotCaptureService(Article153MonthlyNormPositionAuthorityService norms,
            HistoricalCompensationRateService rates, Article153RestDayElectionAuthorityService elections,
            Article153LocalRateAuthorityService localRates, Article153ComponentAuthorityService components,
            CompensationTermRepository compensationTerms, PayPricingTermRepository pricingTerms) {
        this.norms = norms; this.rates = rates; this.elections = elections; this.localRates = localRates;
        this.components = components; this.compensationTerms = compensationTerms; this.pricingTerms = pricingTerms;
    }

    ObjectNode capture(PayrollSnapshot snapshot) {
        AppUser user = snapshot.getOwner();
        YearMonth month = YearMonth.from(snapshot.getPeriodMonth());
        var resolved = norms.resolve(user, month);
        require(resolved.ready(), "NORM_BLOCKED:" + resolved.blockers());
        require(month.equals(resolved.payrollMonth())
                && resolved.quantity().unit() == PayrollQuantityUnit.MINUTES, "MONTH_OR_UNIT");
        ObjectNode doc = object();
        doc.put("schema", SCHEMA); doc.put("scope", "AUTHORITY_ONLY");
        doc.put("qualifiedMinutes", resolved.quantity().value());
        var lines = doc.putArray("pieces");
        var ordered = resolved.pieces().stream().sorted(Comparator
                .comparing((Article153MonthlyNormPositionAuthorityService.NormPositionPiece p) -> p.qualifiedPiece().payrollDate())
                .thenComparing(p -> identity(p.qualifiedPiece().sourcePiece()))
                .thenComparing(p -> p.qualifiedPiece().sourcePiece().sourceEvidenceStartInstant())).toList();
        Map<String, SourceEvidence> evidence = new HashMap<>();
        for (var norm : ordered) {
            var qualified = norm.qualifiedPiece(); var source = qualified.sourcePiece();
            LocalDate date = qualified.payrollDate(); String identity = identity(source);
            require(month.equals(YearMonth.from(date)), "PIECE_MONTH");
            String key = date + ":" + identity;
            retainEvidence(evidence, key, source);
            var rate = rates.resolve(user, date);
            require(rate.sourceDate().equals(date) && rate.payMode().equals(norm.payMode().name())
                    && rate.compensationEffectiveFrom().equals(norm.compensationEffectiveFrom())
                    && Objects.equals(rate.productionNormMinutes(), norm.productionNormMinutes()), "COMPENSATION_MISMATCH");
            var term = compensationTerms.findFirstByOwnerAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(user, month.atDay(1))
                    .orElseThrow(() -> new IllegalStateException("ARTICLE153_SNAPSHOT_COMPENSATION_REQUIRED"));
            require(term.getId() != null && term.getOwner().getId().equals(user.getId())
                    && term.getEffectiveFrom().equals(rate.compensationEffectiveFrom())
                    && term.getPayMode().equals(rate.payMode()) && term.getCurrencyCode().equals(rate.currencyCode())
                    && snapshot.getCompensationEffectiveFrom().equals(term.getEffectiveFrom())
                    && snapshot.getPayMode().equals(term.getPayMode())
                    && snapshot.getCurrencyCode().equals(term.getCurrencyCode())
                    && snapshot.getHourlyRateMinor() == rate.baseHourlyRateMinor()
                    && Objects.equals(snapshot.getConfiguredHourlyRateMinor(), term.getHourlyRateMinor())
                    && Objects.equals(snapshot.getMonthlySalaryMinor(), term.getMonthlySalaryMinor()), "SNAPSHOT_COMPENSATION_MISMATCH");
            if (norm.payMode() == Article153EconomicLegalPolicy.PayMode.SALARY) {
                require(snapshot.getProductionNormMinutes() == norm.productionNormMinutes(), "SNAPSHOT_NORM_MISMATCH");
            }
            long sourceId = source.sourceKind() == OrdinaryWorkPremiumSourceService.SourceKind.EXPLICIT
                    ? source.sourceActualWorkIntervalId() : source.sourceDayEntryId();
            var election = elections.resolve(user, date, source.sourceKind(), sourceId);
            require(election.ready(), "ELECTION_BLOCKED:" + election.blockingReason());
            require(election.workDate().equals(date) && election.sourceIdentity().equals(identity), "ELECTION_IDENTITY");
            var eventPieces = ordered.stream().map(p -> p.qualifiedPiece())
                    .filter(p -> p.payrollDate().equals(date) && identity(p.sourcePiece()).equals(identity)).toList();
            require(eventPieces.stream().allMatch(p -> p.cause() == qualified.cause()), "EVENT_CAUSE");
            require(election.currentSourceFingerprint().equals(elections.fingerprint(date, identity, qualified.cause(), eventPieces)), "ELECTION_SOURCE_DRIFT");
            var local = localRates.resolve(user, date);
            require(local.ready(), "LOCAL_RATE_BLOCKED:" + local.blockingReason());
            require(local.sourceDate().equals(date), "LOCAL_DATE");
            var pricing = pricingTerms.findFirstByOwnerAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(user, date)
                    .orElseThrow(() -> new IllegalStateException("ARTICLE153_SNAPSHOT_PRICING_REQUIRED"));
            var rules = pricing.getRules().stream().filter(p -> "HOLIDAY".equals(p.getDimension()))
                    .sorted(Comparator.comparing(PayPricingRule::getCode)).toList();
            require(pricing.getId() != null && pricing.getOwner().getId().equals(user.getId())
                    && pricing.getEffectiveFrom().equals(local.pricingEffectiveFrom())
                    && local.holidayPolicyFingerprint().equals(localRates.fingerprint(pricing, rules)), "PRICING_DRIFT");
            var component = components.resolve(user, date);
            require(component.ready(), "COMPONENT_BLOCKED:" + component.blockingReason());
            require(component.sourceDate().equals(date), "COMPONENT_DATE");
            var componentIds = new HashSet<Long>();
            for (var c : component.components()) {
                require(Objects.equals(c.ownerId(), user.getId()) && !c.effectiveFrom().isAfter(date)
                        && c.decision() != Article153ComponentAuthority.Decision.UNCLASSIFIED
                        && c.formula().enabled() && componentIds.add(c.componentId()), "COMPONENT_IDENTITY");
            }
            var choice = election.otherRestDayElected() ? Article153EconomicLegalPolicy.CompensationChoice.OTHER_REST_DAY
                    : Article153EconomicLegalPolicy.CompensationChoice.ENHANCED_PAY;
            var floor = Article153EconomicLegalPolicy.resolve(date, norm.payMode(), norm.normPosition(), choice);
            ObjectNode line = lines.addObject();
            line.set("norm", value(norm)); line.set("historicalRate", value(rate));
            line.set("compensationTerm", value(new CompensationFact(term.getId(), term.getEffectiveFrom(),
                    term.getPayMode(), term.getCurrencyCode(), term.getHourlyRateMinor(), term.getMonthlySalaryMinor())));
            line.set("election", value(election)); line.set("statutoryFloor", value(floor));
            line.set("localRate", value(local)); line.put("pricingTermId", pricing.getId());
            line.set("pricingRules", value(rules.stream().map(p -> new PricingRuleFact(p.getCode(), p.getDimension(),
                    p.getPremiumBps(), p.getFromMinute(), p.getToMinuteExclusive(), p.getExclusiveGroup())).toList()));
            line.set("components", value(component));
        }
        return doc;
    }
    /** Planned NIGHT groups cite a shared clock interval; their minutes are quantities, not new clock ranges. */
    private static void retainEvidence(Map<String, SourceEvidence> evidence, String identity,
            OrdinaryWorkPremiumSourceService.SourcePiece source) {
        var previous = evidence.get(identity);
        var start = source.sourceEvidenceStartInstant(); var end = source.sourceEvidenceEndInstant();
        require(start != null && end != null && end.isAfter(start), "SOURCE_CLOCK");
        boolean sharedPlanned = previous != null
                && source.sourceKind() == OrdinaryWorkPremiumSourceService.SourceKind.PLAN_DERIVED
                && start.equals(previous.start()) && end.equals(previous.end());
        require(previous == null || sharedPlanned || !start.isBefore(previous.end()), "OVERLAPPING_SOURCE");
        int minutes = sharedPlanned ? Math.addExact(previous.minutes(), source.minutes()) : source.minutes();
        if (source.sourceKind() == OrdinaryWorkPremiumSourceService.SourceKind.PLAN_DERIVED) {
            require(!sharedPlanned || Objects.equals(previous.timezone(), source.sourceEvidenceTimezone()), "SOURCE_TIMEZONE_DRIFT");
            require(minutes <= Duration.between(start, end).toMinutes(), "PLANNED_EVIDENCE_QUANTITY");
        }
        evidence.put(identity, new SourceEvidence(start, end, source.sourceEvidenceTimezone(), minutes));
    }
    private record SourceEvidence(Instant start, Instant end, String timezone, int minutes) {}
    private static String identity(OrdinaryWorkPremiumSourceService.SourcePiece source) {
        Long id = source.sourceKind() == OrdinaryWorkPremiumSourceService.SourceKind.EXPLICIT
                ? source.sourceActualWorkIntervalId() : source.sourceDayEntryId();
        require(id != null && id > 0, "SOURCE_IDENTITY");
        return source.sourceKind().name() + ":" + id;
    }
    private record CompensationFact(long termId, LocalDate effectiveFrom, String payMode, String currencyCode,
            Long configuredHourlyRateMinor, Long monthlySalaryMinor) {}
    private record PricingRuleFact(String code, String dimension, int premiumBps, int fromMinute,
            Integer toMinuteExclusive, String exclusiveGroup) {}
}
