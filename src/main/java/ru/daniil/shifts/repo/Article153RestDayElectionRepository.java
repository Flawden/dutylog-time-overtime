package ru.daniil.shifts.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.Article153RestDayElection;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface Article153RestDayElectionRepository
        extends JpaRepository<Article153RestDayElection, Long> {

    Optional<Article153RestDayElection> findByOwnerAndWorkDateAndSourceIdentity(
            AppUser owner,
            LocalDate workDate,
            String sourceIdentity
    );

    List<Article153RestDayElection> findByOwnerAndWorkDateBetweenOrderByWorkDateAscIdAsc(
            AppUser owner,
            LocalDate from,
            LocalDate to
    );
}
