package ru.daniil.shifts.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.CompensationComponent;

import java.util.List;
import java.util.Optional;

public interface CompensationComponentRepository
        extends JpaRepository<CompensationComponent, Long> {

    Optional<CompensationComponent> findByOwnerAndId(
            AppUser owner,
            Long id
    );

    List<CompensationComponent> findByOwnerOrderByIdAsc(
            AppUser owner
    );
}
