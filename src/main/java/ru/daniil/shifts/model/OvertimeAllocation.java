package ru.daniil.shifts.model;

import jakarta.persistence.*;
import java.time.Instant;

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

    @Column(name = "allocated_minutes")
    private Integer allocatedMinutes;

    /**
     * Start offset of this allocation inside the fungible overtime credit.
     *
     * This is the persisted form of AllocationPlanItem.alreadyConsumedMinutes.
     * It does not change FIFO ordering; it only materializes which exact credit
     * minute range this allocation consumed.
     */
    @Column(name = "credit_offset_start_minutes", nullable = false)
    private Integer creditOffsetStartMinutes = 0;

    @Column(name = "start_at_instant")
    private Instant startAtInstant;

    @Column(name = "end_at_instant")
    private Instant endAtInstant;

    @Column(name = "source_timezone", length = 80)
    private String sourceTimezone;

    @Column(name = "reconstructed", nullable = false)
    private Boolean reconstructed = false;

    protected OvertimeAllocation() {}

    public OvertimeAllocation(OvertimeCredit credit, OvertimeUsage usage, int minutes) {
        this.credit = credit;
        this.usage = usage;
        setAllocatedMinutes(minutes);
    }

    /** Source-compatible constructor for pre-v27.9 callers. */
    public OvertimeAllocation(OvertimeCredit credit, OvertimeUsage usage, double hours) {
        this(credit, usage, (int) Math.round(Math.max(0.0, hours) * 60.0));
    }

    public Long getId() { return id; }
    public OvertimeCredit getCredit() { return credit; }
    public OvertimeUsage getUsage() { return usage; }
    public int getAllocatedMinutes() {
        if (allocatedMinutes != null && allocatedMinutes > 0) return allocatedMinutes;
        return (int) Math.max(0L, Math.round((hours == null ? 0.0 : hours) * 60.0));
    }
    public void setAllocatedMinutes(int allocatedMinutes) {
        this.allocatedMinutes = Math.max(0, allocatedMinutes);
        this.hours = this.allocatedMinutes / 60.0;
    }
    public double getHours() { return getAllocatedMinutes() / 60.0; }
    public void setHours(double hours) { setAllocatedMinutes((int) Math.round(Math.max(0.0, hours) * 60.0)); }

    public int getCreditOffsetStartMinutes() {
        return creditOffsetStartMinutes == null
                ? 0
                : Math.max(0, creditOffsetStartMinutes);
    }

    public void setCreditOffsetStartMinutes(
            int creditOffsetStartMinutes
    ) {
        this.creditOffsetStartMinutes =
                Math.max(
                        0,
                        creditOffsetStartMinutes
                );
    }
    public Instant getStartAtInstant() { return startAtInstant; }
    public void setStartAtInstant(Instant startAtInstant) { this.startAtInstant = startAtInstant; }
    public Instant getEndAtInstant() { return endAtInstant; }
    public void setEndAtInstant(Instant endAtInstant) { this.endAtInstant = endAtInstant; }
    public String getSourceTimezone() { return sourceTimezone; }
    public void setSourceTimezone(String sourceTimezone) { this.sourceTimezone = sourceTimezone; }
    public boolean isReconstructed() { return reconstructed != null && reconstructed; }
    public void setReconstructed(boolean reconstructed) { this.reconstructed = reconstructed; }
}
