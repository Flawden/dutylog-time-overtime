package ru.daniil.shifts.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TaskPriority priority = TaskPriority.NORMAL;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "due_time")
    private LocalTime dueTime;

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
    public TaskPriority getPriority() { return priority; }
    public void setPriority(TaskPriority priority) { this.priority = priority; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    public LocalTime getDueTime() { return dueTime; }
    public void setDueTime(LocalTime dueTime) { this.dueTime = dueTime; }
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
