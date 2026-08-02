package ru.daniil.shifts.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.TimeAccountingPeriod;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TimeAccountingPeriodRepository extends JpaRepository<TimeAccountingPeriod, Long> {
    Optional<TimeAccountingPeriod> findByOwnerAndPeriodMonth(AppUser owner, LocalDate periodMonth);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from TimeAccountingPeriod p where p.owner = :owner and p.periodMonth = :periodMonth")
    Optional<TimeAccountingPeriod> findForUpdateByOwnerAndPeriodMonth(
            @Param("owner") AppUser owner, @Param("periodMonth") LocalDate periodMonth);
    List<TimeAccountingPeriod> findByOwnerAndPeriodMonthBetweenOrderByPeriodMonthAsc(
            AppUser owner, LocalDate from, LocalDate to);
}
