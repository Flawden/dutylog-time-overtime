package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.model.PayrollBonusP15Nature;
import ru.daniil.shifts.model.PayrollEarningKind;
import ru.daniil.shifts.model.PayrollSnapshot;
import ru.daniil.shifts.model.PayrollSnapshotBonusP15NatureFact;
import ru.daniil.shifts.model.PayrollSnapshotBonusP15NatureManifest;
import ru.daniil.shifts.repo.PayrollSnapshotBonusP15NatureFactRepository;
import ru.daniil.shifts.repo.PayrollSnapshotBonusP15NatureManifestRepository;

import java.util.*;

/** Freezes explicit F3A reward-nature facts beside one immutable Payroll snapshot. */
@Service
public class PayrollBonusP15NatureFreezeService {

    private final PayrollSnapshotBonusP15NatureFactRepository facts;
    private final PayrollSnapshotBonusP15NatureManifestRepository manifests;

    public PayrollBonusP15NatureFreezeService(
            PayrollSnapshotBonusP15NatureFactRepository facts,
            PayrollSnapshotBonusP15NatureManifestRepository manifests
    ) {
        this.facts = Objects.requireNonNull(facts, "Snapshot P15 nature fact repository is required");
        this.manifests = Objects.requireNonNull(manifests, "Snapshot P15 nature manifest repository is required");
    }

    @Transactional
    public FreezeResult freeze(
            PayrollSnapshot snapshot,
            List<PayrollBonusAverageEarningsFactService.AverageFact> averageFacts,
            List<PayrollBonusP15NatureFactService.NatureFact> natureFacts
    ) {
        Objects.requireNonNull(snapshot, "Snapshot P15 nature freeze requires snapshot");
        List<PayrollBonusAverageEarningsFactService.AverageFact> averages = List.copyOf(Objects.requireNonNull(averageFacts, "Average facts are required"));
        List<PayrollBonusP15NatureFactService.NatureFact> natures = List.copyOf(Objects.requireNonNull(natureFacts, "P15 nature facts are required"));

        Map<Long, PayrollBonusAverageEarningsFactService.AverageFact> averageById = new HashMap<>();
        long previousSource = Long.MIN_VALUE;
        for (PayrollBonusAverageEarningsFactService.AverageFact average : averages) {
            Objects.requireNonNull(average, "Average fact is required");
            if (average.bonusSourceFactId() <= previousSource) {
                throw new IllegalStateException("Average facts are not in canonical F2 order");
            }
            previousSource = average.bonusSourceFactId();
            if (averageById.put(average.factId(), average) != null) {
                throw new IllegalStateException("Average facts contain duplicate identity");
            }
        }

        Map<Long, PayrollBonusP15NatureFactService.NatureFact> natureByAverage = new HashMap<>();
        Set<Long> natureIds = new HashSet<>();
        for (PayrollBonusP15NatureFactService.NatureFact nature : natures) {
            Objects.requireNonNull(nature, "P15 nature fact is required");
            if (!natureIds.add(nature.factId()) || natureByAverage.put(nature.bonusAverageFactId(), nature) != null) {
                throw new IllegalStateException("P15 nature facts contain duplicate identity");
            }
            PayrollBonusAverageEarningsFactService.AverageFact average = averageById.get(nature.bonusAverageFactId());
            if (average == null) throw new IllegalStateException("P15 nature fact has no matching average fact");
            requireIdentity(nature, average);
            validateNature(nature.p15Nature(), average);
        }

        List<PayrollSnapshotBonusP15NatureFact> frozen = new ArrayList<>();
        for (PayrollBonusAverageEarningsFactService.AverageFact average : averages) {
            PayrollBonusP15NatureFactService.NatureFact nature = natureByAverage.get(average.factId());
            if (nature == null) continue;
            frozen.add(new PayrollSnapshotBonusP15NatureFact(
                    snapshot,
                    frozen.size(),
                    average.bonusSourceFactId(),
                    average.factId(),
                    nature.factId(),
                    average.componentId(),
                    average.earningKind(),
                    nature.p15Nature()
            ));
        }

        if (!frozen.isEmpty()) facts.saveAll(frozen);
        String fingerprint = PayrollBonusP15NatureFingerprint.calculate(averages.size(), frozen);
        PayrollSnapshotBonusP15NatureManifest manifest = new PayrollSnapshotBonusP15NatureManifest(
                snapshot,
                averages.size(),
                frozen.size(),
                fingerprint
        );
        manifests.saveAndFlush(manifest);
        return new FreezeResult(List.copyOf(frozen), manifest);
    }

    private static void requireIdentity(
            PayrollBonusP15NatureFactService.NatureFact nature,
            PayrollBonusAverageEarningsFactService.AverageFact average
    ) {
        if (nature.bonusAverageFactId() != average.factId()
                || nature.bonusSourceFactId() != average.bonusSourceFactId()
                || nature.componentId() != average.componentId()
                || nature.earningKind() != average.earningKind()) {
            throw new IllegalStateException("P15 nature fact contradicts average fact identity");
        }
    }

    private static void validateNature(PayrollBonusP15Nature nature, PayrollBonusAverageEarningsFactService.AverageFact average) {
        Objects.requireNonNull(nature, "P15 nature is required");
        switch (nature) {
            case MONTHLY -> {
                if (average.earningKind() != PayrollEarningKind.MONTHLY_BONUS
                        || !java.time.YearMonth.from(average.awardPeriodFrom()).equals(java.time.YearMonth.from(average.awardPeriodTo()))
                        || Boolean.TRUE.equals(average.annualResult())) {
                    throw new IllegalStateException("MONTHLY P15 nature contradicts frozen F1 facts");
                }
            }
            case WORK_PERIOD -> {
                if (average.earningKind() != PayrollEarningKind.ONE_TIME_BONUS
                        || Boolean.TRUE.equals(average.annualResult())
                        || !average.awardPeriodTo().isAfter(average.awardPeriodFrom().plusMonths(1).minusDays(1))) {
                    throw new IllegalStateException("WORK_PERIOD P15 nature contradicts frozen F1 facts");
                }
            }
            case ANNUAL_RESULT -> {
                if (average.earningKind() != PayrollEarningKind.ONE_TIME_BONUS || !Boolean.TRUE.equals(average.annualResult())) {
                    throw new IllegalStateException("ANNUAL_RESULT P15 nature contradicts frozen F1 facts");
                }
            }
            case SERVICE_LENGTH -> {
                if (average.earningKind() != PayrollEarningKind.ONE_TIME_BONUS || Boolean.TRUE.equals(average.annualResult())) {
                    throw new IllegalStateException("SERVICE_LENGTH P15 nature contradicts frozen F1 facts");
                }
            }
        }
    }

    public record FreezeResult(
            List<PayrollSnapshotBonusP15NatureFact> facts,
            PayrollSnapshotBonusP15NatureManifest manifest
    ) {
        public FreezeResult {
            facts = List.copyOf(Objects.requireNonNull(facts, "Frozen P15 nature facts are required"));
            Objects.requireNonNull(manifest, "Frozen P15 nature manifest is required");
        }
    }
}
