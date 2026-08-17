package ru.daniil.shifts.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/** Explicit factual work interval. End at or before start means next-day completion. */
@Entity
@Table(name = "actual_work_intervals", indexes =
        @Index(name = "idx_actual_work_intervals_owner_date", columnList = "user_id, work_date, start_time, id"))
public class ActualWorkInterval {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser owner;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "worked_minutes", nullable = false)
    private int workedMinutes;

    @Column(name = "break_minutes", nullable = false)
    private int breakMinutes;

    @Column(length = 500)
    private String note;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected ActualWorkInterval() {}
    public ActualWorkInterval(AppUser owner) { this.owner = owner; }

    public Long getId() { return id; }
    public AppUser getOwner() { return owner; }
    public LocalDate getWorkDate() { return workDate; }
    public void setWorkDate(LocalDate workDate) { this.workDate = workDate; }
    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }
    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public int getWorkedMinutes() { return workedMinutes; }
    public void setWorkedMinutes(int workedMinutes) { this.workedMinutes = workedMinutes; }
    public int getBreakMinutes() { return Math.max(0, breakMinutes); }
    public void setBreakMinutes(int breakMinutes) { this.breakMinutes = Math.max(0, breakMinutes); }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    @PrePersist @PreUpdate
    void touch() { updatedAt = Instant.now(); }
}
