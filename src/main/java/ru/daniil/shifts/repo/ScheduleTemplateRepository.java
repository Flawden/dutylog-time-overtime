package ru.daniil.shifts.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.ScheduleTemplate;
import ru.daniil.shifts.model.ShiftType;

import java.util.List;
import java.util.Optional;

public interface ScheduleTemplateRepository extends JpaRepository<ScheduleTemplate, Long> {
    List<ScheduleTemplate> findByOwnerOrderBySortOrderAscIdAsc(AppUser owner);
    Optional<ScheduleTemplate> findByOwnerAndName(AppUser owner, String name);

    @Query("select count(t) from ScheduleTemplate t join t.steps step where step.shiftType = :shiftType")
    long countUsingShiftType(@Param("shiftType") ShiftType shiftType);
}
