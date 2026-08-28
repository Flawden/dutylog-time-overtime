package ru.daniil.shifts.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.PayrollBonusAverageEarningsFact;

import java.util.List;
import java.util.Optional;

public interface PayrollBonusAverageEarningsFactRepository
        extends JpaRepository<PayrollBonusAverageEarningsFact, Long> {

    Optional<PayrollBonusAverageEarningsFact> findByOwnerAndId(
            AppUser owner,
            Long id
    );

    Optional<PayrollBonusAverageEarningsFact> findByOwnerAndBonusSourceFactId(
            AppUser owner,
            long bonusSourceFactId
    );

    List<PayrollBonusAverageEarningsFact>
    findByOwnerAndBonusSourceFactIdInOrderByBonusSourceFactIdAscIdAsc(
            AppUser owner,
            List<Long> bonusSourceFactIds
    );
}
