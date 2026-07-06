package ru.daniil.shifts.model;

import jakarta.persistence.*;
import java.time.LocalDate;

/**
 * Запись на конкретную дату: назначенная смена (может отсутствовать)
 * и заметка в Markdown (может быть пустой).
 * Если нет ни смены, ни заметки — запись удаляется целиком.
 */
@Entity
@Table(name = "day_entries",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "entry_date"}))
public class DayEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Владелец записи. Один и тот же день у разных пользователей — разные записи. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser owner;

    @Column(name = "entry_date", nullable = false)
    private LocalDate date;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "shift_type_id")
    private ShiftType shiftType; // может быть null

    @Lob
    @Column(name = "note")
    private String note; // Markdown, может быть null/пустой

    protected DayEntry() {} // для JPA

    public DayEntry(AppUser owner, LocalDate date) {
        this.owner = owner;
        this.date = date;
    }

    public Long getId() { return id; }
    public AppUser getOwner() { return owner; }
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
