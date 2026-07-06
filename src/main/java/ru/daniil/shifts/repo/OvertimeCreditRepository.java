package ru.daniil.shifts.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.OvertimeCredit;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface OvertimeCreditRepository extends JpaRepository<OvertimeCredit, Long> {
    List<OvertimeCredit> findByOwnerOrderByWorkDateAscIdAsc(AppUser owner);
    List<OvertimeCredit> findByOwnerAndWorkDateBetweenOrderByWorkDateAscIdAsc(AppUser owner, LocalDate from, LocalDate to);
    Optional<OvertimeCredit> findByOwnerAndId(AppUser owner, Long id);
}
