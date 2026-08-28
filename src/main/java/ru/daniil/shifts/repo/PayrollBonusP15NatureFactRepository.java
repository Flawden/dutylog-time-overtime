package ru.daniil.shifts.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.PayrollBonusP15NatureFact;

import java.util.List;
import java.util.Optional;

public interface PayrollBonusP15NatureFactRepository
        extends JpaRepository<PayrollBonusP15NatureFact, Long> {

    Optional<PayrollBonusP15NatureFact> findByOwnerAndId(
            AppUser owner,
            Long id
    );

    Optional<PayrollBonusP15NatureFact> findByOwnerAndBonusAverageFactId(
            AppUser owner,
            long bonusAverageFactId
    );

    Optional<PayrollBonusP15NatureFact> findByOwnerAndBonusSourceFactId(
            AppUser owner,
            long bonusSourceFactId
    );

    List<PayrollBonusP15NatureFact>
    findByOwnerAndBonusAverageFactIdInOrderByBonusAverageFactIdAscIdAsc(
            AppUser owner,
            List<Long> bonusAverageFactIds
    );
}
