package ru.daniil.shifts.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.daniil.shifts.model.ActualWorkInterval;
import ru.daniil.shifts.model.AppUser;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ActualWorkIntervalRepository extends JpaRepository<ActualWorkInterval, Long> {
    List<ActualWorkInterval> findByOwnerAndWorkDateBetweenOrderByWorkDateAscStartTimeAscIdAsc(
            AppUser owner, LocalDate from, LocalDate to);
    List<ActualWorkInterval> findByOwnerAndWorkDateOrderByStartTimeAscIdAsc(AppUser owner, LocalDate workDate);
    Optional<ActualWorkInterval> findByOwnerAndId(AppUser owner, Long id);
}
