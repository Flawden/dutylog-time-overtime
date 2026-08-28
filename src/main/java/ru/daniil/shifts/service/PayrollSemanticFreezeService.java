package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.model.PayrollSnapshot;
import ru.daniil.shifts.model.PayrollSnapshotEarningLine;
import ru.daniil.shifts.model.PayrollSnapshotEarningManifest;
import ru.daniil.shifts.repo.PayrollSnapshotEarningLineRepository;
import ru.daniil.shifts.repo.PayrollSnapshotEarningManifestRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Persists one immutable semantic earning freeze beside one immutable Payroll
 * snapshot revision.
 *
 * A manifest is always persisted for production snapshots wired through this
 * service. It is complete only when every positive earning source is currently
 * represented by a proven machine-owned PayrollEarningKind.
 */
@Service
public class PayrollSemanticFreezeService {

    private final PayrollSnapshotEarningLineRepository earningLines;
    private final PayrollSnapshotEarningManifestRepository manifests;

    public PayrollSemanticFreezeService(
            PayrollSnapshotEarningLineRepository earningLines,
            PayrollSnapshotEarningManifestRepository manifests
    ) {
        this.earningLines =
                Objects.requireNonNull(
                        earningLines,
                        "Payroll snapshot earning line repository is required"
                );

        this.manifests =
                Objects.requireNonNull(
                        manifests,
                        "Payroll snapshot earning manifest repository is required"
                );
    }

    @Transactional
    public FreezeResult freeze(
            PayrollSnapshot snapshot,
            PayrollSemanticFreezeProjection.Source source
    ) {
        Objects.requireNonNull(
                snapshot,
                "Payroll snapshot is required"
        );

        PayrollSemanticFreezeProjection.Projection projection =
                PayrollSemanticFreezeProjection.project(
                        source
                );

        if (projection.totalEarningSourceAmountMinor()
                != source.totalEarningSourceAmountMinor()) {
            throw new IllegalStateException(
                    "Semantic freeze projection lost Payroll earning money"
            );
        }

        List<PayrollSnapshotEarningLine> frozen =
                new ArrayList<>();

        for (int index = 0;
                index < projection.classifiedLines().size();
                index++) {

            PayrollSemanticFreezeProjection.SemanticLine sourceLine =
                    projection.classifiedLines()
                            .get(
                                    index
                            );

            frozen.add(
                    new PayrollSnapshotEarningLine(
                            snapshot,
                            index,
                            sourceLine.earningKind(),
                            sourceLine.amountMinor(),
                            sourceLine.qualifiedQuantity(),
                            sourceLine.earningPeriodFrom(),
                            sourceLine.earningPeriodTo(),
                            sourceLine.coverageFrom(),
                            sourceLine.coverageTo()
                    )
            );
        }

        if (!frozen.isEmpty()) {
            earningLines.saveAll(
                    frozen
            );
        }

        String fingerprint =
                PayrollSemanticEarningFingerprint.calculate(
                        frozen
                );

        PayrollSnapshotEarningManifest manifest =
                new PayrollSnapshotEarningManifest(
                        snapshot,
                        projection.complete(),
                        frozen.size(),
                        projection.classifiedAmountMinor(),
                        projection.unclassifiedAmountMinor(),
                        fingerprint
                );

        manifests.saveAndFlush(
                manifest
        );

        return new FreezeResult(
                frozen,
                manifest,
                projection
        );
    }

    public record FreezeResult(
            List<PayrollSnapshotEarningLine> lines,
            PayrollSnapshotEarningManifest manifest,
            PayrollSemanticFreezeProjection.Projection projection
    ) {
        public FreezeResult {
            lines =
                    List.copyOf(
                            Objects.requireNonNull(
                                    lines,
                                    "Frozen semantic lines are required"
                            )
                    );

            Objects.requireNonNull(
                    manifest,
                    "Semantic earning manifest is required"
            );

            Objects.requireNonNull(
                    projection,
                    "Semantic freeze projection is required"
            );
        }
    }
}
