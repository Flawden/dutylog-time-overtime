package ru.daniil.shifts.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.daniil.shifts.model.Article153ComponentAuthority;
import ru.daniil.shifts.model.CompensationComponentVersion;
import java.util.Optional;

public interface Article153ComponentAuthorityRepository
        extends JpaRepository<Article153ComponentAuthority, Long> {
    Optional<Article153ComponentAuthority> findByComponentVersion(CompensationComponentVersion version);
}
