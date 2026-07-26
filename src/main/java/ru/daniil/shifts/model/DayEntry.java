package ru.daniil.shifts.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Instant;

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

    /** Immutable absolute identity of this concrete dated shift occurrence. */
    @Column(name = "shift_start_instant")
    private Instant shiftStartInstant;

    @Column(name = "shift_end_instant")
    private Instant shiftEndInstant;

    /** IANA zone in which the occurrence was originally assigned. */
    @Column(name = "shift_source_timezone", length = 80)
    private String shiftSourceTimezone;

    @Column(name = "shift_source_date")
    private LocalDate shiftSourceDate;

    @Column(name = "shift_source_start_time")
    private LocalTime shiftSourceStartTime;

    @Column(name = "shift_source_end_time")
    private LocalTime shiftSourceEndTime;

    @Column(name = "shift_break_minutes")
    private Integer shiftBreakMinutes;

    @Column(name = "shift_net_minutes")
    private Long shiftNetMinutes;

    @Column(name = "overtime_hours", nullable = false)
    private Double overtimeHours = 0.0;

    @Column(name = "time_off_hours", nullable = false)
    private Double timeOffHours = 0.0;

    @Column(name = "note", columnDefinition = "text")
    private String note; // Markdown, может быть null/пустой

    /** Лёгкий визуальный маркер дня: Unicode emoji/короткая Unicode-строка, без картинок. */
    @Column(name = "day_emoji", length = 32)
    private String dayEmoji;

    /** Optimistic concurrency token used by Android offline sync. */
    @Version
    @Column(name = "row_version", nullable = false)
    private Long rowVersion = 0L;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

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
    public Instant getShiftStartInstant() { return shiftStartInstant; }
    public Instant getShiftEndInstant() { return shiftEndInstant; }
    public String getShiftSourceTimezone() { return shiftSourceTimezone; }
    public LocalDate getShiftSourceDate() { return shiftSourceDate; }
    public LocalTime getShiftSourceStartTime() { return shiftSourceStartTime; }
    public LocalTime getShiftSourceEndTime() { return shiftSourceEndTime; }
    public int getShiftBreakMinutes() { return shiftBreakMinutes == null ? 0 : Math.max(0, shiftBreakMinutes); }
    public long getShiftNetMinutes() { return shiftNetMinutes == null ? 0L : Math.max(0L, shiftNetMinutes); }

    public boolean hasShiftOccurrenceSnapshot() {
        return shiftStartInstant != null
                && shiftEndInstant != null
                && shiftSourceTimezone != null
                && !shiftSourceTimezone.isBlank();
    }

    public void captureShiftOccurrence(Instant startInstant,
                                       Instant endInstant,
                                       String sourceTimezone,
                                       LocalDate sourceDate,
                                       LocalTime sourceStartTime,
                                       LocalTime sourceEndTime,
                                       int breakMinutes,
                                       long netMinutes) {
        this.shiftStartInstant = startInstant;
        this.shiftEndInstant = endInstant;
        this.shiftSourceTimezone = sourceTimezone;
        this.shiftSourceDate = sourceDate;
        this.shiftSourceStartTime = sourceStartTime;
        this.shiftSourceEndTime = sourceEndTime;
        this.shiftBreakMinutes = Math.max(0, breakMinutes);
        this.shiftNetMinutes = Math.max(0L, netMinutes);
    }

    public void clearShiftOccurrence() {
        this.shiftStartInstant = null;
        this.shiftEndInstant = null;
        this.shiftSourceTimezone = null;
        this.shiftSourceDate = null;
        this.shiftSourceStartTime = null;
        this.shiftSourceEndTime = null;
        this.shiftBreakMinutes = null;
        this.shiftNetMinutes = null;
    }
    public double getOvertimeHours() { return overtimeHours == null ? 0.0 : overtimeHours; }
    public void setOvertimeHours(double overtimeHours) { this.overtimeHours = Math.max(0.0, overtimeHours); }
    public double getTimeOffHours() { return timeOffHours == null ? 0.0 : timeOffHours; }
    public void setTimeOffHours(double timeOffHours) { this.timeOffHours = Math.max(0.0, timeOffHours); }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public String getDayEmoji() { return dayEmoji; }
    public void setDayEmoji(String dayEmoji) { this.dayEmoji = dayEmoji; }
    public long getRowVersion() { return rowVersion == null ? 0L : rowVersion; }

    /**
     * API sync version. Version 0 is reserved for a row that does not exist;
     * persisted rows therefore start at 1 even though Hibernate stores 0 for
     * the first @Version value.
     */
    public long getSyncVersion() { return getRowVersion() + 1L; }

    public Instant getUpdatedAt() { return updatedAt; }

    @PrePersist
    @PreUpdate
    void touchUpdatedAt() {
        updatedAt = Instant.now();
    }

    /** Пустая запись не имеет смысла и должна удаляться. */
    public boolean isEmpty() {
        return shiftType == null
                && getOvertimeHours() <= 0.00001
                && getTimeOffHours() <= 0.00001
                && (note == null || note.isBlank())
                && (dayEmoji == null || dayEmoji.isBlank());
    }
}
