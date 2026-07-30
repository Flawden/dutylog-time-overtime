package ru.daniil.shifts.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.daniil.shifts.model.AbsenceType;
import ru.daniil.shifts.model.AppUser;

import java.util.List;
import java.util.Optional;

public interface AbsenceTypeRepository extends JpaRepository<AbsenceType, Long> {
    List<AbsenceType> findByOwnerOrderBySortOrderAscIdAsc(AppUser owner);
    Optional<AbsenceType> findByOwnerAndNameIgnoreCase(AppUser owner, String name);
    Optional<AbsenceType> findByOwnerAndSystemCode(AppUser owner, String systemCode);
}
