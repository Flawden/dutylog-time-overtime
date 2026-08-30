package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.PayrollEarningKind;
import ru.daniil.shifts.service.PayrollBonusSourceFactService.BonusFact;
import ru.daniil.shifts.service.PayrollCombinationEpisodeFactService.EpisodeFact;
import ru.daniil.shifts.service.PayrollRegionalCoefficientSourceFactService.SourceFact;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Paragraph-7 explicit pre-event semantic wage FACT projection.
 *
 * <p>J3B3 consumes only employer-observed source facts that already carry an
 * exact earning period and observed money. It does not allocate a monthly
 * amount over dates, does not infer an earning period from posting month and
 * does not treat a missing explicit fact as evidence that the earning did not
 * exist.</p>
 *
 * <p>A source fact is admissible only when its complete observed period ends
 * before the legal event date. Facts starting on/after the event are future
 * evidence and are ignored. A fact that starts before the event but reaches
 * the event date or later cannot be prorated and blocks this projection.</p>
 *
 * <p>This layer deliberately stops before paragraph-7 inclusion policy and
 * before any total wage amount. It preserves raw semantic facts for the later
 * authority layer.</p>
 */
@Service
public class AverageEarningsParagraph7PreEventSemanticWageFactService {
    public static final String SOURCE_PERIOD_CROSSES_EVENT =
            "PP_540_P7_PRE_EVENT_SEMANTIC_SOURCE_CROSSES_EVENT";
    public static final String SOURCE_CURRENCY_MISMATCH =
            "PP_540_P7_PRE_EVENT_SEMANTIC_SOURCE_CURRENCY_MISMATCH";

    private final PayrollCombinationEpisodeFactService combinationFacts;
    private final PayrollRegionalCoefficientSourceFactService regionalFacts;
    private final PayrollBonusSourceFactService bonusFacts;

    public AverageEarningsParagraph7PreEventSemanticWageFactService(
            PayrollCombinationEpisodeFactService combinationFacts,
            PayrollRegionalCoefficientSourceFactService regionalFacts,
            PayrollBonusSourceFactService bonusFacts
    ) {
        this.combinationFacts = Objects.requireNonNull(
                combinationFacts,
                "Paragraph-7 semantic facts require combination authority"
        );
        this.regionalFacts = Objects.requireNonNull(
                regionalFacts,
                "Paragraph-7 semantic facts require regional authority"
        );
        this.bonusFacts = Objects.requireNonNull(
                bonusFacts,
                "Paragraph-7 semantic facts require bonus authority"
        );
    }

    @Transactional(readOnly = true)
    public Resolution resolve(
            AppUser user,
            AverageEarningsParagraph7PreEventBasePayFormula.Calculation basePay
    ) {
        Objects.requireNonNull(
                user,
                "Paragraph-7 semantic facts require user"
        );
        Objects.requireNonNull(
                basePay,
                "Paragraph-7 semantic facts require BASE_PAY calculation"
        );

        AverageEarningsParagraph7PreEventBasePayAuthorityService.Resolution authority =
                Objects.requireNonNull(
                        basePay.authority(),
                        "Paragraph-7 BASE_PAY calculation lost authority"
                );

        if (!authority.ready()) {
            throw new IllegalArgumentException(
                    "Blocked paragraph-7 BASE_PAY authority cannot reach semantic wage facts"
            );
        }

        LocalDate eventDate = Objects.requireNonNull(
                authority.eventDate(),
                "Paragraph-7 semantic facts require legal event date"
        );
        YearMonth eventMonth = YearMonth.from(eventDate);
        LocalDate periodFrom = eventMonth.atDay(1);

        if (!periodFrom.equals(authority.periodFrom())
                || !eventDate.equals(authority.cutoffExclusive())) {
            throw new IllegalStateException(
                    "Paragraph-7 BASE_PAY authority window changed before semantic fact discovery"
            );
        }

        if (!authority.workedTimePresent()) {
            return Resolution.ready(
                    eventDate,
                    periodFrom,
                    basePay,
                    List.of()
            );
        }

        String currency = basePay.currencyCode();
        if (currency == null || !currency.matches("[A-Z]{3}")) {
            throw new IllegalStateException(
                    "Worked paragraph-7 BASE_PAY calculation requires canonical currency"
            );
        }

        List<EpisodeFact> combination = Objects.requireNonNull(
                combinationFacts.resolveMonth(user, eventMonth),
                "Combination source authority returned null month"
        );
        List<SourceFact> regional = Objects.requireNonNull(
                regionalFacts.resolveMonth(user, eventMonth),
                "Regional source authority returned null month"
        );
        List<BonusFact> bonuses = Objects.requireNonNull(
                bonusFacts.resolveMonth(user, eventMonth),
                "Bonus source authority returned null month"
        );

        List<SemanticWageFact> accepted = new ArrayList<>();

        for (EpisodeFact fact : combination) {
            PeriodRelation relation = relation(
                    eventMonth,
                    eventDate,
                    fact.periodFrom(),
                    fact.periodTo()
            );
            if (relation == PeriodRelation.FUTURE) {
                continue;
            }
            if (relation == PeriodRelation.CROSSES_EVENT) {
                return Resolution.blocked(
                        eventDate,
                        periodFrom,
                        basePay,
                        SOURCE_PERIOD_CROSSES_EVENT,
                        "COMBINATION source period reaches the paragraph-7 event date"
                );
            }
            if (!currency.equals(fact.currencyCode())) {
                return Resolution.blocked(
                        eventDate,
                        periodFrom,
                        basePay,
                        SOURCE_CURRENCY_MISMATCH,
                        "COMBINATION source currency disagrees with paragraph-7 BASE_PAY"
                );
            }
            accepted.add(
                    new SemanticWageFact(
                            SourceAuthority.COMBINATION_EPISODE,
                            PayrollEarningKind.COMBINATION,
                            fact.factId(),
                            fact.componentId(),
                            fact.periodFrom(),
                            fact.periodTo(),
                            fact.amountMinor(),
                            fact.currencyCode(),
                            fact.qualifiedMinutes(),
                            fact.agreedRateBps()
                    )
            );
        }

        for (SourceFact fact : regional) {
            PeriodRelation relation = relation(
                    eventMonth,
                    eventDate,
                    fact.periodFrom(),
                    fact.periodTo()
            );
            if (relation == PeriodRelation.FUTURE) {
                continue;
            }
            if (relation == PeriodRelation.CROSSES_EVENT) {
                return Resolution.blocked(
                        eventDate,
                        periodFrom,
                        basePay,
                        SOURCE_PERIOD_CROSSES_EVENT,
                        "REGIONAL_COEFFICIENT source period reaches the paragraph-7 event date"
                );
            }
            if (!currency.equals(fact.currencyCode())) {
                return Resolution.blocked(
                        eventDate,
                        periodFrom,
                        basePay,
                        SOURCE_CURRENCY_MISMATCH,
                        "REGIONAL_COEFFICIENT source currency disagrees with paragraph-7 BASE_PAY"
                );
            }
            accepted.add(
                    new SemanticWageFact(
                            SourceAuthority.REGIONAL_SOURCE,
                            PayrollEarningKind.REGIONAL_COEFFICIENT,
                            fact.factId(),
                            fact.componentId(),
                            fact.periodFrom(),
                            fact.periodTo(),
                            fact.amountMinor(),
                            fact.currencyCode(),
                            null,
                            null
                    )
            );
        }

        for (BonusFact fact : bonuses) {
            PeriodRelation relation = relation(
                    eventMonth,
                    eventDate,
                    fact.periodFrom(),
                    fact.periodTo()
            );
            if (relation == PeriodRelation.FUTURE) {
                continue;
            }
            if (relation == PeriodRelation.CROSSES_EVENT) {
                return Resolution.blocked(
                        eventDate,
                        periodFrom,
                        basePay,
                        SOURCE_PERIOD_CROSSES_EVENT,
                        fact.earningKind() + " source period reaches the paragraph-7 event date"
                );
            }
            if (!currency.equals(fact.currencyCode())) {
                return Resolution.blocked(
                        eventDate,
                        periodFrom,
                        basePay,
                        SOURCE_CURRENCY_MISMATCH,
                        fact.earningKind() + " source currency disagrees with paragraph-7 BASE_PAY"
                );
            }
            accepted.add(
                    new SemanticWageFact(
                            SourceAuthority.BONUS_SOURCE,
                            fact.earningKind(),
                            fact.factId(),
                            fact.componentId(),
                            fact.periodFrom(),
                            fact.periodTo(),
                            fact.amountMinor(),
                            fact.currencyCode(),
                            null,
                            null
                    )
            );
        }

        accepted.sort(
                Comparator
                        .comparing(SemanticWageFact::periodFrom)
                        .thenComparing(SemanticWageFact::periodTo)
                        .thenComparing(fact -> fact.earningKind().name())
                        .thenComparingLong(SemanticWageFact::componentId)
                        .thenComparingLong(SemanticWageFact::factId)
        );

        return Resolution.ready(
                eventDate,
                periodFrom,
                basePay,
                accepted
        );
    }

    private static PeriodRelation relation(
            YearMonth eventMonth,
            LocalDate eventDate,
            LocalDate factFrom,
            LocalDate factTo
    ) {
        Objects.requireNonNull(factFrom, "Semantic source period start is required");
        Objects.requireNonNull(factTo, "Semantic source period end is required");
        if (factTo.isBefore(factFrom)
                || !YearMonth.from(factFrom).equals(eventMonth)
                || !YearMonth.from(factTo).equals(eventMonth)) {
            throw new IllegalStateException(
                    "Semantic source authority returned a fact outside the paragraph-7 event month"
            );
        }
        if (!factFrom.isBefore(eventDate)) {
            return PeriodRelation.FUTURE;
        }
        if (!factTo.isBefore(eventDate)) {
            return PeriodRelation.CROSSES_EVENT;
        }
        return PeriodRelation.PRE_EVENT;
    }

    private enum PeriodRelation {
        PRE_EVENT,
        CROSSES_EVENT,
        FUTURE
    }

    public enum SourceAuthority {
        COMBINATION_EPISODE,
        REGIONAL_SOURCE,
        BONUS_SOURCE
    }

    public record SemanticWageFact(
            SourceAuthority sourceAuthority,
            PayrollEarningKind earningKind,
            long factId,
            long componentId,
            LocalDate periodFrom,
            LocalDate periodTo,
            long amountMinor,
            String currencyCode,
            Long qualifiedMinutes,
            Integer agreedRateBps
    ) {
        public SemanticWageFact {
            Objects.requireNonNull(sourceAuthority, "Semantic source authority is required");
            Objects.requireNonNull(earningKind, "Semantic earning kind is required");
            Objects.requireNonNull(periodFrom, "Semantic earning period start is required");
            Objects.requireNonNull(periodTo, "Semantic earning period end is required");
            if (factId <= 0L
                    || componentId <= 0L
                    || periodTo.isBefore(periodFrom)
                    || amountMinor <= 0L
                    || currencyCode == null
                    || !currencyCode.matches("[A-Z]{3}")) {
                throw new IllegalArgumentException(
                        "Paragraph-7 semantic wage fact is invalid"
                );
            }
            boolean supported = switch (sourceAuthority) {
                case COMBINATION_EPISODE ->
                        earningKind == PayrollEarningKind.COMBINATION
                                && qualifiedMinutes != null
                                && qualifiedMinutes > 0L;
                case REGIONAL_SOURCE ->
                        earningKind == PayrollEarningKind.REGIONAL_COEFFICIENT
                                && qualifiedMinutes == null
                                && agreedRateBps == null;
                case BONUS_SOURCE ->
                        (earningKind == PayrollEarningKind.MONTHLY_BONUS
                                || earningKind == PayrollEarningKind.ONE_TIME_BONUS)
                                && qualifiedMinutes == null
                                && agreedRateBps == null;
            };
            if (!supported) {
                throw new IllegalArgumentException(
                        "Paragraph-7 semantic source identity is inconsistent"
                );
            }
        }
    }

    public record Resolution(
            LocalDate eventDate,
            LocalDate periodFrom,
            LocalDate cutoffExclusive,
            boolean ready,
            String blockingReason,
            String blockingMessage,
            AverageEarningsParagraph7PreEventBasePayFormula.Calculation basePay,
            List<SemanticWageFact> observedFacts
    ) {
        public Resolution {
            Objects.requireNonNull(eventDate, "Paragraph-7 event date is required");
            Objects.requireNonNull(periodFrom, "Paragraph-7 period start is required");
            Objects.requireNonNull(cutoffExclusive, "Paragraph-7 cutoff is required");
            Objects.requireNonNull(basePay, "Paragraph-7 BASE_PAY provenance is required");
            observedFacts = observedFacts == null
                    ? List.of()
                    : List.copyOf(observedFacts);
            if (!periodFrom.equals(YearMonth.from(eventDate).atDay(1))
                    || !cutoffExclusive.equals(eventDate)) {
                throw new IllegalArgumentException(
                        "Paragraph-7 semantic wage fact window is invalid"
                );
            }
            if (ready) {
                if (blockingReason != null || blockingMessage != null) {
                    throw new IllegalArgumentException(
                            "Ready semantic wage facts cannot contain blocker"
                    );
                }
            } else if (blockingReason == null
                    || blockingReason.isBlank()
                    || blockingMessage == null
                    || blockingMessage.isBlank()
                    || !observedFacts.isEmpty()) {
                throw new IllegalArgumentException(
                        "Blocked semantic wage facts cannot expose partial money evidence"
                );
            }
        }

        static Resolution ready(
                LocalDate eventDate,
                LocalDate periodFrom,
                AverageEarningsParagraph7PreEventBasePayFormula.Calculation basePay,
                List<SemanticWageFact> facts
        ) {
            return new Resolution(
                    eventDate,
                    periodFrom,
                    eventDate,
                    true,
                    null,
                    null,
                    basePay,
                    facts
            );
        }

        static Resolution blocked(
                LocalDate eventDate,
                LocalDate periodFrom,
                AverageEarningsParagraph7PreEventBasePayFormula.Calculation basePay,
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
                    basePay,
                    List.of()
            );
        }

        public boolean hasObservedFacts() {
            return ready && !observedFacts.isEmpty();
        }
    }
}
