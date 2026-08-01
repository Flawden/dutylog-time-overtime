package ru.daniil.shifts.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/** A planned or approved absence interval. It never becomes a shift row. */
@Entity
@Table(name = "absence_periods")
public class AbsencePeriod {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser owner;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "absence_type_id", nullable = false)
    private AbsenceType type;

    @Column(length = 120)
    private String title;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(nullable = false, length = 20)
    private String status = "PLANNED";

    @Column(nullable = false, length = 20)
    private String coverage = "FULL_DAY";

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    @Column(name = "charged_minutes", nullable = false)
    private int chargedMinutes;

    /** Explains how this factual absence is covered and later interpreted by payroll. */
    @Column(name = "compensation_policy", nullable = false, length = 30)
    private String compensationPolicy = "NONE";

    /** Minutes actually covered by a linked compensation source (currently overtime FIFO). */
    @Column(name = "compensated_minutes", nullable = false)
    private int compensatedMinutes;

    @Column(length = 1000)
    private String note;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected AbsencePeriod() {}

    public AbsencePeriod(AppUser owner) { this.owner = owner; }

    public Long getId() { return id; }
    public AppUser getOwner() { return owner; }
    public void setOwner(AppUser owner) { this.owner = owner; }
    public AbsenceType getType() { return type; }
    public void setType(AbsenceType type) { this.type = type; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCoverage() { return coverage; }
    public void setCoverage(String coverage) { this.coverage = coverage; }
    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }
    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }
    public int getChargedMinutes() { return Math.max(0, chargedMinutes); }
    public void setChargedMinutes(int chargedMinutes) { this.chargedMinutes = Math.max(0, chargedMinutes); }
    public String getCompensationPolicy() { return compensationPolicy == null ? "NONE" : compensationPolicy; }
    public void setCompensationPolicy(String compensationPolicy) {
        String normalized = compensationPolicy == null ? "NONE" : compensationPolicy.trim().toUpperCase();
        this.compensationPolicy = normalized.isBlank() ? "NONE" : normalized;
    }
    public int getCompensatedMinutes() { return Math.max(0, compensatedMinutes); }
    public void setCompensatedMinutes(int compensatedMinutes) { this.compensatedMinutes = Math.max(0, compensatedMinutes); }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    @PrePersist
    void create() {
        Instant now = Instant.now();
        createdAt = createdAt == null ? now : createdAt;
        updatedAt = now;
        normalize();
    }

    @PreUpdate
    void update() {
        updatedAt = Instant.now();
        normalize();
    }

    private void normalize() {
        if (status == null || status.isBlank()) status = "PLANNED";
        else status = status.trim().toUpperCase();
        if (coverage == null || coverage.isBlank()) coverage = "FULL_DAY";
        else coverage = coverage.trim().toUpperCase();
        if ("FULL_DAY".equals(coverage)) { startTime = null; endTime = null; }
        setCompensationPolicy(compensationPolicy);
    }
}
