package ru.daniil.shifts.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Пользовательские настройки напоминаний.
 * Это не отправленные уведомления, а правила, по которым backend может
 * рассчитать ближайшие напоминания для Web/PWA, Android и будущего Telegram-бота.
 */
@Entity
@Table(name = "notification_settings", uniqueConstraints = {
        @UniqueConstraint(name = "uk_notification_settings_user", columnNames = "user_id")
})
public class NotificationSettings {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser owner;

    @Column(name = "browser_notifications_enabled", nullable = false)
    private boolean browserNotificationsEnabled = false;

    @Column(name = "shift_reminders_enabled", nullable = false)
    private boolean shiftRemindersEnabled = true;

    @Column(name = "shift_reminder_minutes_before", nullable = false)
    private int shiftReminderMinutesBefore = 60;

    @Column(name = "tomorrow_digest_enabled", nullable = false)
    private boolean tomorrowDigestEnabled = false;

    @Column(name = "tomorrow_digest_time", nullable = false)
    private LocalTime tomorrowDigestTime = LocalTime.of(19, 0);

    @Column(name = "task_reminders_enabled", nullable = false)
    private boolean taskRemindersEnabled = true;

    @Column(name = "task_reminder_time", nullable = false)
    private LocalTime taskReminderTime = LocalTime.of(9, 0);

    @Column(name = "important_day_reminders_enabled", nullable = false)
    private boolean importantDayRemindersEnabled = true;

    @Column(name = "important_day_days_before", nullable = false)
    private int importantDayDaysBefore = 1;

    @Column(name = "important_day_reminder_time", nullable = false)
    private LocalTime importantDayReminderTime = LocalTime.of(9, 0);

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    protected NotificationSettings() {}

    public NotificationSettings(AppUser owner) {
        this.owner = owner;
    }

    @PrePersist @PreUpdate
    public void touch() { updatedAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public AppUser getOwner() { return owner; }
    public boolean isBrowserNotificationsEnabled() { return browserNotificationsEnabled; }
    public void setBrowserNotificationsEnabled(boolean browserNotificationsEnabled) { this.browserNotificationsEnabled = browserNotificationsEnabled; }
    public boolean isShiftRemindersEnabled() { return shiftRemindersEnabled; }
    public void setShiftRemindersEnabled(boolean shiftRemindersEnabled) { this.shiftRemindersEnabled = shiftRemindersEnabled; }
    public int getShiftReminderMinutesBefore() { return shiftReminderMinutesBefore; }
    public void setShiftReminderMinutesBefore(int shiftReminderMinutesBefore) { this.shiftReminderMinutesBefore = Math.max(0, shiftReminderMinutesBefore); }
    public boolean isTomorrowDigestEnabled() { return tomorrowDigestEnabled; }
    public void setTomorrowDigestEnabled(boolean tomorrowDigestEnabled) { this.tomorrowDigestEnabled = tomorrowDigestEnabled; }
    public LocalTime getTomorrowDigestTime() { return tomorrowDigestTime; }
    public void setTomorrowDigestTime(LocalTime tomorrowDigestTime) { this.tomorrowDigestTime = tomorrowDigestTime; }
    public boolean isTaskRemindersEnabled() { return taskRemindersEnabled; }
    public void setTaskRemindersEnabled(boolean taskRemindersEnabled) { this.taskRemindersEnabled = taskRemindersEnabled; }
    public LocalTime getTaskReminderTime() { return taskReminderTime; }
    public void setTaskReminderTime(LocalTime taskReminderTime) { this.taskReminderTime = taskReminderTime; }
    public boolean isImportantDayRemindersEnabled() { return importantDayRemindersEnabled; }
    public void setImportantDayRemindersEnabled(boolean importantDayRemindersEnabled) { this.importantDayRemindersEnabled = importantDayRemindersEnabled; }
    public int getImportantDayDaysBefore() { return importantDayDaysBefore; }
    public void setImportantDayDaysBefore(int importantDayDaysBefore) { this.importantDayDaysBefore = Math.max(0, importantDayDaysBefore); }
    public LocalTime getImportantDayReminderTime() { return importantDayReminderTime; }
    public void setImportantDayReminderTime(LocalTime importantDayReminderTime) { this.importantDayReminderTime = importantDayReminderTime; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
