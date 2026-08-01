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
        @Index(name = "idx_overtime_usages_user_date", columnList = "user_id, usage_date"),
        @Index(name = "idx_overtime_usages_source", columnList = "user_id, source_kind, source_absence_id")
}, uniqueConstraints = @UniqueConstraint(name = "uq_overtime_usage_source_absence", columnNames = "source_absence_id"))
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

    /** MANUAL for the classic ledger editor, ABSENCE for an automatically linked time-off. */
    @Column(name = "source_kind", nullable = false, length = 30)
    private String sourceKind = "MANUAL";

    /** Stable source identity without an entity cycle back to Vacation Planner. */
    @Column(name = "source_absence_id")
    private Long sourceAbsenceId;

    /** Future approved absence may reserve hours before they are finally posted. */
    @Column(name = "posting_state", nullable = false, length = 20)
    private String postingState = "POSTED";

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
    public String getSourceKind() { return sourceKind == null ? "MANUAL" : sourceKind; }
    public void setSourceKind(String sourceKind) {
        String normalized = sourceKind == null ? "MANUAL" : sourceKind.trim().toUpperCase();
        this.sourceKind = normalized.isBlank() ? "MANUAL" : normalized;
    }
    public Long getSourceAbsenceId() { return sourceAbsenceId; }
    public void setSourceAbsenceId(Long sourceAbsenceId) { this.sourceAbsenceId = sourceAbsenceId; }
    public String getPostingState() { return postingState == null ? "POSTED" : postingState; }
    public void setPostingState(String postingState) {
        String normalized = postingState == null ? "POSTED" : postingState.trim().toUpperCase();
        if (!"RESERVED".equals(normalized) && !"POSTED".equals(normalized)) normalized = "POSTED";
        this.postingState = normalized;
    }
    public boolean isReserved() { return "RESERVED".equals(getPostingState()); }
    public boolean isAbsenceLinked() { return "ABSENCE".equals(getSourceKind()) && sourceAbsenceId != null; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
