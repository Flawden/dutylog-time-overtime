package ru.daniil.shifts.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.DayTask;

import java.time.LocalDate;
import java.util.List;

public interface DayTaskRepository extends JpaRepository<DayTask, Long> {
    List<DayTask> findByOwnerAndDateOrderByCreatedAtAscIdAsc(AppUser owner, LocalDate date);
    List<DayTask> findByOwnerAndDateBetweenOrderByDateAscCreatedAtAscIdAsc(AppUser owner, LocalDate from, LocalDate to);
    List<DayTask> findByOwnerAndDueDateBetweenOrderByDueDateAscDueTimeAscCreatedAtAscIdAsc(AppUser owner, LocalDate from, LocalDate to);
}
