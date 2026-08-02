package ru.daniil.shifts.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.PayrollAdjustment;

import java.time.LocalDate;
import java.util.List;

public interface PayrollAdjustmentRepository extends JpaRepository<PayrollAdjustment, Long> {
    List<PayrollAdjustment> findByOwnerAndPeriodMonthOrderByIdAsc(AppUser owner, LocalDate periodMonth);
}
