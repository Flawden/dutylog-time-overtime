package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.model.*;
import ru.daniil.shifts.repo.PayrollSnapshotBonusP15NatureFactRepository;
import ru.daniil.shifts.repo.PayrollSnapshotBonusP15NatureManifestRepository;

import java.util.*;

/** Read-only immutable FACT authority combining F2 premium facts with frozen F3A reward nature. */
@Service
public class PayrollHistoricalBonusP15NatureFactsService {

    private final PayrollHistoricalBonusAverageEarningsFactsService averageFacts;
    private final PayrollSnapshotBonusP15NatureFactRepository facts;
    private final PayrollSnapshotBonusP15NatureManifestRepository manifests;

    public PayrollHistoricalBonusP15NatureFactsService(
            PayrollHistoricalBonusAverageEarningsFactsService averageFacts,
            PayrollSnapshotBonusP15NatureFactRepository facts,
            PayrollSnapshotBonusP15NatureManifestRepository manifests
    ) {
        this.averageFacts = Objects.requireNonNull(averageFacts, "Historical F2 bonus authority is required");
        this.facts = Objects.requireNonNull(facts, "Historical P15 nature fact repository is required");
        this.manifests = Objects.requireNonNull(manifests, "Historical P15 nature manifest repository is required");
    }

    @Transactional(readOnly = true)
    public Resolution resolve(PayrollSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "Historical P15 nature facts require snapshot");
        PayrollHistoricalBonusAverageEarningsFactsService.Resolution base = averageFacts.resolve(snapshot);
        if (!base.ready()) return Resolution.blocked(base.blockingReason());

        List<PayrollHistoricalBonusAverageEarningsFactsService.HistoricalBonusFact> averages = base.facts();
        PayrollSnapshotBonusP15NatureManifest manifest = manifests.findBySnapshot(snapshot).orElse(null);

        if (manifest == null) {
            if (averages.isEmpty()) return Resolution.ready(List.of());
            return Resolution.blocked("HISTORICAL_BONUS_P15_NATURE_MANIFEST_MISSING");
        }
        if (!manifest.isComplete()) return Resolution.blocked("HISTORICAL_BONUS_P15_NATURE_INCOMPLETE");
        if (manifest.getAverageFactCount() != averages.size()
                || manifest.getNatureFactCount() != averages.size()) {
            return Resolution.blocked("HISTORICAL_BONUS_P15_NATURE_COUNT_MISMATCH");
        }

        List<PayrollSnapshotBonusP15NatureFact> frozen = facts.findBySnapshotOrderByFactIndexAsc(snapshot);
        if (frozen == null) throw new IllegalStateException("Historical P15 nature repository returned null");
        if (frozen.size() != averages.size()) return Resolution.blocked("HISTORICAL_BONUS_P15_NATURE_COUNT_MISMATCH");

        String fingerprint;
        try {
            fingerprint = PayrollBonusP15NatureFingerprint.calculate(manifest.getAverageFactCount(), frozen);
        } catch (RuntimeException ex) {
            throw new IllegalStateException("Historical P15 nature fact shape/order is invalid", ex);
        }
        if (!manifest.getFingerprint().equals(fingerprint)) {
            return Resolution.blocked("HISTORICAL_BONUS_P15_NATURE_FINGERPRINT_MISMATCH");
        }

        Map<Long, PayrollHistoricalBonusAverageEarningsFactsService.HistoricalBonusFact> byAverageId = new HashMap<>();
        for (var average : averages) {
            if (byAverageId.put(average.bonusAverageFactId(), average) != null) {
                throw new IllegalStateException("Historical F2 bonus facts contain duplicate average identity");
            }
        }

        List<HistoricalBonusP15Fact> result = new ArrayList<>();
        for (int index = 0; index < frozen.size(); index++) {
            PayrollSnapshotBonusP15NatureFact nature = frozen.get(index);
            if (nature == null || nature.getFactIndex() != index) {
                throw new IllegalStateException("Historical P15 nature fact order is invalid");
            }
            var average = byAverageId.get(nature.getBonusAverageFactId());
            if (average == null
                    || nature.getBonusSourceFactId() != average.bonusSourceFactId()
                    || nature.getComponentId() != average.componentId()
                    || nature.getEarningKind() != average.earningKind()) {
                return Resolution.blocked("HISTORICAL_BONUS_P15_NATURE_IDENTITY_MISMATCH");
            }
            if (!validNature(nature.getP15Nature(), average)) {
                return Resolution.blocked("HISTORICAL_BONUS_P15_NATURE_FACT_CONTRADICTION");
            }
            result.add(new HistoricalBonusP15Fact(
                    nature.getBonusNatureFactId(),
                    average,
                    nature.getP15Nature()
            ));
        }
        return Resolution.ready(result);
    }

    private static boolean validNature(
            PayrollBonusP15Nature nature,
            PayrollHistoricalBonusAverageEarningsFactsService.HistoricalBonusFact average
    ) {
        if (nature == null) return false;
        return switch (nature) {
            case MONTHLY -> average.earningKind() == PayrollEarningKind.MONTHLY_BONUS
                    && java.time.YearMonth.from(average.awardPeriodFrom()).equals(java.time.YearMonth.from(average.awardPeriodTo()))
                    && !Boolean.TRUE.equals(average.annualResult());
            case WORK_PERIOD -> average.earningKind() == PayrollEarningKind.ONE_TIME_BONUS
                    && !Boolean.TRUE.equals(average.annualResult())
                    && average.awardPeriodTo().isAfter(average.awardPeriodFrom().plusMonths(1).minusDays(1));
            case ANNUAL_RESULT -> average.earningKind() == PayrollEarningKind.ONE_TIME_BONUS
                    && Boolean.TRUE.equals(average.annualResult());
            case SERVICE_LENGTH -> average.earningKind() == PayrollEarningKind.ONE_TIME_BONUS
                    && !Boolean.TRUE.equals(average.annualResult());
        };
    }

    public record HistoricalBonusP15Fact(
            long bonusNatureFactId,
            PayrollHistoricalBonusAverageEarningsFactsService.HistoricalBonusFact bonus,
            PayrollBonusP15Nature p15Nature
    ) {
        public HistoricalBonusP15Fact {
            if (bonusNatureFactId <= 0L) throw new IllegalArgumentException("Historical P15 nature identity is invalid");
            Objects.requireNonNull(bonus, "Historical F2 bonus fact is required");
            Objects.requireNonNull(p15Nature, "Historical P15 nature is required");
        }
    }

    public record Resolution(boolean ready, String blockingReason, List<HistoricalBonusP15Fact> facts) {
        public Resolution {
            facts = List.copyOf(Objects.requireNonNull(facts, "Historical P15 facts are required"));
            if (ready == (blockingReason != null)) throw new IllegalArgumentException("Historical P15 resolution shape is invalid");
            if (!ready && !facts.isEmpty()) throw new IllegalArgumentException("Blocked historical P15 resolution cannot contain facts");
        }
        static Resolution ready(List<HistoricalBonusP15Fact> facts) { return new Resolution(true, null, facts); }
        static Resolution blocked(String reason) { return new Resolution(false, Objects.requireNonNull(reason), List.of()); }
    }
}
