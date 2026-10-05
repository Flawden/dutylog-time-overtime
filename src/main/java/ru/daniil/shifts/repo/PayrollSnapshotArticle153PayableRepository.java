package ru.daniil.shifts.repo;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.daniil.shifts.model.*;
import java.util.Optional;
public interface PayrollSnapshotArticle153PayableRepository extends JpaRepository<PayrollSnapshotArticle153Payable,Long> {
    Optional<PayrollSnapshotArticle153Payable> findBySnapshotIdAndRemuneration_Tariff_Authority_Snapshot_Owner(Long id,AppUser owner);
}
