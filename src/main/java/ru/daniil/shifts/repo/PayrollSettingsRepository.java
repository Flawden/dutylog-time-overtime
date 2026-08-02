package ru.daniil.shifts.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.PayrollSettings;

import java.util.Optional;

public interface PayrollSettingsRepository extends JpaRepository<PayrollSettings, Long> {
    Optional<PayrollSettings> findByOwner(AppUser owner);
}
