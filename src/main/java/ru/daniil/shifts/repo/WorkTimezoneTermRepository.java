package ru.daniil.shifts.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.WorkTimezoneTerm;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface WorkTimezoneTermRepository
        extends JpaRepository<WorkTimezoneTerm, Long> {

    Optional<WorkTimezoneTerm> findByOwnerAndEffectiveFrom(
            AppUser owner,
            LocalDateTime effectiveFrom
    );

    Optional<WorkTimezoneTerm>
    findFirstByOwnerAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
            AppUser owner,
            LocalDateTime effectiveFrom
    );

    Optional<WorkTimezoneTerm>
    findFirstByOwnerAndEffectiveFromGreaterThanOrderByEffectiveFromAsc(
            AppUser owner,
            LocalDateTime effectiveFrom
    );

    List<WorkTimezoneTerm>
    findByOwnerOrderByEffectiveFromAscIdAsc(AppUser owner);
}
