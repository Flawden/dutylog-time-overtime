package ru.daniil.shifts.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

/**
 * One production-calendar rule for a date. BASE is reserved for official/imported
 * material; LOCAL_OVERRIDE has precedence and is the only layer edited by v27.45.0 UI.
 * Schedule/norm and payroll effects are deliberately independent.
 */
@Entity
@Table(name = "production_calendar_days",
        uniqueConstraints = @UniqueConstraint(name = "uq_production_calendar_day",
                columnNames = {"user_id", "calendar_date", "layer"}),
        indexes = @Index(name = "idx_production_calendar_days_owner_date",
                columnList = "user_id, calendar_date, layer"))
public class ProductionCalendarDay {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser owner;

    @Column(name = "calendar_date", nullable = false)
    private LocalDate date;

    @Column(nullable = false, length = 20)
    private String layer = "LOCAL_OVERRIDE";

    @Column(name = "day_kind", nullable = false, length = 32)
    private String dayKind = "NORMAL";

    @Column(name = "schedule_effect", nullable = false, length = 24)
    private String scheduleEffect = "NONE";

    @Column(name = "norm_minutes_override")
    private Integer normMinutesOverride;

    @Column(name = "payroll_effect", nullable = false, length = 24)
    private String payrollEffect = "NONE";

    @Column(length = 120)
    private String label;

    @Column(name = "source_type", nullable = false, length = 24)
    private String sourceType = "CUSTOM";

    @Column(name = "source_ref", length = 240)
    private String sourceRef;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected ProductionCalendarDay() {}

    public ProductionCalendarDay(AppUser owner, LocalDate date, String layer) {
        this.owner = owner;
        this.date = date;
        this.layer = layer;
    }

    public Long getId() { return id; }
    public AppUser getOwner() { return owner; }
    public LocalDate getDate() { return date; }
    public String getLayer() { return layer; }
    public String getDayKind() { return dayKind; }
    public String getScheduleEffect() { return scheduleEffect; }
    public Integer getNormMinutesOverride() { return normMinutesOverride; }
    public String getPayrollEffect() { return payrollEffect; }
    public String getLabel() { return label; }
    public String getSourceType() { return sourceType; }
    public String getSourceRef() { return sourceRef; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void update(String dayKind, String scheduleEffect, Integer normMinutesOverride,
                       String payrollEffect, String label, String sourceType, String sourceRef) {
        this.dayKind = dayKind;
        this.scheduleEffect = scheduleEffect;
        this.normMinutesOverride = normMinutesOverride;
        this.payrollEffect = payrollEffect;
        this.label = label;
        this.sourceType = sourceType;
        this.sourceRef = sourceRef;
        this.updatedAt = Instant.now();
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        createdAt = createdAt == null ? now : createdAt;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() { updatedAt = Instant.now(); }
}
