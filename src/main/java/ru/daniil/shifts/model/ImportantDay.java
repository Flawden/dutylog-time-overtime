package ru.daniil.shifts.model;

import jakarta.persistence.*;
import java.time.LocalDate;

/**
 * Важный день: день рождения, годовщина, техосмотр, оплата, любой повторяющийся день.
 * Хранится один раз, а сервис календаря разворачивает его в occurrence'ы внутри диапазона.
 */
@Entity
@Table(name = "important_days", indexes = {
        @Index(name = "idx_important_days_user_date", columnList = "user_id, event_date")
})
public class ImportantDay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser owner;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(name = "event_date", nullable = false)
    private LocalDate date;

    @Enumerated(EnumType.STRING)
    @Column(name = "repeat_mode", nullable = false, length = 20)
    private RepeatMode repeatMode = RepeatMode.NONE;

    @Column(nullable = false, length = 7)
    private String color = "#F5B841";

    protected ImportantDay() {}

    public ImportantDay(AppUser owner, String title, LocalDate date, RepeatMode repeatMode, String color) {
        this.owner = owner;
        this.title = title;
        this.date = date;
        this.repeatMode = repeatMode == null ? RepeatMode.NONE : repeatMode;
        this.color = color;
    }

    public Long getId() { return id; }
    public AppUser getOwner() { return owner; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public RepeatMode getRepeatMode() { return repeatMode; }
    public void setRepeatMode(RepeatMode repeatMode) { this.repeatMode = repeatMode == null ? RepeatMode.NONE : repeatMode; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
}
