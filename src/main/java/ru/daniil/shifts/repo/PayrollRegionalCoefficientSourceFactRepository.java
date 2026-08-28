package ru.daniil.shifts.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.PayrollRegionalCoefficientSourceFact;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PayrollRegionalCoefficientSourceFactRepository
        extends JpaRepository<PayrollRegionalCoefficientSourceFact, Long> {

    List<PayrollRegionalCoefficientSourceFact>
    findByOwnerAndComponentIdOrderByPeriodFromAscPeriodToAscIdAsc(
            AppUser owner,
            long componentId
    );

    List<PayrollRegionalCoefficientSourceFact>
    findByOwnerAndPeriodFromBetweenOrderByComponentIdAscPeriodFromAscPeriodToAscIdAsc(
            AppUser owner,
            LocalDate from,
            LocalDate to
    );

    Optional<PayrollRegionalCoefficientSourceFact>
    findByOwnerAndId(
            AppUser owner,
            Long id
    );
}
