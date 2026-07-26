package ru.daniil.shifts.model;

import jakarta.persistence.*;

import java.time.LocalTime;

/**
 * Пользовательский быстрый сценарий заполнения переработки.
 *
 * Идея: сценарий не начисляет часы сам, а только заполняет поля
 * начала/конца/обеда/плана/причины в форме переработки.
 */
@Entity
@Table(name = "quick_scenarios")
public class QuickScenario {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser owner;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(length = 40)
    private String groupLabel;

    @Column(length = 300)
    private String description;

    /** SHIFT_START или SHIFT_END. */
    @Column(nullable = false, length = 30)
    private String startMode = "SHIFT_END";

    /** SHIFT_END, ADD_MINUTES или FIXED_TIME. */
    @Column(nullable = false, length = 30)
    private String endMode = "ADD_MINUTES";

    @Column(name = "end_offset_minutes", nullable = false)
    private int endOffsetMinutes = 120;

    @Column(name = "end_fixed_time")
    private LocalTime endFixedTime;

    @Column(name = "end_next_day", nullable = false)
    private boolean endNextDay = false;

    /** Relative calendar-day offset for FIXED_TIME. Supports previous/same/next/following day. */
    @Column(name = "end_day_offset", nullable = false)
    private int endDayOffset = 0;

    /** ZERO, SHIFT или CUSTOM. */
    @Column(nullable = false, length = 30)
    private String breakMode = "ZERO";

    @Column(name = "custom_break_minutes", nullable = false)
    private int customBreakMinutes = 0;

    /** ZERO, SHIFT или CUSTOM. */
    @Column(nullable = false, length = 30)
    private String plannedMode = "ZERO";

    @Column(name = "custom_planned_hours", nullable = false)
    private double customPlannedHours = 0.0;

    @Column(length = 300)
    private String reasonTemplate;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    protected QuickScenario() {}

    public QuickScenario(AppUser owner) {
        this.owner = owner;
    }

    public Long getId() { return id; }
    public AppUser getOwner() { return owner; }
    public void setOwner(AppUser owner) { this.owner = owner; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getGroupLabel() { return groupLabel; }
    public void setGroupLabel(String groupLabel) { this.groupLabel = groupLabel; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getStartMode() { return startMode; }
    public void setStartMode(String startMode) { this.startMode = startMode; }
    public String getEndMode() { return endMode; }
    public void setEndMode(String endMode) { this.endMode = endMode; }
    public int getEndOffsetMinutes() { return endOffsetMinutes; }
    public void setEndOffsetMinutes(int endOffsetMinutes) { this.endOffsetMinutes = endOffsetMinutes; }
    public LocalTime getEndFixedTime() { return endFixedTime; }
    public void setEndFixedTime(LocalTime endFixedTime) { this.endFixedTime = endFixedTime; }
    public boolean isEndNextDay() { return endDayOffset > 0 || endNextDay; }
    public void setEndNextDay(boolean endNextDay) {
        this.endNextDay = endNextDay;
        this.endDayOffset = endNextDay ? 1 : 0;
    }
    public int getEndDayOffset() { return endDayOffset; }
    public void setEndDayOffset(int endDayOffset) {
        this.endDayOffset = Math.max(-2, Math.min(2, endDayOffset));
        this.endNextDay = this.endDayOffset > 0;
    }
    public String getBreakMode() { return breakMode; }
    public void setBreakMode(String breakMode) { this.breakMode = breakMode; }
    public int getCustomBreakMinutes() { return customBreakMinutes; }
    public void setCustomBreakMinutes(int customBreakMinutes) { this.customBreakMinutes = Math.max(0, customBreakMinutes); }
    public String getPlannedMode() { return plannedMode; }
    public void setPlannedMode(String plannedMode) { this.plannedMode = plannedMode; }
    public double getCustomPlannedHours() { return customPlannedHours; }
    public void setCustomPlannedHours(double customPlannedHours) { this.customPlannedHours = Math.max(0, customPlannedHours); }
    public String getReasonTemplate() { return reasonTemplate; }
    public void setReasonTemplate(String reasonTemplate) { this.reasonTemplate = reasonTemplate; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
}
