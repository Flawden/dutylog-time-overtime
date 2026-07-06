package ru.daniil.shifts.model;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Мобильная авторизация для Android/PWA-клиентов.
 *
 * В базе лежат только SHA-256 хэши токенов, а не сами токены. Если дамп БД утечёт,
 * готовые access/refresh tokens из него не восстановить.
 */
@Entity
@Table(name = "mobile_auth_tokens", indexes = {
        @Index(name = "idx_mobile_auth_tokens_access_hash", columnList = "access_token_hash", unique = true),
        @Index(name = "idx_mobile_auth_tokens_refresh_hash", columnList = "refresh_token_hash", unique = true),
        @Index(name = "idx_mobile_auth_tokens_user", columnList = "user_id")
})
public class MobileAuthToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser owner;

    @Column(name = "access_token_hash", nullable = false, length = 64, unique = true)
    private String accessTokenHash;

    @Column(name = "refresh_token_hash", nullable = false, length = 64, unique = true)
    private String refreshTokenHash;

    @Column(name = "access_expires_at", nullable = false)
    private Instant accessExpiresAt;

    @Column(name = "refresh_expires_at", nullable = false)
    private Instant refreshExpiresAt;

    @Column(name = "device_name", length = 120)
    private String deviceName;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected MobileAuthToken() {}

    public MobileAuthToken(AppUser owner,
                           String accessTokenHash,
                           String refreshTokenHash,
                           Instant accessExpiresAt,
                           Instant refreshExpiresAt,
                           String deviceName) {
        this.owner = owner;
        this.accessTokenHash = accessTokenHash;
        this.refreshTokenHash = refreshTokenHash;
        this.accessExpiresAt = accessExpiresAt;
        this.refreshExpiresAt = refreshExpiresAt;
        this.deviceName = deviceName;
    }

    public Long getId() { return id; }
    public AppUser getOwner() { return owner; }
    public String getAccessTokenHash() { return accessTokenHash; }
    public void setAccessTokenHash(String accessTokenHash) { this.accessTokenHash = accessTokenHash; }
    public String getRefreshTokenHash() { return refreshTokenHash; }
    public void setRefreshTokenHash(String refreshTokenHash) { this.refreshTokenHash = refreshTokenHash; }
    public Instant getAccessExpiresAt() { return accessExpiresAt; }
    public void setAccessExpiresAt(Instant accessExpiresAt) { this.accessExpiresAt = accessExpiresAt; }
    public Instant getRefreshExpiresAt() { return refreshExpiresAt; }
    public void setRefreshExpiresAt(Instant refreshExpiresAt) { this.refreshExpiresAt = refreshExpiresAt; }
    public String getDeviceName() { return deviceName; }
    public void setDeviceName(String deviceName) { this.deviceName = deviceName; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastUsedAt() { return lastUsedAt; }
    public void setLastUsedAt(Instant lastUsedAt) { this.lastUsedAt = lastUsedAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public void revoke() { this.revokedAt = Instant.now(); }

    public boolean isRevoked() {
        return revokedAt != null;
    }
}
