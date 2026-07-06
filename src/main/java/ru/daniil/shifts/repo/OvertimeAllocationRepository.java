package ru.daniil.shifts.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.OvertimeAllocation;
import ru.daniil.shifts.model.OvertimeCredit;
import ru.daniil.shifts.model.OvertimeUsage;

import java.util.List;

public interface OvertimeAllocationRepository extends JpaRepository<OvertimeAllocation, Long> {
    @Query("select a from OvertimeAllocation a where a.credit.owner = :owner")
    List<OvertimeAllocation> findAllByOwner(@Param("owner") AppUser owner);

    @Query("select coalesce(sum(a.hours), 0) from OvertimeAllocation a where a.credit = :credit")
    double sumHoursByCredit(@Param("credit") OvertimeCredit credit);

    List<OvertimeAllocation> findByUsage(OvertimeUsage usage);
    List<OvertimeAllocation> findByCredit(OvertimeCredit credit);

    void deleteByUsage(OvertimeUsage usage);
}
