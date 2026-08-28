package ru.daniil.shifts.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.PayrollCombinationEpisodeFact;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PayrollCombinationEpisodeFactRepository
        extends JpaRepository<PayrollCombinationEpisodeFact, Long> {

    List<PayrollCombinationEpisodeFact>
    findByOwnerAndComponentIdOrderByPeriodFromAscPeriodToAscIdAsc(
            AppUser owner,
            long componentId
    );

    List<PayrollCombinationEpisodeFact>
    findByOwnerAndPeriodFromBetweenOrderByComponentIdAscPeriodFromAscPeriodToAscIdAsc(
            AppUser owner,
            LocalDate from,
            LocalDate to
    );

    Optional<PayrollCombinationEpisodeFact>
    findByOwnerAndId(
            AppUser owner,
            Long id
    );
}
