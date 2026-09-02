package ru.daniil.shifts.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Explicit factual work interval. End at or before start means next-day completion. */
@Entity
@Table(name = "actual_work_intervals", indexes =
        @Index(name = "idx_actual_work_intervals_owner_date", columnList = "user_id, work_date, start_time, id"))
public class ActualWorkInterval {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser owner;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "worked_minutes", nullable = false)
    private int workedMinutes;

    @Column(name = "break_minutes", nullable = false)
    private int breakMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "break_authority", nullable = false, length = 32)
    private WorkBreakAuthority breakAuthority = WorkBreakAuthority.LEGACY_EARLY_TOTAL;

    @OneToMany(
            mappedBy = "actualWorkInterval",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    @OrderBy("position ASC")
    private List<ActualWorkBreakWindow> breakWindows = new ArrayList<>();

    @Column(length = 500)
    private String note;

    /**
     * Absolute historical identity of this factual interval.
     *
     * Null values mean a legacy interval that has not yet been reconstructed
     * from Temporal Work Context.
     */
    @Column(name = "source_timezone", length = 80)
    private String sourceTimezone;

    @Column(name = "start_instant")
    private Instant startInstant;

    @Column(name = "end_instant")
    private Instant endInstant;

    @Column(name = "identity_reconstructed", nullable = false)
    private boolean identityReconstructed;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected ActualWorkInterval() {}
    public ActualWorkInterval(AppUser owner) { this.owner = owner; }

    public Long getId() { return id; }
    public AppUser getOwner() { return owner; }
    public LocalDate getWorkDate() { return workDate; }
    public void setWorkDate(LocalDate workDate) { this.workDate = workDate; }
    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }
    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public int getWorkedMinutes() { return workedMinutes; }
    public void setWorkedMinutes(int workedMinutes) { this.workedMinutes = workedMinutes; }
    public int getBreakMinutes() { return Math.max(0, breakMinutes); }
    public void setBreakMinutes(int breakMinutes) { this.breakMinutes = Math.max(0, breakMinutes); }

    public WorkBreakAuthority getBreakAuthority() {
        return breakAuthority == null
                ? WorkBreakAuthority.LEGACY_EARLY_TOTAL
                : breakAuthority;
    }

    public List<ActualWorkBreakWindow> getBreakWindows() {
        return Collections.unmodifiableList(breakWindows);
    }

    public void captureLegacyBreakMinutes(int breakMinutes) {
        this.breakAuthority = WorkBreakAuthority.LEGACY_EARLY_TOTAL;
        this.breakMinutes = Math.max(0, breakMinutes);
        this.breakWindows.clear();
    }

    public void captureExplicitBreakWindows(
            int breakMinutes,
            List<ActualWorkBreakWindow> windows
    ) {
        List<ActualWorkBreakWindow> safe = windows == null
                ? new ArrayList<>()
                : new ArrayList<>(windows);
        for (ActualWorkBreakWindow window : safe) {
            if (window == null || window.getActualWorkInterval() != this) {
                throw new IllegalArgumentException(
                        "Explicit break snapshot must belong to this actual interval"
                );
            }
        }
        safe.sort(Comparator.comparingInt(ActualWorkBreakWindow::getPosition));

        this.breakAuthority = WorkBreakAuthority.EXPLICIT_WINDOWS;
        this.breakMinutes = Math.max(0, breakMinutes);
        this.breakWindows.clear();
        this.breakWindows.addAll(safe);
    }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public String getSourceTimezone() { return sourceTimezone; }
    public void setSourceTimezone(String sourceTimezone) {
        this.sourceTimezone = sourceTimezone;
    }

    public Instant getStartInstant() { return startInstant; }
    public void setStartInstant(Instant startInstant) {
        this.startInstant = startInstant;
    }

    public Instant getEndInstant() { return endInstant; }
    public void setEndInstant(Instant endInstant) {
        this.endInstant = endInstant;
    }

    public boolean isIdentityReconstructed() {
        return identityReconstructed;
    }

    public void setIdentityReconstructed(boolean identityReconstructed) {
        this.identityReconstructed = identityReconstructed;
    }

    public boolean hasAbsoluteIdentity() {
        return sourceTimezone != null
                && !sourceTimezone.isBlank()
                && startInstant != null
                && endInstant != null;
    }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    @PrePersist @PreUpdate
    void touch() { updatedAt = Instant.now(); }
}
