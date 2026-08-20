package ru.daniil.shifts.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.daniil.shifts.model.ActualWorkInterval;
import ru.daniil.shifts.model.AppUser;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ActualWorkIntervalRepository extends JpaRepository<ActualWorkInterval, Long> {
    List<ActualWorkInterval> findByOwnerAndWorkDateBetweenOrderByWorkDateAscStartTimeAscIdAsc(
            AppUser owner, LocalDate from, LocalDate to);
    @Query("select a from ActualWorkInterval a where a.owner = :owner and a.workDate <= :to and a.endDate >= :from order by a.workDate asc, a.startTime asc, a.id asc")
    List<ActualWorkInterval> findOverlappingRange(@Param("owner") AppUser owner, @Param("from") LocalDate from, @Param("to") LocalDate to);
    List<ActualWorkInterval> findByOwnerAndWorkDateOrderByStartTimeAscIdAsc(AppUser owner, LocalDate workDate);

    /**
     * Unbounded Temporal Work Context tail.
     *
     * Exact LocalDateTime overlap is filtered by WorkTimezoneChangeService;
     * this query only avoids loading facts that ended before the changed term.
     */
    List<ActualWorkInterval> findByOwnerAndEndDateGreaterThanEqualOrderByWorkDateAscStartTimeAscIdAsc(
            AppUser owner,
            LocalDate from
    );

    Optional<ActualWorkInterval> findByOwnerAndId(AppUser owner, Long id);
}
