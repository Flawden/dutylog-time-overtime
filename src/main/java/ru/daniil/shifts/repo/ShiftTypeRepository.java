package ru.daniil.shifts.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.ShiftType;

import java.util.List;

public interface ShiftTypeRepository extends JpaRepository<ShiftType, Long> {
    List<ShiftType> findByOwner(AppUser owner);
    List<ShiftType> findByOwnerAndName(AppUser owner, String name);
}
