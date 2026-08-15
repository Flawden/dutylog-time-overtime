package ru.daniil.shifts.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * One factual exception on top of a companion CalendarLayer template.
 * No row means that the date follows the template.
 */
@Entity
@Table(name = "calendar_layer_overrides",
        uniqueConstraints = @UniqueConstraint(columnNames = {"layer_id", "source_date"}))
public class CalendarLayerOverride {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "layer_id", nullable = false)
    private CalendarLayer layer;

    @Column(name = "source_date", nullable = false)
    private LocalDate sourceDate;

    @Column(nullable = false, length = 16)
    private String kind;

    @Column(length = 24)
    private String reason;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "shift_type_id")
    private ShiftType shiftType;

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected CalendarLayerOverride() {}

    public CalendarLayerOverride(CalendarLayer layer, LocalDate sourceDate) {
        this.layer = layer;
        this.sourceDate = sourceDate;
    }

    public Long getId() { return id; }
    public CalendarLayer getLayer() { return layer; }
    public LocalDate getSourceDate() { return sourceDate; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public ShiftType getShiftType() { return shiftType; }
    public void setShiftType(ShiftType shiftType) { this.shiftType = shiftType; }
    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }
    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        createdAt = createdAt == null ? now : createdAt;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() { updatedAt = Instant.now(); }
}
