package ru.daniil.shifts.model;

import jakarta.persistence.*;

/** One ordered shift inside a repeating schedule template. */
@Entity
@Table(name = "schedule_template_steps",
        uniqueConstraints = @UniqueConstraint(columnNames = {"template_id", "position"}))
public class ScheduleTemplateStep {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id", nullable = false)
    private ScheduleTemplate template;

    @Column(nullable = false)
    private int position;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "shift_type_id", nullable = false)
    private ShiftType shiftType;

    protected ScheduleTemplateStep() {}

    public ScheduleTemplateStep(ScheduleTemplate template, int position, ShiftType shiftType) {
        this.template = template;
        this.position = position;
        this.shiftType = shiftType;
    }

    public Long getId() { return id; }
    public ScheduleTemplate getTemplate() { return template; }
    public int getPosition() { return position; }
    public ShiftType getShiftType() { return shiftType; }
}
