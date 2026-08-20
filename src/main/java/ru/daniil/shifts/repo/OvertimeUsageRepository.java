package ru.daniil.shifts.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.OvertimeUsage;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface OvertimeUsageRepository extends JpaRepository<OvertimeUsage, Long> {
    List<OvertimeUsage> findByOwnerOrderByUsageDateAscIdAsc(AppUser owner);
    List<OvertimeUsage> findByOwnerAndUsageDateBetweenOrderByUsageDateAscIdAsc(AppUser owner, LocalDate from, LocalDate to);
    Optional<OvertimeUsage> findByOwnerAndId(AppUser owner, Long id);
    Optional<OvertimeUsage> findByOwnerAndSourceAbsenceId(
            AppUser owner,
            Long sourceAbsenceId
    );

    Optional<OvertimeUsage> findByOwnerAndSourceSettlementId(
            AppUser owner,
            Long sourceSettlementId
    );
}
