package ru.daniil.shifts.model;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One-level checklist item owned by a parent task.
 * Subtasks are intentionally one-level checklist items and are not recursive.
 */
@Entity
@Table(name = "task_subtasks", indexes = {
        @Index(name = "idx_task_subtasks_task_order", columnList = "task_id, sort_order, id")
})
public class TaskSubtask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false)
    private DayTask task;

    @Column(nullable = false, length = 300)
    private String text;

    @Column(nullable = false)
    private boolean done = false;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected TaskSubtask() {}

    public TaskSubtask(DayTask task, String text, int sortOrder) {
        this.task = task;
        this.text = text;
        this.sortOrder = sortOrder;
    }

    public Long getId() { return id; }
    public DayTask getTask() { return task; }
    public void setTask(DayTask task) { this.task = task; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public boolean isDone() { return done; }
    public void setDone(boolean done) { this.done = done; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
