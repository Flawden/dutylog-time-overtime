package ru.daniil.shifts.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.CompensationTerm;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CompensationTermRepository extends JpaRepository<CompensationTerm, Long> {
    Optional<CompensationTerm> findByOwnerAndEffectiveFrom(AppUser owner, LocalDate effectiveFrom);
    Optional<CompensationTerm> findFirstByOwnerAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(AppUser owner, LocalDate date);
    List<CompensationTerm> findByOwnerOrderByEffectiveFromDesc(AppUser owner);
}
