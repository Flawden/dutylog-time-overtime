package ru.daniil.shifts.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Отдельная задача на конкретный день.
 * Это не Markdown-строка в заметке, а нормальная сущность: её можно чекать,
 * показывать индикатором на календаре, синхронизировать с Android и Telegram.
 */
@Entity
@Table(name = "day_tasks", indexes = {
        @Index(name = "idx_day_tasks_user_date", columnList = "user_id, task_date")
})
public class DayTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser owner;

    @Column(name = "task_date", nullable = false)
    private LocalDate date;

    @Column(nullable = false, length = 500)
    private String text;

    @Column(length = 4000)
    private String description;

    @Column(nullable = false)
    private boolean done = false;

    @Column(length = 80)
    private String category;

    @Column(length = 80)
    private String project;

    @Column(name = "all_day", nullable = false)
    private boolean allDay = true;

    @Column(name = "scheduled_start_date")
    private LocalDate scheduledStartDate;

    @Column(name = "scheduled_start_time")
    private LocalTime scheduledStartTime;

    @Column(name = "scheduled_end_date")
    private LocalDate scheduledEndDate;

    @Column(name = "scheduled_end_time")
    private LocalTime scheduledEndTime;

    @Column(name = "scheduled_start_instant")
    private Instant scheduledStartInstant;

    @Column(name = "scheduled_end_instant")
    private Instant scheduledEndInstant;

    @Column(name = "scheduled_source_timezone", length = 80)
    private String scheduledSourceTimezone;

    @Column(name = "scheduled_source_start_date")
    private LocalDate scheduledSourceStartDate;

    @Column(name = "scheduled_source_start_time")
    private LocalTime scheduledSourceStartTime;

    @Column(name = "scheduled_source_end_date")
    private LocalDate scheduledSourceEndDate;

    @Column(name = "scheduled_source_end_time")
    private LocalTime scheduledSourceEndTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TaskPriority priority = TaskPriority.NORMAL;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "due_time")
    private LocalTime dueTime;

    /**
     * Absolute deadline for tasks that have both a due date and a due time.
     *
     * <p>The legacy {@code dueDate}/{@code dueTime} columns remain the current
     * canonical-timezone projection used by existing API clients and indexes.
     * This instant is the source of truth across timezone changes.</p>
     */
    @Column(name = "due_instant")
    private Instant dueInstant;

    @Column(name = "due_source_timezone", length = 80)
    private String dueSourceTimezone;

    @Column(name = "due_source_date")
    private LocalDate dueSourceDate;

    @Column(name = "due_source_time")
    private LocalTime dueSourceTime;

    @Column(name = "reminder_enabled", nullable = false)
    private boolean reminderEnabled = false;

    @Column(name = "reminder_minutes_before")
    private Integer reminderMinutesBefore;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "day_task_tags", joinColumns = @JoinColumn(name = "task_id"))
    @OrderColumn(name = "tag_order", nullable = false)
    @Column(name = "tag", nullable = false, length = 40)
    private List<String> tags = new ArrayList<>();

    @OneToMany(mappedBy = "task", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("sortOrder ASC, createdAt ASC, id ASC")
    private List<TaskSubtask> subtasks = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected DayTask() {}

    public DayTask(AppUser owner, LocalDate date, String text) {
        this.owner = owner;
        this.date = date;
        this.text = text;
    }

    public Long getId() { return id; }
    public AppUser getOwner() { return owner; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public boolean isDone() { return done; }
    public void setDone(boolean done) { this.done = done; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getProject() { return project; }
    public void setProject(String project) { this.project = project; }
    public boolean isAllDay() { return allDay; }
    public void setAllDay(boolean allDay) { this.allDay = allDay; }
    public LocalDate getScheduledStartDate() { return scheduledStartDate; }
    public void setScheduledStartDate(LocalDate scheduledStartDate) { this.scheduledStartDate = scheduledStartDate; }
    public LocalTime getScheduledStartTime() { return scheduledStartTime; }
    public void setScheduledStartTime(LocalTime scheduledStartTime) { this.scheduledStartTime = scheduledStartTime; }
    public LocalDate getScheduledEndDate() { return scheduledEndDate; }
    public void setScheduledEndDate(LocalDate scheduledEndDate) { this.scheduledEndDate = scheduledEndDate; }
    public LocalTime getScheduledEndTime() { return scheduledEndTime; }
    public void setScheduledEndTime(LocalTime scheduledEndTime) { this.scheduledEndTime = scheduledEndTime; }
    public Instant getScheduledStartInstant() { return scheduledStartInstant; }
    public void setScheduledStartInstant(Instant scheduledStartInstant) { this.scheduledStartInstant = scheduledStartInstant; }
    public Instant getScheduledEndInstant() { return scheduledEndInstant; }
    public void setScheduledEndInstant(Instant scheduledEndInstant) { this.scheduledEndInstant = scheduledEndInstant; }
    public String getScheduledSourceTimezone() { return scheduledSourceTimezone; }
    public void setScheduledSourceTimezone(String scheduledSourceTimezone) { this.scheduledSourceTimezone = scheduledSourceTimezone; }
    public LocalDate getScheduledSourceStartDate() { return scheduledSourceStartDate; }
    public void setScheduledSourceStartDate(LocalDate scheduledSourceStartDate) { this.scheduledSourceStartDate = scheduledSourceStartDate; }
    public LocalTime getScheduledSourceStartTime() { return scheduledSourceStartTime; }
    public void setScheduledSourceStartTime(LocalTime scheduledSourceStartTime) { this.scheduledSourceStartTime = scheduledSourceStartTime; }
    public LocalDate getScheduledSourceEndDate() { return scheduledSourceEndDate; }
    public void setScheduledSourceEndDate(LocalDate scheduledSourceEndDate) { this.scheduledSourceEndDate = scheduledSourceEndDate; }
    public LocalTime getScheduledSourceEndTime() { return scheduledSourceEndTime; }
    public void setScheduledSourceEndTime(LocalTime scheduledSourceEndTime) { this.scheduledSourceEndTime = scheduledSourceEndTime; }
    public boolean hasScheduledStart() { return scheduledStartInstant != null; }
    public boolean hasScheduledEnd() { return scheduledEndInstant != null; }
    public TaskPriority getPriority() { return priority; }
    public void setPriority(TaskPriority priority) { this.priority = priority; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    public LocalTime getDueTime() { return dueTime; }
    public void setDueTime(LocalTime dueTime) { this.dueTime = dueTime; }
    public Instant getDueInstant() { return dueInstant; }
    public void setDueInstant(Instant dueInstant) { this.dueInstant = dueInstant; }
    public String getDueSourceTimezone() { return dueSourceTimezone; }
    public void setDueSourceTimezone(String dueSourceTimezone) { this.dueSourceTimezone = dueSourceTimezone; }
    public LocalDate getDueSourceDate() { return dueSourceDate; }
    public void setDueSourceDate(LocalDate dueSourceDate) { this.dueSourceDate = dueSourceDate; }
    public LocalTime getDueSourceTime() { return dueSourceTime; }
    public void setDueSourceTime(LocalTime dueSourceTime) { this.dueSourceTime = dueSourceTime; }
    public boolean hasAbsoluteDeadline() { return dueInstant != null; }
    public boolean isReminderEnabled() { return reminderEnabled; }
    public void setReminderEnabled(boolean reminderEnabled) { this.reminderEnabled = reminderEnabled; }
    public Integer getReminderMinutesBefore() { return reminderMinutesBefore; }
    public void setReminderMinutesBefore(Integer reminderMinutesBefore) { this.reminderMinutesBefore = reminderMinutesBefore; }
    public List<String> getTags() { return tags; }
    public void setTags(Collection<String> tags) {
        this.tags.clear();
        if (tags != null) this.tags.addAll(tags);
    }
    public List<TaskSubtask> getSubtasks() { return subtasks; }
    public void addSubtask(TaskSubtask subtask) {
        if (subtask == null) return;
        subtask.setTask(this);
        subtasks.add(subtask);
    }
    public void removeSubtask(TaskSubtask subtask) {
        if (subtask == null) return;
        subtasks.remove(subtask);
    }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
