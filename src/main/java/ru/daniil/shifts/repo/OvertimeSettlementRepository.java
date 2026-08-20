package ru.daniil.shifts.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.OvertimeSettlement;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface OvertimeSettlementRepository
        extends JpaRepository<OvertimeSettlement, Long> {

    Optional<OvertimeSettlement> findByOwnerAndId(
            AppUser owner,
            Long id
    );

    List<OvertimeSettlement>
    findByOwnerOrderBySettlementDateAscIdAsc(
            AppUser owner
    );

    List<OvertimeSettlement>
    findByOwnerAndSettlementDateBetweenOrderBySettlementDateAscIdAsc(
            AppUser owner,
            LocalDate from,
            LocalDate to
    );
}
