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

    /** Integer-minute authority for deterministic FIFO. */
    @Column(name = "requested_minutes")
    private Integer requestedMinutes;

    @Column(columnDefinition = "text")
    private String reason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected OvertimeUsage() {}

    public OvertimeUsage(AppUser owner, LocalDate usageDate, double hours, String reason) {
        this.owner = owner;
        this.usageDate = usageDate;
        setHours(hours);
        this.reason = reason;
    }

    public Long getId() { return id; }
    public AppUser getOwner() { return owner; }
    public LocalDate getUsageDate() { return usageDate; }
    public void setUsageDate(LocalDate usageDate) { this.usageDate = usageDate; }
    public int getRequestedMinutes() {
        if (requestedMinutes != null && requestedMinutes > 0) return requestedMinutes;
        return (int) Math.max(0L, Math.round((hours == null ? 0.0 : hours) * 60.0));
    }
    public void setRequestedMinutes(int requestedMinutes) {
        this.requestedMinutes = Math.max(0, requestedMinutes);
        this.hours = this.requestedMinutes / 60.0;
    }
    public double getHours() { return getRequestedMinutes() / 60.0; }
    public void setHours(double hours) { setRequestedMinutes((int) Math.round(Math.max(0.0, hours) * 60.0)); }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
