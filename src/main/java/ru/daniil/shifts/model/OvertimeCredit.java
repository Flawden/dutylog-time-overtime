package ru.daniil.shifts.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

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

    public Long getId() { return id; }
    public AppUser getOwner() { return owner; }
    public LocalDate getWorkDate() { return workDate; }
    public void setWorkDate(LocalDate workDate) { this.workDate = workDate; }
    public String getTimeRange() { return timeRange; }
    public void setTimeRange(String timeRange) { this.timeRange = timeRange; }
    public double getHours() { return hours == null ? 0.0 : hours; }
    public void setHours(double hours) { this.hours = Math.max(0.0, hours); }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
