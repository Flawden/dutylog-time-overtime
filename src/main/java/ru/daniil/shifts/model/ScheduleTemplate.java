package ru.daniil.shifts.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * User-owned repeating shift cycle.
 *
 * <p>Templates are intentionally separate from dated calendar rows: a cycle can be
 * previewed, reused and safely reapplied without becoming a hidden source of truth
 * for days that the user later edits manually.</p>
 */
@Entity
@Table(name = "schedule_templates",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "name"}))
public class ScheduleTemplate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser owner;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 400)
    private String description;

    /** WEEKDAY aligns step 0 with Monday; CYCLE_START aligns it with anchorDate. */
    @Column(name = "alignment_mode", nullable = false, length = 20)
    private String alignmentMode = "CYCLE_START";

    @Column(name = "system_preset", nullable = false)
    private boolean systemPreset;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC, id ASC")
    private List<ScheduleTemplateStep> steps = new ArrayList<>();

    protected ScheduleTemplate() {}

    public ScheduleTemplate(AppUser owner) {
        this.owner = owner;
    }

    public Long getId() { return id; }
    public AppUser getOwner() { return owner; }
    public void setOwner(AppUser owner) { this.owner = owner; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getAlignmentMode() { return alignmentMode; }
    public void setAlignmentMode(String alignmentMode) { this.alignmentMode = alignmentMode; }
    public boolean isSystemPreset() { return systemPreset; }
    public void setSystemPreset(boolean systemPreset) { this.systemPreset = systemPreset; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<ScheduleTemplateStep> getSteps() { return steps; }

    public void replaceSteps(List<ShiftType> shiftTypes) {
        steps.clear();
        for (int i = 0; i < shiftTypes.size(); i++) {
            steps.add(new ScheduleTemplateStep(this, i, shiftTypes.get(i)));
        }
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        createdAt = createdAt == null ? now : createdAt;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
