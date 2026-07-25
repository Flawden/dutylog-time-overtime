package ru.daniil.shifts.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Instant;

/**
 * Начисление переработки: конкретный день, диапазон времени, часы и причина.
 * Эти часы живут до тех пор, пока пользователь не спишет их отгулом.
 */
@Entity
@Table(name = "overtime_credits", indexes = {
        @Index(name = "idx_overtime_credits_user_date", columnList = "user_id, work_date")
})
public class OvertimeCredit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser owner;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Column(name = "time_range", length = 50)
    private String timeRange;

    /**
     * Необязательные поля для автоподсчёта: пользователь вводит интервал работы,
     * обед и плановые часы, а система считает чистую переработку.
     */
    @Column(name = "start_at")
    private LocalDateTime startAt;

    @Column(name = "end_at")
    private LocalDateTime endAt;

    /**
     * Absolute identity for credits created after Time Foundation. Historical
     * rows intentionally keep these fields null because their original zone was
     * never persisted.
     */
    @Column(name = "start_at_instant")
    private Instant startAtInstant;

    @Column(name = "end_at_instant")
    private Instant endAtInstant;

    @Column(name = "source_timezone", length = 80)
    private String sourceTimezone;

    @Column(name = "break_minutes", nullable = false)
    private Integer breakMinutes = 0;

    @Column(name = "planned_hours", nullable = false)
    private Double plannedHours = 0.0;

    @Column(name = "calculated", nullable = false)
    private Boolean calculated = false;

    @Column(nullable = false)
    private Double hours;

    @Column(columnDefinition = "text")
    private String reason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected OvertimeCredit() {}

    public OvertimeCredit(AppUser owner, LocalDate workDate, String timeRange, double hours, String reason) {
        this.owner = owner;
        this.workDate = workDate;
        this.timeRange = timeRange;
        this.hours = hours;
        this.reason = reason;
    }

    public OvertimeCredit(AppUser owner, LocalDate workDate, String timeRange, double hours, String reason,
                          LocalDateTime startAt, LocalDateTime endAt, int breakMinutes, double plannedHours, boolean calculated) {
        this(owner, workDate, timeRange, hours, reason);
        this.startAt = startAt;
        this.endAt = endAt;
        this.breakMinutes = Math.max(0, breakMinutes);
        this.plannedHours = Math.max(0.0, plannedHours);
        this.calculated = calculated;
    }

    public Long getId() { return id; }
    public AppUser getOwner() { return owner; }
    public LocalDate getWorkDate() { return workDate; }
    public void setWorkDate(LocalDate workDate) { this.workDate = workDate; }
    public String getTimeRange() { return timeRange; }
    public void setTimeRange(String timeRange) { this.timeRange = timeRange; }
    public LocalDateTime getStartAt() { return startAt; }
    public void setStartAt(LocalDateTime startAt) { this.startAt = startAt; }
    public LocalDateTime getEndAt() { return endAt; }
    public void setEndAt(LocalDateTime endAt) { this.endAt = endAt; }
    public Instant getStartAtInstant() { return startAtInstant; }
    public void setStartAtInstant(Instant startAtInstant) { this.startAtInstant = startAtInstant; }
    public Instant getEndAtInstant() { return endAtInstant; }
    public void setEndAtInstant(Instant endAtInstant) { this.endAtInstant = endAtInstant; }
    public String getSourceTimezone() { return sourceTimezone; }
    public void setSourceTimezone(String sourceTimezone) { this.sourceTimezone = sourceTimezone; }
    public int getBreakMinutes() { return breakMinutes == null ? 0 : breakMinutes; }
    public void setBreakMinutes(int breakMinutes) { this.breakMinutes = Math.max(0, breakMinutes); }
    public double getPlannedHours() { return plannedHours == null ? 0.0 : plannedHours; }
    public void setPlannedHours(double plannedHours) { this.plannedHours = Math.max(0.0, plannedHours); }
    public boolean isCalculated() { return calculated != null && calculated; }
    public void setCalculated(boolean calculated) { this.calculated = calculated; }
    public double getHours() { return hours == null ? 0.0 : hours; }
    public void setHours(double hours) { this.hours = Math.max(0.0, hours); }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
