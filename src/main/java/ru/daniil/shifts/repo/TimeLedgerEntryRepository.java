package ru.daniil.shifts.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.TimeLedgerEntry;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TimeLedgerEntryRepository extends JpaRepository<TimeLedgerEntry, Long> {
    List<TimeLedgerEntry> findByOwnerAndEffectiveDateBetweenOrderByEffectiveDateAscIdAsc(
            AppUser owner, LocalDate from, LocalDate to);
    List<TimeLedgerEntry> findByOwnerAndSourceKindAndSourceIdOrderByIdAsc(
            AppUser owner, String sourceKind, Long sourceId);
    Optional<TimeLedgerEntry> findFirstByOwnerAndSourceKindAndSourceIdOrderByIdDesc(
            AppUser owner, String sourceKind, Long sourceId);
}
