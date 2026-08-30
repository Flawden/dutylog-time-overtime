package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.PayrollBonusP15Nature;
import ru.daniil.shifts.model.PayrollEarningKind;
import ru.daniil.shifts.service.AverageEarningsBonusP15HistoricalFactDiscoveryService.DiscoveredBonusFact;
import ru.daniil.shifts.service.AverageEarningsParagraph7PreEventBonusP15FactService.BonusP15Fact;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Paragraph-7 pre-event bonus accrual/discovery FACT authority.
 *
 * <p>J3B6A proves the explicit live source -> average-earnings FACT -> P15
 * nature chain for bonus source facts wholly inside the pre-event event-month
 * window. That still does not prove an accrual month. This layer reconciles
 * those facts with the existing immutable historical P15 discovery authority,
 * where the Payroll snapshot month is the canonical accrual-month authority.</p>
 *
 * <p>Unmatched MONTHLY / WORK_PERIOD historical bonuses are deliberately
 * ignored: a Payroll month alone does not prove that their source belongs to
 * {@code [eventMonthStart,eventDate)}. ANNUAL_RESULT / SERVICE_LENGTH facts are
 * additionally surfaced from historical discovery because paragraph 15 has a
 * separate event-year rule that can make a later accrual relevant. This layer
 * does not decide that eligibility.</p>
 *
 * <p>No paragraph-15 policy, proportional adjustment, bonus formula, paragraph
 * 7 aggregate numerator or paragraph 8 selection is performed here.</p>
 */
@Service
public class AverageEarningsParagraph7PreEventBonusAccrualAuthorityService {
    public static final String AUTHORITY_WINDOW_MISMATCH =
            "PP_540_P7_PRE_EVENT_BONUS_ACCRUAL_WINDOW_MISMATCH";
    public static final String HISTORICAL_WINDOW_MISMATCH =
            "PP_540_P7_PRE_EVENT_BONUS_HISTORICAL_WINDOW_MISMATCH";
    public static final String DIRECT_ACCRUAL_AUTHORITY_MISSING =
            "PP_540_P7_PRE_EVENT_BONUS_ACCRUAL_AUTHORITY_MISSING";
    public static final String FACT_IDENTITY_MISMATCH =
            "PP_540_P7_PRE_EVENT_BONUS_ACCRUAL_FACT_IDENTITY_MISMATCH";
    public static final String DUPLICATE_HISTORICAL_IDENTITY =
            "PP_540_P7_PRE_EVENT_BONUS_HISTORICAL_IDENTITY_DUPLICATE";

    private final AverageEarningsBonusP15HistoricalFactDiscoveryService historicalDiscovery;

    public AverageEarningsParagraph7PreEventBonusAccrualAuthorityService(
            AverageEarningsBonusP15HistoricalFactDiscoveryService historicalDiscovery
    ) {
        this.historicalDiscovery = Objects.requireNonNull(
                historicalDiscovery,
                "Paragraph-7 bonus accrual authority requires historical P15 discovery"
        );
    }

    @Transactional(readOnly = true)
    public Resolution resolve(
            AppUser user,
            AverageEarningsParagraph7PreEventBonusP15FactService.Resolution preEventFacts,
            YearMonth discoveryThroughMonth,
            List<YearMonth> provenNoPayrollMonths
    ) {
        Objects.requireNonNull(user, "Paragraph-7 bonus accrual authority requires user");
        Objects.requireNonNull(
                preEventFacts,
                "Paragraph-7 bonus accrual authority requires B6A FACT authority"
        );
        Objects.requireNonNull(
                discoveryThroughMonth,
                "Paragraph-7 bonus accrual authority requires discovery-through month"
        );
        List<YearMonth> zeroProofs = List.copyOf(Objects.requireNonNull(
                provenNoPayrollMonths,
                "Paragraph-7 bonus accrual authority requires explicit zero-month proofs"
        ));

        if (!preEventFacts.ready()) {
            throw new IllegalArgumentException(
                    "Blocked paragraph-7 bonus P15 FACT authority cannot reach accrual discovery"
            );
        }

        LocalDate eventDate = Objects.requireNonNull(
                preEventFacts.eventDate(),
                "Paragraph-7 bonus accrual authority requires event date"
        );
        LocalDate periodFrom = YearMonth.from(eventDate).atDay(1);
        if (!periodFrom.equals(preEventFacts.periodFrom())
                || !eventDate.equals(preEventFacts.cutoffExclusive())) {
            return Resolution.blocked(
                    eventDate,
                    periodFrom,
                    discoveryThroughMonth,
                    preEventFacts,
                    AUTHORITY_WINDOW_MISMATCH,
                    null,
                    "B6A bonus FACT authority does not match the paragraph-7 pre-event window"
            );
        }

        AverageEarningsBonusP15HistoricalFactDiscoveryService.Resolution historical =
                Objects.requireNonNull(
                        historicalDiscovery.resolve(
                                user,
                                eventDate,
                                discoveryThroughMonth,
                                zeroProofs
                        ),
                        "Historical P15 discovery returned null"
                );

        YearMonth expectedEventMonth = YearMonth.from(eventDate);
        YearMonth expectedReferenceFrom = expectedEventMonth.minusMonths(12);
        YearMonth expectedReferenceTo = expectedEventMonth.minusMonths(1);
        if (!eventDate.equals(historical.eventDate())
                || !expectedEventMonth.equals(historical.eventMonth())
                || !expectedReferenceFrom.equals(historical.referenceFrom())
                || !expectedReferenceTo.equals(historical.referenceTo())
                || !discoveryThroughMonth.equals(historical.discoveryThroughMonth())) {
            return Resolution.blocked(
                    eventDate,
                    periodFrom,
                    discoveryThroughMonth,
                    preEventFacts,
                    HISTORICAL_WINDOW_MISMATCH,
                    null,
                    "Historical P15 discovery does not match the requested legal event/as-of window"
            );
        }

        if (!historical.ready()) {
            return Resolution.blocked(
                    eventDate,
                    periodFrom,
                    discoveryThroughMonth,
                    preEventFacts,
                    Objects.requireNonNull(
                            historical.blockingReason(),
                            "Blocked historical P15 discovery requires blocker"
                    ),
                    historical.blockingPeriod(),
                    "Historical P15 discovery is blocked"
            );
        }

        Map<Long, DiscoveredBonusFact> byNatureId = indexHistorical(historical.facts());
        List<AccrualBonusFact> accepted = new ArrayList<>();
        Set<Long> directNatureIds = new HashSet<>();

        for (BonusP15Fact direct : preEventFacts.bonusFacts()) {
            Objects.requireNonNull(direct, "Paragraph-7 B6A bonus FACT cannot be null");
            long natureId = direct.natureFact().factId();
            if (!directNatureIds.add(natureId)) {
                throw new IllegalStateException(
                        "Paragraph-7 B6A bonus FACT authority contains duplicate nature identity"
                );
            }
            DiscoveredBonusFact discovered = byNatureId.get(natureId);
            if (discovered == null) {
                return Resolution.blocked(
                        eventDate,
                        periodFrom,
                        discoveryThroughMonth,
                        preEventFacts,
                        DIRECT_ACCRUAL_AUTHORITY_MISSING,
                        null,
                        "Pre-event bonus lacks immutable accrual-month authority: " + natureId
                );
            }
            if (!matchesDirect(direct, discovered)) {
                return Resolution.blocked(
                        eventDate,
                        periodFrom,
                        discoveryThroughMonth,
                        preEventFacts,
                        FACT_IDENTITY_MISMATCH,
                        discovered.accrualMonth(),
                        "Historical bonus accrual authority contradicts B6A identity: " + natureId
                );
            }
            accepted.add(toAccrualFact(
                    AccrualOrigin.PRE_EVENT_SOURCE,
                    discovered
            ));
        }

        for (DiscoveredBonusFact discovered : historical.facts()) {
            if (directNatureIds.contains(discovered.bonusNatureFactId())) {
                continue;
            }
            if (discovered.p15Nature() != PayrollBonusP15Nature.ANNUAL_RESULT
                    && discovered.p15Nature() != PayrollBonusP15Nature.SERVICE_LENGTH) {
                continue;
            }
            accepted.add(toAccrualFact(
                    AccrualOrigin.HISTORICAL_ANNUAL_OR_SERVICE_DISCOVERY,
                    discovered
            ));
        }

        accepted.sort((left, right) -> {
            int origin = left.origin().compareTo(right.origin());
            if (origin != 0) {
                return origin;
            }
            int accrual = left.accrualMonth().compareTo(right.accrualMonth());
            if (accrual != 0) {
                return accrual;
            }
            int sourceFrom = left.sourcePeriodFrom().compareTo(right.sourcePeriodFrom());
            if (sourceFrom != 0) {
                return sourceFrom;
            }
            return Long.compare(left.bonusNatureFactId(), right.bonusNatureFactId());
        });

        return Resolution.ready(
                eventDate,
                periodFrom,
                discoveryThroughMonth,
                preEventFacts,
                accepted
        );
    }

    private static Map<Long, DiscoveredBonusFact> indexHistorical(
            List<DiscoveredBonusFact> facts
    ) {
        Map<Long, DiscoveredBonusFact> indexed = new HashMap<>();
        for (DiscoveredBonusFact fact : List.copyOf(Objects.requireNonNull(
                facts,
                "Historical P15 discovery facts are required"
        ))) {
            Objects.requireNonNull(fact, "Historical P15 discovery cannot contain null fact");
            if (indexed.put(fact.bonusNatureFactId(), fact) != null) {
                throw new IllegalStateException(DUPLICATE_HISTORICAL_IDENTITY);
            }
        }
        return indexed;
    }

    private static boolean matchesDirect(
            BonusP15Fact direct,
            DiscoveredBonusFact discovered
    ) {
        var source = direct.sourceFact();
        var average = direct.averageFact();
        var nature = direct.natureFact();
        return discovered.bonusNatureFactId() == nature.factId()
                && discovered.bonusSourceFactId() == source.factId()
                && discovered.bonusAverageFactId() == average.factId()
                && discovered.componentId() == source.componentId()
                && discovered.earningKind() == source.earningKind()
                && discovered.p15Nature() == nature.p15Nature()
                && discovered.sourcePeriodFrom().equals(source.periodFrom())
                && discovered.sourcePeriodTo().equals(source.periodTo())
                && discovered.amountMinor() == source.amountMinor()
                && discovered.currencyCode().equals(source.currencyCode())
                && discovered.indicatorKey().equals(average.indicatorKey())
                && discovered.awardPeriodFrom().equals(average.awardPeriodFrom())
                && discovered.awardPeriodTo().equals(average.awardPeriodTo())
                && Objects.equals(
                        discovered.accruedForActualWorkTime(),
                        average.accruedForActualWorkTime()
                )
                && Objects.equals(
                        discovered.proratedForPartialAwardPeriod(),
                        average.proratedForPartialAwardPeriod()
                );
    }

    private static AccrualBonusFact toAccrualFact(
            AccrualOrigin origin,
            DiscoveredBonusFact fact
    ) {
        return new AccrualBonusFact(
                origin,
                fact.snapshotPeriodMonth(),
                fact.snapshotRevision(),
                fact.bonusNatureFactId(),
                fact.bonusSourceFactId(),
                fact.bonusAverageFactId(),
                fact.componentId(),
                fact.earningKind(),
                fact.p15Nature(),
                fact.accrualMonth(),
                fact.sourcePeriodFrom(),
                fact.sourcePeriodTo(),
                fact.amountMinor(),
                fact.currencyCode(),
                fact.indicatorKey(),
                fact.awardPeriodFrom(),
                fact.awardPeriodTo(),
                fact.accruedForActualWorkTime(),
                fact.proratedForPartialAwardPeriod()
        );
    }

    public enum AccrualOrigin {
        PRE_EVENT_SOURCE,
        HISTORICAL_ANNUAL_OR_SERVICE_DISCOVERY
    }

    public record AccrualBonusFact(
            AccrualOrigin origin,
            LocalDate snapshotPeriodMonth,
            int snapshotRevision,
            long bonusNatureFactId,
            long bonusSourceFactId,
            long bonusAverageFactId,
            long componentId,
            PayrollEarningKind earningKind,
            PayrollBonusP15Nature p15Nature,
            YearMonth accrualMonth,
            LocalDate sourcePeriodFrom,
            LocalDate sourcePeriodTo,
            long factualAmountMinor,
            String currencyCode,
            String indicatorKey,
            LocalDate awardPeriodFrom,
            LocalDate awardPeriodTo,
            Boolean accruedForActualWorkTime,
            Boolean proratedForPartialAwardPeriod
    ) {
        public AccrualBonusFact {
            Objects.requireNonNull(origin, "Paragraph-7 bonus accrual origin is required");
            if (snapshotPeriodMonth == null
                    || !snapshotPeriodMonth.equals(YearMonth.from(snapshotPeriodMonth).atDay(1))
                    || snapshotRevision <= 0
                    || bonusNatureFactId <= 0L
                    || bonusSourceFactId <= 0L
                    || bonusAverageFactId <= 0L
                    || componentId <= 0L
                    || earningKind == null
                    || p15Nature == null
                    || accrualMonth == null
                    || !accrualMonth.equals(YearMonth.from(snapshotPeriodMonth))
                    || sourcePeriodFrom == null
                    || sourcePeriodTo == null
                    || sourcePeriodTo.isBefore(sourcePeriodFrom)
                    || factualAmountMinor <= 0L
                    || currencyCode == null
                    || !currencyCode.matches("[A-Z]{3}")
                    || indicatorKey == null
                    || !indicatorKey.matches("[A-Z0-9][A-Z0-9._:-]{0,95}")
                    || awardPeriodFrom == null
                    || awardPeriodTo == null
                    || awardPeriodTo.isBefore(awardPeriodFrom)) {
                throw new IllegalArgumentException(
                        "Paragraph-7 bonus accrual FACT is invalid"
                );
            }
        }
    }

    public record Resolution(
            LocalDate eventDate,
            LocalDate periodFrom,
            LocalDate cutoffExclusive,
            YearMonth discoveryThroughMonth,
            boolean ready,
            String blockingReason,
            YearMonth blockingPeriod,
            String blockingMessage,
            AverageEarningsParagraph7PreEventBonusP15FactService.Resolution preEventFacts,
            List<AccrualBonusFact> bonusFacts
    ) {
        public Resolution {
            Objects.requireNonNull(eventDate, "Paragraph-7 bonus accrual event date is required");
            Objects.requireNonNull(periodFrom, "Paragraph-7 bonus accrual period start is required");
            Objects.requireNonNull(cutoffExclusive, "Paragraph-7 bonus accrual cutoff is required");
            Objects.requireNonNull(
                    discoveryThroughMonth,
                    "Paragraph-7 bonus accrual discovery-through month is required"
            );
            Objects.requireNonNull(
                    preEventFacts,
                    "Paragraph-7 bonus accrual provenance is required"
            );
            bonusFacts = bonusFacts == null ? List.of() : List.copyOf(bonusFacts);
            if (!periodFrom.equals(YearMonth.from(eventDate).atDay(1))
                    || !cutoffExclusive.equals(eventDate)) {
                throw new IllegalArgumentException(
                        "Paragraph-7 bonus accrual resolution window is invalid"
                );
            }
            if (ready) {
                if (blockingReason != null
                        || blockingPeriod != null
                        || blockingMessage != null) {
                    throw new IllegalArgumentException(
                            "Ready paragraph-7 bonus accrual authority cannot contain blocker"
                    );
                }
            } else if (blockingReason == null
                    || blockingReason.isBlank()
                    || blockingMessage == null
                    || blockingMessage.isBlank()
                    || !bonusFacts.isEmpty()) {
                throw new IllegalArgumentException(
                        "Blocked paragraph-7 bonus accrual authority cannot expose partial facts"
                );
            }
        }

        static Resolution ready(
                LocalDate eventDate,
                LocalDate periodFrom,
                YearMonth discoveryThroughMonth,
                AverageEarningsParagraph7PreEventBonusP15FactService.Resolution preEventFacts,
                List<AccrualBonusFact> facts
        ) {
            return new Resolution(
                    eventDate,
                    periodFrom,
                    eventDate,
                    discoveryThroughMonth,
                    true,
                    null,
                    null,
                    null,
                    preEventFacts,
                    facts
            );
        }

        static Resolution blocked(
                LocalDate eventDate,
                LocalDate periodFrom,
                YearMonth discoveryThroughMonth,
                AverageEarningsParagraph7PreEventBonusP15FactService.Resolution preEventFacts,
                String reason,
                YearMonth blockingPeriod,
                String message
        ) {
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException(
                        "Paragraph-7 bonus accrual blocker reason is required"
                );
            }
            return new Resolution(
                    eventDate,
                    periodFrom,
                    eventDate,
                    discoveryThroughMonth,
                    false,
                    reason,
                    blockingPeriod,
                    message,
                    preEventFacts,
                    List.of()
            );
        }
    }
}
