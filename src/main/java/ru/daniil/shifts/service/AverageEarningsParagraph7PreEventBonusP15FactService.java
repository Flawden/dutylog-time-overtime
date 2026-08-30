package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.service.AverageEarningsParagraph7PreEventSemanticWageFactService.SemanticWageFact;
import ru.daniil.shifts.service.AverageEarningsParagraph7PreEventSemanticWageFactService.SourceAuthority;
import ru.daniil.shifts.service.PayrollBonusAverageEarningsFactService.AverageFact;
import ru.daniil.shifts.service.PayrollBonusP15NatureFactService.NatureFact;
import ru.daniil.shifts.service.PayrollBonusSourceFactService.BonusFact;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Paragraph-7 pre-event bonus paragraph-15 FACT authority.
 *
 * <p>J3B3 already proves which observed bonus source facts belong completely to
 * {@code [eventMonthStart,eventDate)}. This layer attaches only the two explicit
 * factual authorities required before paragraph-15 policy may run: the bonus
 * average-earnings fact and the explicit paragraph-15 reward nature.</p>
 *
 * <p>No paragraph-15 inclusion, proportional adjustment or money is calculated
 * here. Missing F1/F2 authority is a blocker; source period, display text,
 * posting month, earning kind or amount are never used to invent it.</p>
 */
@Service
public class AverageEarningsParagraph7PreEventBonusP15FactService {
    public static final String AUTHORITY_WINDOW_MISMATCH =
            "PP_540_P7_PRE_EVENT_BONUS_P15_WINDOW_MISMATCH";
    public static final String AVERAGE_FACT_MISSING =
            "PP_540_P7_PRE_EVENT_BONUS_AVERAGE_FACT_MISSING";
    public static final String NATURE_FACT_MISSING =
            "PP_540_P7_PRE_EVENT_BONUS_P15_NATURE_FACT_MISSING";
    public static final String FACT_IDENTITY_MISMATCH =
            "PP_540_P7_PRE_EVENT_BONUS_P15_FACT_IDENTITY_MISMATCH";

    private final PayrollBonusAverageEarningsFactService averageFacts;
    private final PayrollBonusP15NatureFactService natureFacts;

    public AverageEarningsParagraph7PreEventBonusP15FactService(
            PayrollBonusAverageEarningsFactService averageFacts,
            PayrollBonusP15NatureFactService natureFacts
    ) {
        this.averageFacts = Objects.requireNonNull(
                averageFacts,
                "Paragraph-7 bonus P15 facts require average-earnings FACT authority"
        );
        this.natureFacts = Objects.requireNonNull(
                natureFacts,
                "Paragraph-7 bonus P15 facts require reward-nature FACT authority"
        );
    }

    @Transactional(readOnly = true)
    public Resolution resolve(
            AppUser user,
            AverageEarningsParagraph7PreEventSemanticWageFactService.Resolution semanticFacts
    ) {
        Objects.requireNonNull(user, "Paragraph-7 bonus P15 facts require user");
        Objects.requireNonNull(
                semanticFacts,
                "Paragraph-7 bonus P15 facts require semantic wage authority"
        );
        if (!semanticFacts.ready()) {
            throw new IllegalArgumentException(
                    "Blocked paragraph-7 semantic wage authority cannot reach bonus P15 FACTs"
            );
        }

        LocalDate eventDate = Objects.requireNonNull(
                semanticFacts.eventDate(),
                "Paragraph-7 bonus P15 facts require legal event date"
        );
        LocalDate periodFrom = YearMonth.from(eventDate).atDay(1);
        if (!periodFrom.equals(semanticFacts.periodFrom())
                || !eventDate.equals(semanticFacts.cutoffExclusive())) {
            return Resolution.blocked(
                    eventDate,
                    periodFrom,
                    semanticFacts,
                    AUTHORITY_WINDOW_MISMATCH,
                    "Semantic wage authority does not match paragraph-7 pre-event window"
            );
        }

        List<SemanticWageFact> bonusSemantic = semanticFacts.observedFacts().stream()
                .filter(fact -> fact.sourceAuthority() == SourceAuthority.BONUS_SOURCE)
                .toList();
        if (bonusSemantic.isEmpty()) {
            return Resolution.ready(
                    eventDate,
                    periodFrom,
                    semanticFacts,
                    List.of()
            );
        }

        List<BonusFact> source = bonusSemantic.stream()
                .map(AverageEarningsParagraph7PreEventBonusP15FactService::toBonusSourceFact)
                .toList();

        List<AverageFact> resolvedAverage = Objects.requireNonNull(
                averageFacts.resolveForBonusFacts(user, source),
                "Bonus average-earnings FACT authority returned null"
        );
        Map<Long, AverageFact> averageBySource = indexAverageFacts(resolvedAverage);
        for (BonusFact bonus : source) {
            if (!averageBySource.containsKey(bonus.factId())) {
                return Resolution.blocked(
                        eventDate,
                        periodFrom,
                        semanticFacts,
                        AVERAGE_FACT_MISSING,
                        "Bonus source fact lacks explicit average-earnings FACT: " + bonus.factId()
                );
            }
        }
        if (averageBySource.size() != source.size()) {
            return Resolution.blocked(
                    eventDate,
                    periodFrom,
                    semanticFacts,
                    FACT_IDENTITY_MISMATCH,
                    "Bonus average-earnings FACT authority changed source cardinality"
            );
        }

        List<AverageFact> orderedAverage = new ArrayList<>(source.size());
        for (BonusFact bonus : source) {
            AverageFact average = averageBySource.get(bonus.factId());
            if (!sameSourceIdentity(bonus, average)) {
                return Resolution.blocked(
                        eventDate,
                        periodFrom,
                        semanticFacts,
                        FACT_IDENTITY_MISMATCH,
                        "Bonus average-earnings FACT contradicts source identity: " + bonus.factId()
                );
            }
            orderedAverage.add(average);
        }

        List<NatureFact> resolvedNature = Objects.requireNonNull(
                natureFacts.resolveForAverageFacts(user, orderedAverage),
                "Bonus paragraph-15 nature FACT authority returned null"
        );
        Map<Long, NatureFact> natureByAverage = indexNatureFacts(resolvedNature);
        for (AverageFact average : orderedAverage) {
            if (!natureByAverage.containsKey(average.factId())) {
                return Resolution.blocked(
                        eventDate,
                        periodFrom,
                        semanticFacts,
                        NATURE_FACT_MISSING,
                        "Bonus average-earnings FACT lacks explicit P15 nature FACT: "
                                + average.factId()
                );
            }
        }
        if (natureByAverage.size() != orderedAverage.size()) {
            return Resolution.blocked(
                    eventDate,
                    periodFrom,
                    semanticFacts,
                    FACT_IDENTITY_MISMATCH,
                    "Bonus P15 nature FACT authority changed average-fact cardinality"
            );
        }

        List<BonusP15Fact> facts = new ArrayList<>(source.size());
        for (int index = 0; index < source.size(); index++) {
            BonusFact bonus = source.get(index);
            AverageFact average = orderedAverage.get(index);
            NatureFact nature = natureByAverage.get(average.factId());
            if (!sameP15Identity(bonus, average, nature)) {
                return Resolution.blocked(
                        eventDate,
                        periodFrom,
                        semanticFacts,
                        FACT_IDENTITY_MISMATCH,
                        "Bonus P15 nature FACT contradicts explicit source identity: "
                                + bonus.factId()
                );
            }
            facts.add(
                    new BonusP15Fact(
                            bonusSemantic.get(index),
                            average,
                            nature
                    )
            );
        }

        return Resolution.ready(
                eventDate,
                periodFrom,
                semanticFacts,
                facts
        );
    }

    private static BonusFact toBonusSourceFact(SemanticWageFact fact) {
        return new BonusFact(
                fact.factId(),
                fact.componentId(),
                fact.earningKind(),
                fact.periodFrom(),
                fact.periodTo(),
                fact.amountMinor(),
                fact.currencyCode()
        );
    }

    private static Map<Long, AverageFact> indexAverageFacts(List<AverageFact> facts) {
        Map<Long, AverageFact> indexed = new LinkedHashMap<>();
        for (AverageFact fact : facts) {
            Objects.requireNonNull(fact, "Bonus average-earnings FACT cannot be null");
            if (indexed.put(fact.bonusSourceFactId(), fact) != null) {
                throw new IllegalStateException(
                        "Bonus average-earnings FACT authority returned duplicate source identity"
                );
            }
        }
        return indexed;
    }

    private static Map<Long, NatureFact> indexNatureFacts(List<NatureFact> facts) {
        Map<Long, NatureFact> indexed = new LinkedHashMap<>();
        for (NatureFact fact : facts) {
            Objects.requireNonNull(fact, "Bonus P15 nature FACT cannot be null");
            if (indexed.put(fact.bonusAverageFactId(), fact) != null) {
                throw new IllegalStateException(
                        "Bonus P15 nature FACT authority returned duplicate average identity"
                );
            }
        }
        return indexed;
    }

    private static boolean sameSourceIdentity(BonusFact source, AverageFact average) {
        return source.factId() == average.bonusSourceFactId()
                && source.componentId() == average.componentId()
                && source.earningKind() == average.earningKind();
    }

    private static boolean sameP15Identity(
            BonusFact source,
            AverageFact average,
            NatureFact nature
    ) {
        return nature.bonusAverageFactId() == average.factId()
                && nature.bonusSourceFactId() == source.factId()
                && nature.componentId() == source.componentId()
                && nature.earningKind() == source.earningKind();
    }

    public record BonusP15Fact(
            SemanticWageFact sourceFact,
            AverageFact averageFact,
            NatureFact natureFact
    ) {
        public BonusP15Fact {
            Objects.requireNonNull(sourceFact, "Paragraph-7 bonus source FACT is required");
            Objects.requireNonNull(averageFact, "Paragraph-7 bonus average FACT is required");
            Objects.requireNonNull(natureFact, "Paragraph-7 bonus P15 nature FACT is required");
            if (sourceFact.sourceAuthority() != SourceAuthority.BONUS_SOURCE
                    || sourceFact.factId() != averageFact.bonusSourceFactId()
                    || sourceFact.componentId() != averageFact.componentId()
                    || sourceFact.earningKind() != averageFact.earningKind()
                    || natureFact.bonusAverageFactId() != averageFact.factId()
                    || natureFact.bonusSourceFactId() != sourceFact.factId()
                    || natureFact.componentId() != sourceFact.componentId()
                    || natureFact.earningKind() != sourceFact.earningKind()) {
                throw new IllegalArgumentException(
                        "Paragraph-7 bonus P15 FACT identities are inconsistent"
                );
            }
        }

        public long factualAmountMinor() {
            return sourceFact.amountMinor();
        }

        public String currencyCode() {
            return sourceFact.currencyCode();
        }
    }

    public record Resolution(
            LocalDate eventDate,
            LocalDate periodFrom,
            LocalDate cutoffExclusive,
            boolean ready,
            String blockingReason,
            String blockingMessage,
            AverageEarningsParagraph7PreEventSemanticWageFactService.Resolution semanticFacts,
            List<BonusP15Fact> bonusFacts
    ) {
        public Resolution {
            Objects.requireNonNull(eventDate, "Paragraph-7 bonus P15 event date is required");
            Objects.requireNonNull(periodFrom, "Paragraph-7 bonus P15 period start is required");
            Objects.requireNonNull(cutoffExclusive, "Paragraph-7 bonus P15 cutoff is required");
            Objects.requireNonNull(semanticFacts, "Paragraph-7 bonus P15 provenance is required");
            bonusFacts = bonusFacts == null ? List.of() : List.copyOf(bonusFacts);
            if (!periodFrom.equals(YearMonth.from(eventDate).atDay(1))
                    || !cutoffExclusive.equals(eventDate)) {
                throw new IllegalArgumentException(
                        "Paragraph-7 bonus P15 FACT window is invalid"
                );
            }
            if (ready) {
                if (blockingReason != null || blockingMessage != null) {
                    throw new IllegalArgumentException(
                            "Ready paragraph-7 bonus P15 FACTs cannot contain blocker"
                    );
                }
            } else if (blockingReason == null
                    || blockingReason.isBlank()
                    || blockingMessage == null
                    || blockingMessage.isBlank()
                    || !bonusFacts.isEmpty()) {
                throw new IllegalArgumentException(
                        "Blocked paragraph-7 bonus P15 FACTs cannot expose partial authority"
                );
            }
        }

        static Resolution ready(
                LocalDate eventDate,
                LocalDate periodFrom,
                AverageEarningsParagraph7PreEventSemanticWageFactService.Resolution semanticFacts,
                List<BonusP15Fact> bonusFacts
        ) {
            return new Resolution(
                    eventDate,
                    periodFrom,
                    eventDate,
                    true,
                    null,
                    null,
                    semanticFacts,
                    bonusFacts
            );
        }

        static Resolution blocked(
                LocalDate eventDate,
                LocalDate periodFrom,
                AverageEarningsParagraph7PreEventSemanticWageFactService.Resolution semanticFacts,
                String reason,
                String message
        ) {
            return new Resolution(
                    eventDate,
                    periodFrom,
                    eventDate,
                    false,
                    reason,
                    message,
                    semanticFacts,
                    List.of()
            );
        }
    }
}
