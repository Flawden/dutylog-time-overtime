package ru.daniil.shifts.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.daniil.shifts.model.PayrollSnapshot;
import ru.daniil.shifts.model.PayrollSnapshotBonusP15NatureManifest;

import java.util.Optional;

public interface PayrollSnapshotBonusP15NatureManifestRepository
        extends JpaRepository<PayrollSnapshotBonusP15NatureManifest, Long> {

    Optional<PayrollSnapshotBonusP15NatureManifest> findBySnapshot(PayrollSnapshot snapshot);
}
