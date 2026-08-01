package ru.daniil.shifts.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

/** One user-owned calendar month that may be closed before payroll calculation. */
@Entity
@Table(name = "time_accounting_periods", uniqueConstraints =
        @UniqueConstraint(name = "uq_time_accounting_period", columnNames = {"user_id", "period_month"}))
public class TimeAccountingPeriod {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser owner;

    @Column(name = "period_month", nullable = false)
    private LocalDate periodMonth;

    @Column(nullable = false, length = 20)
    private String status = "OPEN";

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected TimeAccountingPeriod() {}

    public TimeAccountingPeriod(AppUser owner, LocalDate periodMonth) {
        this.owner = owner;
        this.periodMonth = periodMonth.withDayOfMonth(1);
    }

    public Long getId() { return id; }
    public AppUser getOwner() { return owner; }
    public LocalDate getPeriodMonth() { return periodMonth; }
    public String getStatus() { return status; }
    public Instant getClosedAt() { return closedAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void close() { status = "CLOSED"; closedAt = Instant.now(); updatedAt = closedAt; }
    public void reopen() { status = "OPEN"; closedAt = null; updatedAt = Instant.now(); }
    public boolean isClosed() { return "CLOSED".equals(status); }
}
