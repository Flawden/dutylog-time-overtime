package ru.daniil.shifts.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

/** Append-only manual addition or deduction attached to one payroll month. */
@Entity
@Table(name = "payroll_adjustments", indexes =
        @Index(name = "idx_payroll_adjustments_owner_month", columnList = "user_id, period_month, id"))
public class PayrollAdjustment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser owner;

    @Column(name = "period_month", nullable = false)
    private LocalDate periodMonth;

    @Column(name = "adjustment_type", nullable = false, length = 20)
    private String adjustmentType;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(length = 500)
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected PayrollAdjustment() {}

    public PayrollAdjustment(AppUser owner, LocalDate periodMonth, String adjustmentType,
                             long amountMinor, String title, String note) {
        this.owner = owner;
        this.periodMonth = periodMonth.withDayOfMonth(1);
        this.adjustmentType = adjustmentType;
        this.amountMinor = amountMinor;
        this.title = title;
        this.note = note;
    }

    public Long getId() { return id; }
    public AppUser getOwner() { return owner; }
    public LocalDate getPeriodMonth() { return periodMonth; }
    public String getAdjustmentType() { return adjustmentType; }
    public long getAmountMinor() { return amountMinor; }
    public String getTitle() { return title; }
    public String getNote() { return note; }
    public Instant getCreatedAt() { return createdAt; }
}
