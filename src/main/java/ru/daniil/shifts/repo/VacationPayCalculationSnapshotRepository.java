package ru.daniil.shifts.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.VacationPayCalculationSnapshot;

import java.util.Optional;

public interface VacationPayCalculationSnapshotRepository
        extends JpaRepository<VacationPayCalculationSnapshot, Long> {

    Optional<VacationPayCalculationSnapshot>
    findFirstByOwnerAndAbsencePeriod_IdOrderByIdDesc(AppUser owner, Long absencePeriodId);
}
