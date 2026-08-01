package ru.daniil.shifts.model;

import jakarta.persistence.*;

import java.time.Instant;

/** User-owned rules for vacation allowance and work-year counting. */
@Entity
@Table(name = "vacation_settings", uniqueConstraints = @UniqueConstraint(columnNames = "user_id"))
public class VacationSettings {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser owner;

    @Column(name = "annual_allowance_days", nullable = false)
    private int annualAllowanceDays = 28;

    @Column(name = "carryover_days", nullable = false)
    private int carryoverDays;

    /** CALENDAR_DAYS counts every civil day; WEEKDAYS counts Monday-Friday only. */
    @Column(name = "count_mode", nullable = false, length = 20)
    private String countMode = "CALENDAR_DAYS";

    @Column(name = "work_year_start_month", nullable = false)
    private int workYearStartMonth = 1;

    /** Limited to 1-28 so every configured work-year boundary exists. */
    @Column(name = "work_year_start_day", nullable = false)
    private int workYearStartDay = 1;

    @Column(name = "time_off_balance_minutes", nullable = false)
    private int timeOffBalanceMinutes;

    @Column(name = "default_time_off_day_minutes", nullable = false)
    private int defaultTimeOffDayMinutes = 480;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected VacationSettings() {}

    public VacationSettings(AppUser owner) {
        this.owner = owner;
    }

    public Long getId() { return id; }
    public AppUser getOwner() { return owner; }
    public void setOwner(AppUser owner) { this.owner = owner; }
    public int getAnnualAllowanceDays() { return annualAllowanceDays; }
    public void setAnnualAllowanceDays(int annualAllowanceDays) { this.annualAllowanceDays = annualAllowanceDays; }
    public int getCarryoverDays() { return carryoverDays; }
    public void setCarryoverDays(int carryoverDays) { this.carryoverDays = carryoverDays; }
    public String getCountMode() { return countMode; }
    public void setCountMode(String countMode) { this.countMode = countMode; }
    public int getWorkYearStartMonth() { return workYearStartMonth; }
    public void setWorkYearStartMonth(int workYearStartMonth) { this.workYearStartMonth = workYearStartMonth; }
    public int getWorkYearStartDay() { return workYearStartDay; }
    public void setWorkYearStartDay(int workYearStartDay) { this.workYearStartDay = workYearStartDay; }
    public int getTimeOffBalanceMinutes() { return Math.max(0, timeOffBalanceMinutes); }
    public void setTimeOffBalanceMinutes(int value) { this.timeOffBalanceMinutes = Math.max(0, value); }
    public int getDefaultTimeOffDayMinutes() { return Math.max(15, defaultTimeOffDayMinutes); }
    public void setDefaultTimeOffDayMinutes(int value) { this.defaultTimeOffDayMinutes = Math.max(15, value); }
    public Instant getUpdatedAt() { return updatedAt; }

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
        if (countMode == null || countMode.isBlank()) countMode = "CALENDAR_DAYS";
        else countMode = countMode.trim().toUpperCase();
    }
}
