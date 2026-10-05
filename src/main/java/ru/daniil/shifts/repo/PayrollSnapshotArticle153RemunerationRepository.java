package ru.daniil.shifts.repo;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.daniil.shifts.model.*;
import java.util.Optional;
public interface PayrollSnapshotArticle153RemunerationRepository extends JpaRepository<PayrollSnapshotArticle153Remuneration,Long> {
    Optional<PayrollSnapshotArticle153Remuneration> findBySnapshotIdAndTariff_Authority_Snapshot_Owner(Long snapshotId,AppUser owner);
}
