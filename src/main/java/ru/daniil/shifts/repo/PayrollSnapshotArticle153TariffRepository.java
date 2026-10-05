package ru.daniil.shifts.repo;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.daniil.shifts.model.*;
import java.util.Optional;
public interface PayrollSnapshotArticle153TariffRepository extends JpaRepository<PayrollSnapshotArticle153Tariff, Long> {
    Optional<PayrollSnapshotArticle153Tariff> findBySnapshotIdAndAuthority_Snapshot_Owner(Long snapshotId, AppUser owner);
}
