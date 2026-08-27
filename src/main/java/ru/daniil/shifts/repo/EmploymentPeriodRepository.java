package ru.daniil.shifts.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.EmploymentPeriod;

import java.util.List;
import java.util.Optional;

public interface EmploymentPeriodRepository
        extends JpaRepository<EmploymentPeriod, Long> {

    List<EmploymentPeriod>
    findByOwnerOrderByStartDateAscIdAsc(
            AppUser owner
    );

    Optional<EmploymentPeriod>
    findByOwnerAndId(
            AppUser owner,
            Long id
    );
}
