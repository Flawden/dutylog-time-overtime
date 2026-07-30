package ru.daniil.shifts.model;

import jakarta.persistence.*;

import java.time.Instant;

/** User-owned absence category. Vacation is only one possible absence type. */
@Entity
@Table(name = "absence_types",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"user_id", "name"}),
                @UniqueConstraint(columnNames = {"user_id", "system_code"})
        })
public class AbsenceType {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser owner;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false, length = 7)
    private String color = "#4FA3A5";

    @Column(name = "counts_against_allowance", nullable = false)
    private boolean countsAgainstAllowance;

    @Column(name = "system_preset", nullable = false)
    private boolean systemPreset;

    @Column(name = "system_code", length = 30)
    private String systemCode;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected AbsenceType() {}

    public AbsenceType(AppUser owner) { this.owner = owner; }

    public Long getId() { return id; }
    public AppUser getOwner() { return owner; }
    public void setOwner(AppUser owner) { this.owner = owner; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public boolean isCountsAgainstAllowance() { return countsAgainstAllowance; }
    public void setCountsAgainstAllowance(boolean countsAgainstAllowance) { this.countsAgainstAllowance = countsAgainstAllowance; }
    public boolean isSystemPreset() { return systemPreset; }
    public void setSystemPreset(boolean systemPreset) { this.systemPreset = systemPreset; }
    public String getSystemCode() { return systemCode; }
    public void setSystemCode(String systemCode) { this.systemCode = systemCode; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    @PrePersist
    void create() {
        Instant now = Instant.now();
        createdAt = createdAt == null ? now : createdAt;
        updatedAt = now;
        normalize();
    }

    @PreUpdate
    void update() {
        updatedAt = Instant.now();
        normalize();
    }

    private void normalize() {
        if (systemCode != null) {
            systemCode = systemCode.trim().toUpperCase();
            if (systemCode.isBlank()) systemCode = null;
        }
    }
}
