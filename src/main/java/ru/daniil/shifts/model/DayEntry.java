package ru.daniil.shifts.model;

import jakarta.persistence.*;
import java.time.LocalDate;

/**
 * Запись на конкретную дату: назначенная смена, заметка в Markdown,
 * дополнительные часы переработки и часы использованного отгула.
 *
 * Важная мысль по переработкам:
 * - shiftType хранит обычную/плановую смену дня;
 * - overtimeHours — дополнительные часы сверх обычной смены, например ППР после работы;
 * - timeOffHours — часы, которые пользователь списал как отгул/компенсацию.
 *
 * Баланс переработки за период считается как sum(overtimeHours) - sum(timeOffHours).
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

    @Column(name = "overtime_hours", nullable = false)
    private Double overtimeHours = 0.0;

    @Column(name = "time_off_hours", nullable = false)
    private Double timeOffHours = 0.0;

    @Column(name = "note", columnDefinition = "text")
    private String note; // Markdown, может быть null/пустой

    /** Лёгкий визуальный маркер дня: Unicode emoji/короткая Unicode-строка, без картинок. */
    @Column(name = "day_emoji", length = 32)
    private String dayEmoji;

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
    public double getOvertimeHours() { return overtimeHours == null ? 0.0 : overtimeHours; }
    public void setOvertimeHours(double overtimeHours) { this.overtimeHours = Math.max(0.0, overtimeHours); }
    public double getTimeOffHours() { return timeOffHours == null ? 0.0 : timeOffHours; }
    public void setTimeOffHours(double timeOffHours) { this.timeOffHours = Math.max(0.0, timeOffHours); }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public String getDayEmoji() { return dayEmoji; }
    public void setDayEmoji(String dayEmoji) { this.dayEmoji = dayEmoji; }

    /** Пустая запись не имеет смысла и должна удаляться. */
    public boolean isEmpty() {
        return shiftType == null
                && getOvertimeHours() <= 0.00001
                && getTimeOffHours() <= 0.00001
                && (note == null || note.isBlank())
                && (dayEmoji == null || dayEmoji.isBlank());
    }
}
