package ru.daniil.shifts.model;

import jakarta.persistence.*;
import java.time.LocalDate;

/**
 * Запись на конкретную дату: назначенная смена (может отсутствовать)
 * и заметка в Markdown (может быть пустой).
 * Если нет ни смены, ни заметки — запись удаляется целиком.
 */
@Entity
@Table(name = "day_entries", uniqueConstraints = @UniqueConstraint(columnNames = "entry_date"))
public class DayEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entry_date", nullable = false)
    private LocalDate date;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "shift_type_id")
    private ShiftType shiftType; // может быть null

    @Lob
    @Column(name = "note")
    private String note; // Markdown, может быть null/пустой

    protected DayEntry() {} // для JPA

    public DayEntry(LocalDate date) {
        this.date = date;
    }

    public Long getId() { return id; }
    public LocalDate getDate() { return date; }
    public ShiftType getShiftType() { return shiftType; }
    public void setShiftType(ShiftType shiftType) { this.shiftType = shiftType; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    /** Пустая запись не имеет смысла и должна удаляться. */
    public boolean isEmpty() {
        return shiftType == null && (note == null || note.isBlank());
    }
}
