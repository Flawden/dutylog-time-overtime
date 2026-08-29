package ru.daniil.shifts.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.daniil.shifts.model.PayrollSnapshot;
import ru.daniil.shifts.model.PayrollSnapshotP15WorkTimeManifest;

import java.util.Optional;

public interface PayrollSnapshotP15WorkTimeManifestRepository
        extends JpaRepository<PayrollSnapshotP15WorkTimeManifest, Long> {

    Optional<PayrollSnapshotP15WorkTimeManifest> findBySnapshot(PayrollSnapshot snapshot);
}
