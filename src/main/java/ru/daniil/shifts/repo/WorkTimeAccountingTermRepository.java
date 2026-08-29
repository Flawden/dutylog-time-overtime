package ru.daniil.shifts.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.WorkTimeAccountingTerm;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface WorkTimeAccountingTermRepository
        extends JpaRepository<WorkTimeAccountingTerm, Long> {

    Optional<WorkTimeAccountingTerm> findByOwnerAndEffectiveFrom(
            AppUser owner,
            LocalDate effectiveFrom
    );

    Optional<WorkTimeAccountingTerm>
    findFirstByOwnerAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
            AppUser owner,
            LocalDate date
    );

    List<WorkTimeAccountingTerm>
    findByOwnerOrderByEffectiveFromAscIdAsc(AppUser owner);
}
