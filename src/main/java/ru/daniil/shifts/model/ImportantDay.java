package ru.daniil.shifts.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Important calendar entity.
 *
 * <p>The historical class/table/API name remains {@code ImportantDay} for
 * compatibility, while the product model now supports a floating important
 * date, a timed event and a multi-day period.</p>
 */
@Entity
@Table(name = "important_days", indexes = {
        @Index(name = "idx_important_days_user_date", columnList = "user_id, event_date"),
        @Index(name = "idx_important_days_user_end_date", columnList = "user_id, end_date"),
        @Index(name = "idx_important_days_user_start_instant", columnList = "user_id, start_instant")
})
public class ImportantDay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser owner;

    @Column(nullable = false, length = 120)
    private String title;

    /** Floating/source-local start date. Kept under the legacy column name. */
    @Column(name = "event_date", nullable = false)
    private LocalDate date;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 24)
    private ImportantEventType eventType = ImportantEventType.IMPORTANT_DATE;

    @Column(name = "all_day", nullable = false)
    private boolean allDay = true;

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    /** Canonical base occurrence instants for timed entities. */
    @Column(name = "start_instant")
    private Instant startInstant;

    @Column(name = "end_instant")
    private Instant endInstant;

    /** IANA timezone used by the author when entering local dates/times. */
    @Column(name = "source_timezone", length = 80)
    private String sourceTimezone;

    @Column(length = 240)
    private String place;

    @Column(columnDefinition = "text")
    private String description;

    @Column(length = 32)
    private String icon;

    @Column(length = 80)
    private String category;

    /** Sorted comma-separated minute offsets before the event start. */
    @Column(name = "reminder_offsets", length = 240)
    private String reminderOffsets;

    @Enumerated(EnumType.STRING)
    @Column(name = "repeat_mode", nullable = false, length = 20)
    private RepeatMode repeatMode = RepeatMode.NONE;

    @Column(nullable = false, length = 7)
    private String color = "#F5B841";

    protected ImportantDay() {}

    public ImportantDay(AppUser owner, String title, LocalDate date, RepeatMode repeatMode, String color) {
        this.owner = owner;
        this.title = title;
        this.date = date;
        this.repeatMode = repeatMode == null ? RepeatMode.NONE : repeatMode;
        this.color = color;
    }

    public Long getId() { return id; }
    public AppUser getOwner() { return owner; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public ImportantEventType getEventType() { return eventType == null ? ImportantEventType.IMPORTANT_DATE : eventType; }
    public void setEventType(ImportantEventType eventType) { this.eventType = eventType == null ? ImportantEventType.IMPORTANT_DATE : eventType; }
    public boolean isAllDay() { return allDay; }
    public void setAllDay(boolean allDay) { this.allDay = allDay; }
    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }
    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }
    public Instant getStartInstant() { return startInstant; }
    public void setStartInstant(Instant startInstant) { this.startInstant = startInstant; }
    public Instant getEndInstant() { return endInstant; }
    public void setEndInstant(Instant endInstant) { this.endInstant = endInstant; }
    public String getSourceTimezone() { return sourceTimezone; }
    public void setSourceTimezone(String sourceTimezone) { this.sourceTimezone = sourceTimezone; }
    public String getPlace() { return place; }
    public void setPlace(String place) { this.place = place; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getReminderOffsets() { return reminderOffsets; }
    public void setReminderOffsets(String reminderOffsets) { this.reminderOffsets = reminderOffsets; }
    public RepeatMode getRepeatMode() { return repeatMode; }
    public void setRepeatMode(RepeatMode repeatMode) { this.repeatMode = repeatMode == null ? RepeatMode.NONE : repeatMode; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
}
