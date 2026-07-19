package ru.daniil.shifts.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.MobileAuthToken;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MobileAuthTokenRepository extends JpaRepository<MobileAuthToken, Long> {
    Optional<MobileAuthToken> findByAccessTokenHash(String accessTokenHash);
    Optional<MobileAuthToken> findByRefreshTokenHash(String refreshTokenHash);
    List<MobileAuthToken> findByOwnerOrderByCreatedAtDesc(AppUser owner);
    long deleteByRefreshExpiresAtBefore(Instant threshold);
}
