package ru.daniil.shifts.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.daniil.shifts.model.AbsencePeriod;
import ru.daniil.shifts.model.AbsenceType;
import ru.daniil.shifts.model.AppUser;

import java.time.LocalDate;
import java.util.List;

public interface AbsencePeriodRepository extends JpaRepository<AbsencePeriod, Long> {
    List<AbsencePeriod> findByOwnerOrderByStartDateAscIdAsc(AppUser owner);

    List<AbsencePeriod> findByOwnerAndEndDateGreaterThanEqualAndStartDateLessThanEqualOrderByStartDateAscIdAsc(
            AppUser owner, LocalDate from, LocalDate to);

    boolean existsByType(AbsenceType type);

    @Query("""
            select count(a) from AbsencePeriod a
            where a.owner = :owner
              and a.endDate >= :from
              and a.startDate <= :to
              and (:excludeId is null or a.id <> :excludeId)
            """)
    long countOverlapping(@Param("owner") AppUser owner,
                          @Param("from") LocalDate from,
                          @Param("to") LocalDate to,
                          @Param("excludeId") Long excludeId);
}
