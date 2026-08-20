package ru.daniil.shifts.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.CompensationComponent;
import ru.daniil.shifts.model.CompensationComponentVersion;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CompensationComponentVersionRepository
        extends JpaRepository<CompensationComponentVersion, Long> {

    Optional<CompensationComponentVersion>
    findByComponentAndEffectiveFrom(
            CompensationComponent component,
            LocalDate effectiveFrom
    );

    @Query("""
            select version
            from CompensationComponentVersion version
            join fetch version.component component
            where component.owner = :owner
              and version.effectiveFrom <= :effectiveFrom
            order by component.id asc,
                     version.effectiveFrom desc,
                     version.id desc
            """)
    List<CompensationComponentVersion>
    findOwnerHistoryAtOrBefore(
            @Param("owner") AppUser owner,
            @Param("effectiveFrom") LocalDate effectiveFrom
    );
}
