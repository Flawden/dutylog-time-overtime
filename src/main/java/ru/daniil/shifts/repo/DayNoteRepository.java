package ru.daniil.shifts.repo;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    @Query("""
            select n from DayNote n
            where n.owner = :owner
              and (:fromDate is null or n.date >= :fromDate)
              and (:toDate is null or n.date <= :toDate)
              and (
                    lower(coalesce(n.title, '')) like lower(concat('%', :query, '%'))
                 or lower(coalesce(n.content, '')) like lower(concat('%', :query, '%'))
              )
            order by n.date desc, n.pinned desc, n.updatedAt desc, n.id desc
            """)
    List<DayNote> search(@Param("owner") AppUser owner,
                         @Param("query") String query,
                         @Param("fromDate") LocalDate fromDate,
                         @Param("toDate") LocalDate toDate,
                         Pageable pageable);

    long countByOwner(AppUser owner);

    List<DayNote> findByOwnerOrderByDateAscPinnedDescSortOrderAscCreatedAtAscIdAsc(AppUser owner);
}
