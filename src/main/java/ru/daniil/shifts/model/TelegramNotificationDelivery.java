package ru.daniil.shifts.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * Журнал отправленных Telegram-напоминаний.
 * Нужен, чтобы scheduler не слал одно и то же сообщение повторно при каждом скане.
 */
@Entity
@Table(name = "telegram_notification_deliveries",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "reminder_id", "remind_at_instant"}),
        indexes = {
                @Index(name = "idx_tg_notifications_user", columnList = "user_id"),
                @Index(name = "idx_tg_notifications_remind_at", columnList = "remind_at"),
                @Index(name = "idx_tg_notifications_remind_at_instant", columnList = "remind_at_instant"),
                @Index(name = "idx_tg_notifications_link", columnList = "telegram_link_id")
        })
public class TelegramNotificationDelivery {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser owner;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "telegram_link_id", nullable = false)
    private TelegramLink telegramLink;

    @Column(name = "reminder_id", nullable = false, length = 180)
    private String reminderId;

    @Column(name = "reminder_type", nullable = false, length = 40)
    private String reminderType;

    @Column(name = "remind_at", nullable = false)
    private LocalDateTime remindAt;

    @Column(name = "remind_at_instant")
    private Instant remindAtInstant;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt = Instant.now();

    protected TelegramNotificationDelivery() {}

    public TelegramNotificationDelivery(AppUser owner,
                                        TelegramLink telegramLink,
                                        String reminderId,
                                        String reminderType,
                                        LocalDateTime remindAt,
                                        Instant remindAtInstant) {
        this.owner = owner;
        this.telegramLink = telegramLink;
        this.reminderId = reminderId;
        this.reminderType = reminderType;
        this.remindAt = remindAt;
        this.remindAtInstant = remindAtInstant;
        this.sentAt = Instant.now();
    }

    public Long getId() { return id; }
    public AppUser getOwner() { return owner; }
    public TelegramLink getTelegramLink() { return telegramLink; }
    public String getReminderId() { return reminderId; }
    public String getReminderType() { return reminderType; }
    public LocalDateTime getRemindAt() { return remindAt; }
    public Instant getRemindAtInstant() { return remindAtInstant; }
    public Instant getSentAt() { return sentAt; }
}
