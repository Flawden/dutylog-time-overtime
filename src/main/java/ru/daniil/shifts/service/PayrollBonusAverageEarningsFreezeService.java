package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.model.PayrollSnapshot;
import ru.daniil.shifts.model.PayrollSnapshotBonusAverageEarningsFact;
import ru.daniil.shifts.model.PayrollSnapshotBonusAverageEarningsManifest;
import ru.daniil.shifts.repo.PayrollSnapshotBonusAverageEarningsFactRepository;
import ru.daniil.shifts.repo.PayrollSnapshotBonusAverageEarningsManifestRepository;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Freezes current explicit D2 + F1 premium facts beside one immutable Payroll
 * snapshot revision.
 *
 * <p>Missing F1 facts do not block ordinary payroll snapshot creation. They are
 * represented by an incomplete manifest so future paragraph-15 calculation can
 * fail closed without changing ordinary payroll history.</p>
 */
@Service
public class PayrollBonusAverageEarningsFreezeService {

    private final PayrollSnapshotBonusAverageEarningsFactRepository facts;
    private final PayrollSnapshotBonusAverageEarningsManifestRepository manifests;

    public PayrollBonusAverageEarningsFreezeService(
            PayrollSnapshotBonusAverageEarningsFactRepository facts,
            PayrollSnapshotBonusAverageEarningsManifestRepository manifests
    ) {
        this.facts = Objects.requireNonNull(
                facts,
                "Snapshot bonus average-earnings fact repository is required"
        );
        this.manifests = Objects.requireNonNull(
                manifests,
                "Snapshot bonus average-earnings manifest repository is required"
        );
    }

    @Transactional
    public FreezeResult freeze(
            PayrollSnapshot snapshot,
            List<PayrollBonusSourceFactService.BonusFact> sourceFacts,
            List<PayrollBonusAverageEarningsFactService.AverageFact> averageFacts
    ) {
        Objects.requireNonNull(
                snapshot,
                "Snapshot bonus average-earnings freeze requires snapshot"
        );

        List<PayrollBonusSourceFactService.BonusFact> sources =
                List.copyOf(Objects.requireNonNull(
                        sourceFacts,
                        "Snapshot bonus source facts are required"
                ));

        List<PayrollBonusAverageEarningsFactService.AverageFact> averages =
                List.copyOf(Objects.requireNonNull(
                        averageFacts,
                        "Snapshot bonus average-earnings facts are required"
                ));

        if (snapshot.getPeriodMonth() == null
                || snapshot.getCurrencyCode() == null
                || !snapshot.getCurrencyCode().matches("[A-Z]{3}")) {
            throw new IllegalStateException(
                    "Snapshot bonus average-earnings freeze requires valid snapshot month/currency"
            );
        }

        YearMonth snapshotMonth = YearMonth.from(snapshot.getPeriodMonth());
        String snapshotCurrency = snapshot.getCurrencyCode();

        Map<Long, PayrollBonusSourceFactService.BonusFact> sourceById =
                new HashMap<>();

        PayrollBonusSourceFactService.BonusFact previousSource = null;

        for (PayrollBonusSourceFactService.BonusFact source : sources) {
            Objects.requireNonNull(source, "Snapshot bonus source fact is required");

            if (previousSource != null
                    && compareSource(previousSource, source) >= 0) {
                throw new IllegalStateException(
                        "Snapshot bonus source facts are not in canonical D2 order"
                );
            }
            previousSource = source;

            if (sourceById.put(source.factId(), source) != null) {
                throw new IllegalStateException(
                        "Snapshot bonus source facts contain duplicate identity"
                );
            }

            if (!snapshotMonth.equals(YearMonth.from(source.periodFrom()))) {
                throw new IllegalStateException(
                        "Snapshot bonus source fact belongs to another payroll month"
                );
            }

            if (!snapshotCurrency.equals(source.currencyCode())) {
                throw new IllegalStateException(
                        "Snapshot bonus source fact currency contradicts Payroll snapshot"
                );
            }
        }

        Map<Long, PayrollBonusAverageEarningsFactService.AverageFact> averageBySource =
                new HashMap<>();
        Set<Long> averageFactIds = new HashSet<>();

        for (PayrollBonusAverageEarningsFactService.AverageFact average : averages) {
            Objects.requireNonNull(
                    average,
                    "Snapshot bonus average-earnings fact is required"
            );

            if (!averageFactIds.add(average.factId())
                    || averageBySource.put(average.bonusSourceFactId(), average) != null) {
                throw new IllegalStateException(
                        "Snapshot bonus average-earnings facts contain duplicate identity"
                );
            }

            PayrollBonusSourceFactService.BonusFact source =
                    sourceById.get(average.bonusSourceFactId());

            if (source == null) {
                throw new IllegalStateException(
                        "Snapshot bonus average-earnings fact has no source fact"
                );
            }

            if (average.componentId() != source.componentId()
                    || average.earningKind() != source.earningKind()) {
                throw new IllegalStateException(
                        "Snapshot bonus average-earnings fact contradicts source identity"
                );
            }
        }

        List<PayrollSnapshotBonusAverageEarningsFact> frozen =
                new ArrayList<>();

        for (PayrollBonusSourceFactService.BonusFact source : sources) {
            PayrollBonusAverageEarningsFactService.AverageFact average =
                    averageBySource.get(source.factId());

            if (average == null) {
                continue;
            }

            frozen.add(
                    new PayrollSnapshotBonusAverageEarningsFact(
                            snapshot,
                            frozen.size(),
                            source.factId(),
                            average.factId(),
                            source.componentId(),
                            source.earningKind(),
                            source.periodFrom(),
                            source.periodTo(),
                            source.amountMinor(),
                            source.currencyCode(),
                            average.indicatorKey(),
                            average.awardPeriodFrom(),
                            average.awardPeriodTo(),
                            average.annualResult(),
                            average.accruedForActualWorkTime(),
                            average.proratedForPartialAwardPeriod()
                    )
            );
        }

        if (!frozen.isEmpty()) {
            facts.saveAll(frozen);
        }

        String fingerprint =
                PayrollBonusAverageEarningsFingerprint.calculate(
                        sources.size(),
                        frozen
                );

        PayrollSnapshotBonusAverageEarningsManifest manifest =
                new PayrollSnapshotBonusAverageEarningsManifest(
                        snapshot,
                        sources.size(),
                        frozen.size(),
                        fingerprint
                );

        manifests.saveAndFlush(manifest);

        return new FreezeResult(frozen, manifest);
    }

    private static int compareSource(
            PayrollBonusSourceFactService.BonusFact left,
            PayrollBonusSourceFactService.BonusFact right
    ) {
        int comparison = left.earningKind().name().compareTo(right.earningKind().name());
        if (comparison != 0) return comparison;

        comparison = Long.compare(left.componentId(), right.componentId());
        if (comparison != 0) return comparison;

        comparison = left.periodFrom().compareTo(right.periodFrom());
        if (comparison != 0) return comparison;

        comparison = left.periodTo().compareTo(right.periodTo());
        if (comparison != 0) return comparison;

        return Long.compare(left.factId(), right.factId());
    }

    public record FreezeResult(
            List<PayrollSnapshotBonusAverageEarningsFact> facts,
            PayrollSnapshotBonusAverageEarningsManifest manifest
    ) {
        public FreezeResult {
            facts = List.copyOf(Objects.requireNonNull(
                    facts,
                    "Frozen snapshot bonus average-earnings facts are required"
            ));
            Objects.requireNonNull(
                    manifest,
                    "Snapshot bonus average-earnings manifest is required"
            );
        }
    }
}
