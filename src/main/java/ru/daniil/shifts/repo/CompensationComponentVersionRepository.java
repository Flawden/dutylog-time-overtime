package ru.daniil.shifts.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.CompensationComponent;
import ru.daniil.shifts.model.CompensationComponentVersion;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CompensationComponentVersionRepository
        extends JpaRepository<CompensationComponentVersion, Long> {

    // Serialize competing certifications and formula writes on the same persisted version.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from CompensationComponentVersion v where v.id = :id and v.component.owner = :owner")
    Optional<CompensationComponentVersion> findOwnedForArticle153Certification(
            @Param("owner") AppUser owner, @Param("id") Long id);

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

    @Query("""
            select version
            from CompensationComponentVersion version
            join fetch version.component component
            where component.owner = :owner
            order by component.id asc,
                     version.effectiveFrom desc,
                     version.id desc
            """)
    List<CompensationComponentVersion>
    findOwnerHistory(
            @Param("owner") AppUser owner
    );
}
