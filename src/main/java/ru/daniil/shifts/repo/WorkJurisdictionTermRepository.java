package ru.daniil.shifts.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.WorkJurisdictionTerm;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface WorkJurisdictionTermRepository
        extends JpaRepository<WorkJurisdictionTerm, Long> {

    Optional<WorkJurisdictionTerm> findByOwnerAndEffectiveFrom(
            AppUser owner,
            LocalDate effectiveFrom
    );

    Optional<WorkJurisdictionTerm>
    findFirstByOwnerAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
            AppUser owner,
            LocalDate date
    );

    List<WorkJurisdictionTerm>
    findByOwnerOrderByEffectiveFromAscIdAsc(AppUser owner);
}
