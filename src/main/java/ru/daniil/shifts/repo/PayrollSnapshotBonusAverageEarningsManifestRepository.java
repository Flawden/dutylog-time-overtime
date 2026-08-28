package ru.daniil.shifts.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.daniil.shifts.model.PayrollSnapshot;
import ru.daniil.shifts.model.PayrollSnapshotBonusAverageEarningsManifest;

import java.util.Optional;

public interface PayrollSnapshotBonusAverageEarningsManifestRepository
        extends JpaRepository<PayrollSnapshotBonusAverageEarningsManifest, Long> {

    Optional<PayrollSnapshotBonusAverageEarningsManifest>
    findBySnapshot(PayrollSnapshot snapshot);
}
