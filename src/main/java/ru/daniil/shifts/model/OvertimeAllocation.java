package ru.daniil.shifts.model;

import jakarta.persistence.*;

/**
 * Связка списания с конкретным начислением переработки.
 * Например: списание 4 ч может забрать 2 ч из 20 числа и 2 ч из 21 числа.
 */
@Entity
@Table(name = "overtime_allocations", indexes = {
        @Index(name = "idx_overtime_allocations_credit", columnList = "credit_id"),
        @Index(name = "idx_overtime_allocations_usage", columnList = "usage_id")
})
public class OvertimeAllocation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "credit_id", nullable = false)
    private OvertimeCredit credit;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usage_id", nullable = false)
    private OvertimeUsage usage;

    @Column(nullable = false)
    private Double hours;

    protected OvertimeAllocation() {}

    public OvertimeAllocation(OvertimeCredit credit, OvertimeUsage usage, double hours) {
        this.credit = credit;
        this.usage = usage;
        this.hours = hours;
    }

    public Long getId() { return id; }
    public OvertimeCredit getCredit() { return credit; }
    public OvertimeUsage getUsage() { return usage; }
    public double getHours() { return hours == null ? 0.0 : hours; }
    public void setHours(double hours) { this.hours = Math.max(0.0, hours); }
}
