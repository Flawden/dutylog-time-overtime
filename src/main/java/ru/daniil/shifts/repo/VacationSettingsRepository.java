package ru.daniil.shifts.repo;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.VacationSettings;

import java.util.Optional;

public interface VacationSettingsRepository extends JpaRepository<VacationSettings, Long> {
    Optional<VacationSettings> findByOwner(AppUser owner);

    /** Serializes allowance-sensitive writes for one owner. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from VacationSettings s where s.owner = :owner")
    Optional<VacationSettings> findForUpdateByOwner(@Param("owner") AppUser owner);
}
