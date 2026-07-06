package ru.daniil.shifts.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Списание переработки в отгул. Само списание распределяется по начислениям
 * через OvertimeAllocation по принципу FIFO: сначала самые старые остатки.
 */
@Entity
@Table(name = "overtime_usages", indexes = {
        @Index(name = "idx_overtime_usages_user_date", columnList = "user_id, usage_date")
})
public class OvertimeUsage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser owner;

    @Column(name = "usage_date", nullable = false)
    private LocalDate usageDate;

    @Column(nullable = false)
    private Double hours;

    @Column(columnDefinition = "text")
    private String reason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected OvertimeUsage() {}

    public OvertimeUsage(AppUser owner, LocalDate usageDate, double hours, String reason) {
        this.owner = owner;
        this.usageDate = usageDate;
        this.hours = hours;
        this.reason = reason;
    }

    public Long getId() { return id; }
    public AppUser getOwner() { return owner; }
    public LocalDate getUsageDate() { return usageDate; }
    public void setUsageDate(LocalDate usageDate) { this.usageDate = usageDate; }
    public double getHours() { return hours == null ? 0.0 : hours; }
    public void setHours(double hours) { this.hours = Math.max(0.0, hours); }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
