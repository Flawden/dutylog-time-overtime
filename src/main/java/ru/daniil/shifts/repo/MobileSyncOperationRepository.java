package ru.daniil.shifts.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.MobileSyncOperation;

import java.time.Instant;
import java.util.Optional;

public interface MobileSyncOperationRepository extends JpaRepository<MobileSyncOperation, Long> {
    Optional<MobileSyncOperation> findByOwnerAndOperationId(AppUser owner, String operationId);
    long deleteByCreatedAtBefore(Instant threshold);
}
