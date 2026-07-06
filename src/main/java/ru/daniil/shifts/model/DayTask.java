package ru.daniil.shifts.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Отдельная задача на конкретный день.
 * Это не Markdown-строка в заметке, а нормальная сущность: её можно чекать,
 * показывать индикатором на календаре, синхронизировать с Android и Telegram.
 */
@Entity
@Table(name = "day_tasks", indexes = {
        @Index(name = "idx_day_tasks_user_date", columnList = "user_id, task_date")
})
public class DayTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser owner;

    @Column(name = "task_date", nullable = false)
    private LocalDate date;

    @Column(nullable = false, length = 500)
    private String text;

    @Column(nullable = false)
    private boolean done = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected DayTask() {}

    public DayTask(AppUser owner, LocalDate date, String text) {
        this.owner = owner;
        this.date = date;
        this.text = text;
    }

    public Long getId() { return id; }
    public AppUser getOwner() { return owner; }
    public LocalDate getDate() { return date; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public boolean isDone() { return done; }
    public void setDone(boolean done) { this.done = done; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
