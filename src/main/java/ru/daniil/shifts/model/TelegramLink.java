package ru.daniil.shifts.model;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Привязка аккаунта DutyLog к Telegram-чату.
 * Один пользователь — один активный Telegram-чат. Chat id уникален, чтобы
 * один и тот же Telegram не оказался привязан к двум аккаунтам сразу.
 */
@Entity
@Table(name = "telegram_links",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = "user_id"),
                @UniqueConstraint(columnNames = "telegram_chat_id")
        })
public class TelegramLink {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser owner;

    @Column(name = "telegram_chat_id", nullable = false)
    private Long telegramChatId;

    @Column(name = "telegram_user_id")
    private Long telegramUserId;

    @Column(length = 64)
    private String username;

    @Column(name = "first_name", length = 80)
    private String firstName;

    @Column(name = "last_name", length = 80)
    private String lastName;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "linked_at", nullable = false)
    private Instant linkedAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected TelegramLink() {}

    public TelegramLink(AppUser owner, Long telegramChatId) {
        this.owner = owner;
        this.telegramChatId = telegramChatId;
        this.linkedAt = Instant.now();
        this.updatedAt = this.linkedAt;
    }

    public Long getId() { return id; }
    public AppUser getOwner() { return owner; }
    public Long getTelegramChatId() { return telegramChatId; }
    public void setTelegramChatId(Long telegramChatId) { this.telegramChatId = telegramChatId; touch(); }
    public Long getTelegramUserId() { return telegramUserId; }
    public void setTelegramUserId(Long telegramUserId) { this.telegramUserId = telegramUserId; touch(); }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; touch(); }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; touch(); }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; touch(); }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; touch(); }
    public Instant getLinkedAt() { return linkedAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    private void touch() { this.updatedAt = Instant.now(); }
}
