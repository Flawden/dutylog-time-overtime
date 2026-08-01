package ru.daniil.shifts.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.TimeAccountingPeriod;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TimeAccountingPeriodRepository extends JpaRepository<TimeAccountingPeriod, Long> {
    Optional<TimeAccountingPeriod> findByOwnerAndPeriodMonth(AppUser owner, LocalDate periodMonth);
    List<TimeAccountingPeriod> findByOwnerAndPeriodMonthBetweenOrderByPeriodMonthAsc(
            AppUser owner, LocalDate from, LocalDate to);
}
