package ru.daniil.shifts.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.daniil.shifts.model.PayrollSnapshot;
import ru.daniil.shifts.model.PayrollSnapshotEarningManifest;

import java.util.Optional;

public interface PayrollSnapshotEarningManifestRepository
        extends JpaRepository<PayrollSnapshotEarningManifest, Long> {

    Optional<PayrollSnapshotEarningManifest>
    findBySnapshot(
            PayrollSnapshot snapshot
    );
}
