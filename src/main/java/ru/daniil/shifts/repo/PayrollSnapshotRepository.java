package ru.daniil.shifts.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.PayrollSnapshot;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PayrollSnapshotRepository extends JpaRepository<PayrollSnapshot, Long> {
    List<PayrollSnapshot> findByOwnerAndPeriodMonthOrderByRevisionDesc(AppUser owner, LocalDate periodMonth);
    Optional<PayrollSnapshot> findFirstByOwnerAndPeriodMonthOrderByRevisionDesc(AppUser owner, LocalDate periodMonth);
}
