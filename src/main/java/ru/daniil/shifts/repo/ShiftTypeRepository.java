package ru.daniil.shifts.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.daniil.shifts.model.ShiftType;

public interface ShiftTypeRepository extends JpaRepository<ShiftType, Long> {
}
