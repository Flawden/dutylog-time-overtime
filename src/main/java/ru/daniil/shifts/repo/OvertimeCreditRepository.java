package ru.daniil.shifts.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.OvertimeCredit;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OvertimeCreditRepository extends JpaRepository<OvertimeCredit, Long> {
    List<OvertimeCredit> findByOwnerOrderByWorkDateAscIdAsc(AppUser owner);
    List<OvertimeCredit> findByOwnerAndWorkDateBetweenOrderByWorkDateAscIdAsc(AppUser owner, LocalDate from, LocalDate to);
    Optional<OvertimeCredit> findByOwnerAndId(AppUser owner, Long id);
    List<OvertimeCredit> findByOwnerAndWorkDateOrderByIdAsc(AppUser owner, LocalDate workDate);
    Optional<OvertimeCredit> findByOwnerAndWorkDateAndSourceKind(AppUser owner, LocalDate workDate, String sourceKind);

    /**
     * Для начислений с точным временем запрещаем пересечения,
     * иначе один и тот же ночной/суточный период можно засчитать дважды.
     */
    List<OvertimeCredit> findByOwnerAndStartAtLessThanAndEndAtGreaterThan(AppUser owner, LocalDateTime endAt, LocalDateTime startAt);

    List<OvertimeCredit> findByOwnerAndStartAtInstantLessThanAndEndAtInstantGreaterThan(
            AppUser owner, Instant endAtInstant, Instant startAtInstant);
}
