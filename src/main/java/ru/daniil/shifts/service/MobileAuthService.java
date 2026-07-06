package ru.daniil.shifts.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.dto.Dtos.MobileAuthTokenDto;
import ru.daniil.shifts.dto.Dtos.MobileLoginRequest;
import ru.daniil.shifts.dto.Dtos.MobileTokenResponse;
import ru.daniil.shifts.dto.Dtos.MobileUserDto;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.MobileAuthToken;
import ru.daniil.shifts.repo.MobileAuthTokenRepository;
import ru.daniil.shifts.repo.UserRepository;
import ru.daniil.shifts.service.exception.ApiException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

@Service
public class MobileAuthService {
    private static final Duration ACCESS_TTL = Duration.ofMinutes(30);
    private static final Duration REFRESH_TTL = Duration.ofDays(45);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final MobileAuthTokenRepository tokens;
    private final PasswordEncoder passwordEncoder;

    public MobileAuthService(UserRepository users,
                             MobileAuthTokenRepository tokens,
                             PasswordEncoder passwordEncoder) {
        this.users = users;
        this.tokens = tokens;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public MobileTokenResponse login(MobileLoginRequest req) {
        if (req == null) {
            throw ApiException.badRequest("Некорректный JSON в запросе");
        }
        String username = req.username() == null ? "" : req.username().trim();
        String password = req.password() == null ? "" : req.password();
        AppUser user = users.findByUsername(username)
                .orElseThrow(() -> ApiException.badRequest("Неверный логин или пароль"));
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw ApiException.badRequest("Неверный логин или пароль");
        }
        return issueNewTokenPair(user, normalizeDeviceName(req.deviceName()));
    }

    @Transactional
    public MobileTokenResponse refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw ApiException.badRequest("Не указан refreshToken");
        }

        MobileAuthToken existing = tokens.findByRefreshTokenHash(hash(refreshToken))
                .orElseThrow(() -> ApiException.badRequest("Refresh token недействителен"));

        Instant now = Instant.now();
        if (existing.isRevoked() || !existing.getRefreshExpiresAt().isAfter(now)) {
            existing.revoke();
            tokens.save(existing);
            throw ApiException.badRequest("Refresh token истёк или был отозван");
        }

        // Ротация: старый access и refresh заменяются новой парой.
        String access = randomToken();
        String refresh = randomToken();
        existing.setAccessTokenHash(hash(access));
        existing.setRefreshTokenHash(hash(refresh));
        existing.setAccessExpiresAt(now.plus(ACCESS_TTL));
        existing.setRefreshExpiresAt(now.plus(REFRESH_TTL));
        existing.setLastUsedAt(now);
        MobileAuthToken saved = tokens.save(existing);

        return toResponse(access, refresh, saved);
    }

    @Transactional(readOnly = true)
    public Optional<AppUser> authenticateAccessToken(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            return Optional.empty();
        }
        return tokens.findByAccessTokenHash(hash(accessToken))
                .filter(token -> !token.isRevoked())
                .filter(token -> token.getAccessExpiresAt().isAfter(Instant.now()))
                .map(token -> {
                    AppUser owner = token.getOwner();
                    owner.getUsername(); // инициализируем lazy-proxy внутри транзакции
                    return owner;
                });
    }

    @Transactional
    public void touchAccessToken(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            return;
        }
        tokens.findByAccessTokenHash(hash(accessToken))
                .filter(token -> !token.isRevoked())
                .ifPresent(token -> {
                    token.setLastUsedAt(Instant.now());
                    tokens.save(token);
                });
    }

    @Transactional
    public void logoutByRefreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        tokens.findByRefreshTokenHash(hash(refreshToken)).ifPresent(token -> {
            token.revoke();
            tokens.save(token);
        });
    }

    @Transactional
    public void logoutCurrentAccessToken(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            return;
        }
        tokens.findByAccessTokenHash(hash(accessToken)).ifPresent(token -> {
            token.revoke();
            tokens.save(token);
        });
    }

    @Transactional(readOnly = true)
    public List<MobileAuthTokenDto> sessions(AppUser user) {
        Instant now = Instant.now();
        return tokens.findByOwnerOrderByCreatedAtDesc(user).stream()
                .map(token -> new MobileAuthTokenDto(
                        token.getId(),
                        token.getDeviceName(),
                        token.getCreatedAt().toString(),
                        token.getLastUsedAt() != null ? token.getLastUsedAt().toString() : null,
                        token.getRefreshExpiresAt().toString(),
                        token.isRevoked(),
                        !token.isRevoked() && token.getRefreshExpiresAt().isAfter(now)
                ))
                .toList();
    }

    /** Отзывает все мобильные сессии пользователя — вызывается при смене пароля. */
    @Transactional
    public void revokeAllSessions(AppUser user) {
        tokens.findByOwnerOrderByCreatedAtDesc(user).forEach(token -> {
            if (!token.isRevoked()) {
                token.revoke();
                tokens.save(token);
            }
        });
    }

    @Transactional
    public void revokeSession(AppUser user, Long id) {
        if (id == null) {
            throw ApiException.badRequest("Не указан id сессии");
        }
        MobileAuthToken token = tokens.findById(id)
                .orElseThrow(() -> ApiException.notFound("Сессия не найдена"));
        if (!token.getOwner().getId().equals(user.getId())) {
            throw ApiException.notFound("Сессия не найдена");
        }
        token.revoke();
        tokens.save(token);
    }

    private MobileTokenResponse issueNewTokenPair(AppUser user, String deviceName) {
        Instant now = Instant.now();
        String access = randomToken();
        String refresh = randomToken();
        MobileAuthToken saved = tokens.save(new MobileAuthToken(
                user,
                hash(access),
                hash(refresh),
                now.plus(ACCESS_TTL),
                now.plus(REFRESH_TTL),
                deviceName
        ));
        return toResponse(access, refresh, saved);
    }

    private MobileTokenResponse toResponse(String access, String refresh, MobileAuthToken token) {
        return new MobileTokenResponse(
                "Bearer",
                access,
                token.getAccessExpiresAt().toString(),
                refresh,
                token.getRefreshExpiresAt().toString(),
                new MobileUserDto(token.getOwner().getUsername())
        );
    }

    private String normalizeDeviceName(String deviceName) {
        if (deviceName == null || deviceName.isBlank()) {
            return "Android";
        }
        String trimmed = deviceName.trim();
        return trimmed.length() > 120 ? trimmed.substring(0, 120) : trimmed;
    }

    private static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hashed.length * 2);
            for (byte b : hashed) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 недоступен", e);
        }
    }
}
