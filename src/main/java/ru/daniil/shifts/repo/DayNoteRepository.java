package ru.daniil.shifts.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.DayNote;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DayNoteRepository extends JpaRepository<DayNote, Long> {
    Optional<DayNote> findByOwnerAndId(AppUser owner, Long id);

    List<DayNote> findByOwnerAndDateOrderByPinnedDescSortOrderAscCreatedAtAscIdAsc(
            AppUser owner, LocalDate date);

    List<DayNote> findByOwnerAndDateBetweenOrderByDateAscPinnedDescSortOrderAscCreatedAtAscIdAsc(
            AppUser owner, LocalDate from, LocalDate to);

    long countByOwner(AppUser owner);

    List<DayNote> findByOwnerOrderByDateAscPinnedDescSortOrderAscCreatedAtAscIdAsc(AppUser owner);
}
