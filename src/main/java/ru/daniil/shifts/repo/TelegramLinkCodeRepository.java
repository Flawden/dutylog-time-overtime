package ru.daniil.shifts.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.TelegramLinkCode;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TelegramLinkCodeRepository extends JpaRepository<TelegramLinkCode, Long> {
    boolean existsByCode(String code);
    Optional<TelegramLinkCode> findByCodeAndUsedAtIsNull(String code);
    List<TelegramLinkCode> findByOwnerAndUsedAtIsNullOrderByCreatedAtDesc(AppUser owner);
    void deleteByOwnerAndUsedAtIsNull(AppUser owner);
    void deleteByExpiresAtBeforeAndUsedAtIsNull(Instant now);
}
