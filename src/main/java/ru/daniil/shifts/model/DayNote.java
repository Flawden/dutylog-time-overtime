package ru.daniil.shifts.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

/** Independent Markdown note attached to a calendar date. */
@Entity
@Table(name = "day_notes", indexes = {
        @Index(name = "idx_day_notes_user_date", columnList = "user_id, note_date"),
        @Index(name = "idx_day_notes_user_date_order", columnList = "user_id, note_date, pinned, sort_order")
})
public class DayNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser owner;

    @Column(name = "note_date", nullable = false)
    private LocalDate date;

    @Column(length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String content = "";

    @Column(nullable = false)
    private boolean pinned = false;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @Version
    @Column(name = "row_version", nullable = false)
    private Long rowVersion = 0L;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected DayNote() {}

    public DayNote(AppUser owner, LocalDate date, String content) {
        this.owner = owner;
        this.date = date;
        this.content = content == null ? "" : content;
    }

    public Long getId() { return id; }
    public AppUser getOwner() { return owner; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content == null ? "" : content; }
    public boolean isPinned() { return pinned; }
    public void setPinned(boolean pinned) { this.pinned = pinned; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = Math.max(0, sortOrder); }
    public long getVersion() { return rowVersion == null ? 0L : rowVersion + 1L; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
