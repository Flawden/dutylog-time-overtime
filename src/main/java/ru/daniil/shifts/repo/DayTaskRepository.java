package ru.daniil.shifts.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.DayTask;

import java.time.LocalDate;
import java.util.List;

public interface DayTaskRepository extends JpaRepository<DayTask, Long> {
    List<DayTask> findByOwnerAndDateOrderByCreatedAtAscIdAsc(AppUser owner, LocalDate date);
    List<DayTask> findByOwnerAndDateBetweenOrderByDateAscCreatedAtAscIdAsc(AppUser owner, LocalDate from, LocalDate to);
    List<DayTask> findByOwnerAndDueDateBetweenOrderByDueDateAscDueTimeAscCreatedAtAscIdAsc(AppUser owner, LocalDate from, LocalDate to);
    List<DayTask> findByOwnerOrderByDoneAscDueDateAscDueTimeAscDateAscCreatedAtAscIdAsc(AppUser owner);

    @Query("select distinct t.category from DayTask t where t.owner = :owner and t.category is not null")
    List<String> findDistinctCategories(@Param("owner") AppUser owner);

    @Query("select distinct tag from DayTask t join t.tags tag where t.owner = :owner")
    List<String> findDistinctTags(@Param("owner") AppUser owner);

    @Query("select distinct t.project from DayTask t where t.owner = :owner and t.project is not null")
    List<String> findDistinctProjects(@Param("owner") AppUser owner);
}
