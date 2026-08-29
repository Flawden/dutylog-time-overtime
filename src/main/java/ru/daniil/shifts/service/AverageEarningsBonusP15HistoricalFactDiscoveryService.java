package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.PayrollBonusP15Nature;
import ru.daniil.shifts.model.PayrollEarningKind;
import ru.daniil.shifts.model.PayrollSnapshot;
import ru.daniil.shifts.repo.PayrollSnapshotRepository;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * Historical FACT discovery for paragraph-15 premium inputs.
 *
 * <p>This boundary exists because annual-result and service-length rewards for
 * the calendar year preceding the event participate regardless of accrual
 * time. A later immutable Payroll month can therefore become relevant to an
 * earlier average-earnings event and trigger recalculation.</p>
 *
 * <p>The Payroll snapshot month is the explicit accrual/payroll-month
 * authority supplied to the later P15 POLICY kernel. D2 source periods,
 * award periods, display labels and earning names are never repurposed as an
 * accrual month.</p>
 *
 * <p>Discovery is deterministic as-of an explicit closed Payroll month. Every
 * month from the canonical reference start through that as-of month must be
 * covered either by a latest immutable Payroll snapshot or by an explicit
 * upstream proof that no Payroll snapshot is required for that month. This
 * service does not manufacture such zero-month proofs from employment,
 * schedules, account age or missing rows.</p>
 *
 * <p>No paragraph-15 inclusion decision, proportional-time authority, premium
 * amount formula, numerator merge or average-earnings money is calculated
 * here.</p>
 */
@Service
public class AverageEarningsBonusP15HistoricalFactDiscoveryService {

    private static final String SNAPSHOT_MISSING =
            "HISTORICAL_P15_PAYROLL_SNAPSHOT_MISSING";
    private static final String ZERO_CONTRADICTION =
            "HISTORICAL_P15_NO_PAYROLL_PROOF_CONTRADICTS_SNAPSHOT";
    private static final String LATEST_SUPERSEDED =
            "HISTORICAL_P15_PAYROLL_LATEST_REVISION_SUPERSEDED";
    private static final String CURRENCY_INVALID =
            "HISTORICAL_P15_PAYROLL_CURRENCY_INVALID";
    private static final String CURRENCY_MISMATCH =
            "HISTORICAL_P15_PAYROLL_CURRENCY_MISMATCH";
    private static final String DUPLICATE_BONUS_IDENTITY =
            "HISTORICAL_P15_BONUS_IDENTITY_DUPLICATE";

    private final PayrollSnapshotRepository snapshots;
    private final PayrollHistoricalBonusP15NatureFactsService historicalFacts;

    public AverageEarningsBonusP15HistoricalFactDiscoveryService(
            PayrollSnapshotRepository snapshots,
            PayrollHistoricalBonusP15NatureFactsService historicalFacts
    ) {
        this.snapshots = Objects.requireNonNull(
                snapshots,
                "Payroll snapshot repository is required"
        );
        this.historicalFacts = Objects.requireNonNull(
                historicalFacts,
                "Historical paragraph-15 fact authority is required"
        );
    }

    @Transactional(readOnly = true)
    public Resolution resolve(
            AppUser user,
            LocalDate eventDate,
            YearMonth discoveryThroughMonth,
            List<YearMonth> provenNoPayrollMonths
    ) {
        Objects.requireNonNull(
                user,
                "Paragraph-15 historical discovery requires user"
        );
        Objects.requireNonNull(
                eventDate,
                "Paragraph-15 historical discovery requires event date"
        );
        Objects.requireNonNull(
                discoveryThroughMonth,
                "Paragraph-15 historical discovery requires as-of Payroll month"
        );
        Objects.requireNonNull(
                provenNoPayrollMonths,
                "Paragraph-15 historical discovery requires zero-month proofs"
        );

        AverageEarningsLegalPolicy.requireRegime(eventDate);

        YearMonth eventMonth = YearMonth.from(eventDate);
        YearMonth referenceFrom = eventMonth.minusMonths(12);
        YearMonth referenceTo = eventMonth.minusMonths(1);

        if (discoveryThroughMonth.isBefore(referenceTo)) {
            throw new IllegalArgumentException(
                    "Paragraph-15 historical discovery cannot end before the canonical reference period"
            );
        }

        Set<YearMonth> zeroMonths = validateZeroMonths(
                provenNoPayrollMonths,
                referenceFrom,
                discoveryThroughMonth
        );

        List<PayrollSnapshot> candidates =
                snapshots.findByOwnerAndPeriodMonthBetweenOrderByPeriodMonthAscRevisionDesc(
                        user,
                        referenceFrom.atDay(1),
                        discoveryThroughMonth.atDay(1)
                );

        Map<YearMonth, PayrollSnapshot> latestByMonth = new TreeMap<>();

        if (candidates != null) {
            for (PayrollSnapshot candidate : candidates) {
                validateCandidate(
                        candidate,
                        referenceFrom,
                        discoveryThroughMonth
                );

                YearMonth month = YearMonth.from(candidate.getPeriodMonth());
                latestByMonth.merge(
                        month,
                        candidate,
                        (left, right) -> left.getRevision() >= right.getRevision()
                                ? left
                                : right
                );
            }
        }

        List<DiscoveredBonusFact> discovered = new ArrayList<>();
        Map<Long, YearMonth> seenNatureIds = new HashMap<>();
        String currency = null;

        for (YearMonth month = referenceFrom;
                !month.isAfter(discoveryThroughMonth);
                month = month.plusMonths(1)) {

            PayrollSnapshot snapshot = latestByMonth.get(month);
            boolean provenZero = zeroMonths.contains(month);

            if (snapshot == null) {
                if (provenZero) {
                    continue;
                }
                return Resolution.blocked(
                        eventDate,
                        eventMonth,
                        referenceFrom,
                        referenceTo,
                        discoveryThroughMonth,
                        SNAPSHOT_MISSING,
                        month
                );
            }

            if (provenZero) {
                return Resolution.blocked(
                        eventDate,
                        eventMonth,
                        referenceFrom,
                        referenceTo,
                        discoveryThroughMonth,
                        ZERO_CONTRADICTION,
                        month
                );
            }

            if (snapshot.getSupersededBy() != null) {
                return Resolution.blocked(
                        eventDate,
                        eventMonth,
                        referenceFrom,
                        referenceTo,
                        discoveryThroughMonth,
                        LATEST_SUPERSEDED,
                        month
                );
            }

            String snapshotCurrency = snapshot.getCurrencyCode();
            if (snapshotCurrency == null || !snapshotCurrency.matches("[A-Z]{3}")) {
                return Resolution.blocked(
                        eventDate,
                        eventMonth,
                        referenceFrom,
                        referenceTo,
                        discoveryThroughMonth,
                        CURRENCY_INVALID,
                        month
                );
            }

            if (currency == null) {
                currency = snapshotCurrency;
            } else if (!currency.equals(snapshotCurrency)) {
                return Resolution.blocked(
                        eventDate,
                        eventMonth,
                        referenceFrom,
                        referenceTo,
                        discoveryThroughMonth,
                        CURRENCY_MISMATCH,
                        month
                );
            }

            PayrollHistoricalBonusP15NatureFactsService.Resolution resolved =
                    Objects.requireNonNull(
                            historicalFacts.resolve(snapshot),
                            "Historical paragraph-15 authority returned null"
                    );

            if (!resolved.ready()) {
                return Resolution.blocked(
                        eventDate,
                        eventMonth,
                        referenceFrom,
                        referenceTo,
                        discoveryThroughMonth,
                        resolved.blockingReason(),
                        month
                );
            }

            for (PayrollHistoricalBonusP15NatureFactsService.HistoricalBonusP15Fact fact
                    : resolved.facts()) {
                Objects.requireNonNull(
                        fact,
                        "Historical paragraph-15 authority returned null fact"
                );

                YearMonth previousMonth = seenNatureIds.putIfAbsent(
                        fact.bonusNatureFactId(),
                        month
                );

                if (previousMonth != null) {
                    return Resolution.blocked(
                            eventDate,
                            eventMonth,
                            referenceFrom,
                            referenceTo,
                            discoveryThroughMonth,
                            DUPLICATE_BONUS_IDENTITY,
                            month
                    );
                }

                discovered.add(
                        toDiscoveredFact(
                                snapshot,
                                month,
                                fact
                        )
                );
            }
        }

        return Resolution.ready(
                eventDate,
                eventMonth,
                referenceFrom,
                referenceTo,
                discoveryThroughMonth,
                currency,
                discovered
        );
    }

    private static Set<YearMonth> validateZeroMonths(
            List<YearMonth> provenNoPayrollMonths,
            YearMonth discoveryFrom,
            YearMonth discoveryThrough
    ) {
        Set<YearMonth> result = new LinkedHashSet<>();
        YearMonth previous = null;

        for (YearMonth month : List.copyOf(provenNoPayrollMonths)) {
            if (month == null
                    || month.isBefore(discoveryFrom)
                    || month.isAfter(discoveryThrough)) {
                throw new IllegalArgumentException(
                        "Paragraph-15 no-Payroll proof lies outside discovery window"
                );
            }

            if (previous != null && !month.isAfter(previous)) {
                throw new IllegalArgumentException(
                        "Paragraph-15 no-Payroll proofs must be strictly ascending and unique"
                );
            }

            result.add(month);
            previous = month;
        }

        return result;
    }

    private static void validateCandidate(
            PayrollSnapshot candidate,
            YearMonth discoveryFrom,
            YearMonth discoveryThrough
    ) {
        if (candidate == null
                || candidate.getPeriodMonth() == null
                || candidate.getRevision() <= 0) {
            throw new IllegalStateException(
                    "Historical paragraph-15 Payroll snapshot identity is invalid"
            );
        }

        YearMonth month = YearMonth.from(candidate.getPeriodMonth());

        if (!candidate.getPeriodMonth().equals(month.atDay(1))) {
            throw new IllegalStateException(
                    "Historical paragraph-15 Payroll snapshot month is not canonical"
            );
        }

        if (month.isBefore(discoveryFrom) || month.isAfter(discoveryThrough)) {
            throw new IllegalStateException(
                    "Historical paragraph-15 Payroll repository returned snapshot outside discovery window"
            );
        }
    }

    private static DiscoveredBonusFact toDiscoveredFact(
            PayrollSnapshot snapshot,
            YearMonth accrualMonth,
            PayrollHistoricalBonusP15NatureFactsService.HistoricalBonusP15Fact fact
    ) {
        PayrollHistoricalBonusAverageEarningsFactsService.HistoricalBonusFact bonus =
                Objects.requireNonNull(
                        fact.bonus(),
                        "Historical paragraph-15 fact requires F2 bonus fact"
                );

        return new DiscoveredBonusFact(
                snapshot.getPeriodMonth(),
                snapshot.getRevision(),
                fact.bonusNatureFactId(),
                bonus.bonusSourceFactId(),
                bonus.bonusAverageFactId(),
                bonus.componentId(),
                bonus.earningKind(),
                fact.p15Nature(),
                accrualMonth,
                bonus.sourcePeriodFrom(),
                bonus.sourcePeriodTo(),
                bonus.amountMinor(),
                bonus.currencyCode(),
                bonus.indicatorKey(),
                bonus.awardPeriodFrom(),
                bonus.awardPeriodTo(),
                bonus.accruedForActualWorkTime(),
                bonus.proratedForPartialAwardPeriod()
        );
    }

    public record DiscoveredBonusFact(
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
            long amountMinor,
            String currencyCode,
            String indicatorKey,
            LocalDate awardPeriodFrom,
            LocalDate awardPeriodTo,
            Boolean accruedForActualWorkTime,
            Boolean proratedForPartialAwardPeriod
    ) {
        public DiscoveredBonusFact {
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
                    || amountMinor <= 0L
                    || currencyCode == null
                    || !currencyCode.matches("[A-Z]{3}")
                    || indicatorKey == null
                    || !indicatorKey.matches("[A-Z0-9][A-Z0-9._:-]{0,95}")
                    || awardPeriodFrom == null
                    || awardPeriodTo == null
                    || awardPeriodTo.isBefore(awardPeriodFrom)) {
                throw new IllegalArgumentException(
                        "Discovered paragraph-15 historical bonus fact is invalid"
                );
            }
        }

        public AverageEarningsBonusP15Policy.BonusFact toPolicyFact() {
            return new AverageEarningsBonusP15Policy.BonusFact(
                    bonusNatureFactId,
                    accrualMonth,
                    p15Nature,
                    indicatorKey,
                    amountMinor,
                    awardPeriodFrom,
                    awardPeriodTo,
                    accruedForActualWorkTime,
                    proratedForPartialAwardPeriod
            );
        }
    }

    public record Resolution(
            boolean ready,
            LocalDate eventDate,
            YearMonth eventMonth,
            YearMonth referenceFrom,
            YearMonth referenceTo,
            YearMonth discoveryThroughMonth,
            String currencyCode,
            String blockingReason,
            YearMonth blockingPeriod,
            List<DiscoveredBonusFact> facts
    ) {
        public Resolution {
            Objects.requireNonNull(eventDate, "Paragraph-15 discovery event date is required");
            Objects.requireNonNull(eventMonth, "Paragraph-15 discovery event month is required");
            Objects.requireNonNull(referenceFrom, "Paragraph-15 discovery reference start is required");
            Objects.requireNonNull(referenceTo, "Paragraph-15 discovery reference end is required");
            Objects.requireNonNull(discoveryThroughMonth, "Paragraph-15 discovery as-of month is required");
            facts = List.copyOf(Objects.requireNonNull(
                    facts,
                    "Paragraph-15 discovered facts are required"
            ));

            if (ready == (blockingReason != null)) {
                throw new IllegalArgumentException(
                        "Paragraph-15 historical discovery resolution state is invalid"
                );
            }
            if (ready && blockingPeriod != null) {
                throw new IllegalArgumentException(
                        "Ready paragraph-15 discovery cannot contain blocker period"
                );
            }
            if (!ready && blockingPeriod == null) {
                throw new IllegalArgumentException(
                        "Blocked paragraph-15 discovery requires blocker period"
                );
            }
            if (!ready && !facts.isEmpty()) {
                throw new IllegalArgumentException(
                        "Blocked paragraph-15 discovery cannot expose partial facts"
                );
            }
            if (currencyCode != null && !currencyCode.matches("[A-Z]{3}")) {
                throw new IllegalArgumentException(
                        "Paragraph-15 discovery currency is invalid"
                );
            }
            if (!ready && currencyCode != null) {
                throw new IllegalArgumentException(
                        "Blocked paragraph-15 discovery cannot expose partial currency"
                );
            }
        }

        public static Resolution ready(
                LocalDate eventDate,
                YearMonth eventMonth,
                YearMonth referenceFrom,
                YearMonth referenceTo,
                YearMonth discoveryThroughMonth,
                String currencyCode,
                List<DiscoveredBonusFact> facts
        ) {
            return new Resolution(
                    true,
                    eventDate,
                    eventMonth,
                    referenceFrom,
                    referenceTo,
                    discoveryThroughMonth,
                    currencyCode,
                    null,
                    null,
                    facts
            );
        }

        public static Resolution blocked(
                LocalDate eventDate,
                YearMonth eventMonth,
                YearMonth referenceFrom,
                YearMonth referenceTo,
                YearMonth discoveryThroughMonth,
                String blockingReason,
                YearMonth blockingPeriod
        ) {
            if (blockingReason == null || blockingReason.isBlank()) {
                throw new IllegalArgumentException(
                        "Paragraph-15 historical discovery blocker reason is required"
                );
            }
            return new Resolution(
                    false,
                    eventDate,
                    eventMonth,
                    referenceFrom,
                    referenceTo,
                    discoveryThroughMonth,
                    null,
                    blockingReason,
                    blockingPeriod,
                    List.of()
            );
        }

        public List<AverageEarningsBonusP15Policy.BonusFact> policyFacts() {
            if (!ready) {
                return List.of();
            }
            return facts.stream()
                    .map(DiscoveredBonusFact::toPolicyFact)
                    .toList();
        }
    }
}
