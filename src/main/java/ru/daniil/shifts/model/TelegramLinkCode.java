package ru.daniil.shifts.model;

import jakarta.persistence.*;

import java.time.Instant;

/** Одноразовый код для привязки Telegram: /start DL-123456. */
@Entity
@Table(name = "telegram_link_codes",
        uniqueConstraints = @UniqueConstraint(columnNames = "code"),
        indexes = {
                @Index(name = "idx_telegram_link_codes_user", columnList = "user_id"),
                @Index(name = "idx_telegram_link_codes_expires", columnList = "expires_at")
        })
public class TelegramLinkCode {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser owner;

    @Column(nullable = false, length = 20)
    private String code;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    protected TelegramLinkCode() {}

    public TelegramLinkCode(AppUser owner, String code, Instant expiresAt) {
        this.owner = owner;
        this.code = code;
        this.expiresAt = expiresAt;
    }

    public Long getId() { return id; }
    public AppUser getOwner() { return owner; }
    public String getCode() { return code; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getUsedAt() { return usedAt; }
    public void setUsedAt(Instant usedAt) { this.usedAt = usedAt; }
    public boolean isExpired() { return expiresAt == null || expiresAt.isBefore(Instant.now()); }
}
