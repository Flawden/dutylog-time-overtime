package ru.daniil.shifts.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.daniil.shifts.model.*;
import java.util.Optional;

public interface PayrollSnapshotArticle153Repository extends JpaRepository<PayrollSnapshotArticle153, Long> {
    Optional<PayrollSnapshotArticle153> findBySnapshot_IdAndSnapshot_Owner(Long snapshotId, AppUser owner);
}
