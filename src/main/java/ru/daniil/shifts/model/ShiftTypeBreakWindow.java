package ru.daniil.shifts.model;

import jakarta.persistence.*;

@Entity
@Table(name = "shift_type_break_windows")
public class ShiftTypeBreakWindow {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shift_type_id", nullable = false)
    private ShiftType shiftType;

    @Column(nullable = false)
    private int position;

    @Column(name = "start_offset_minutes", nullable = false)
    private int startOffsetMinutes;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    protected ShiftTypeBreakWindow() {}

    public ShiftTypeBreakWindow(
            ShiftType shiftType,
            int position,
            int startOffsetMinutes,
            int durationMinutes
    ) {
        if (shiftType == null) throw new IllegalArgumentException("Shift type is required");
        if (position < 0) throw new IllegalArgumentException("Break position cannot be negative");
        if (startOffsetMinutes < 0 || startOffsetMinutes >= 1440) {
            throw new IllegalArgumentException("Break offset must be from 0 to 1439 minutes");
        }
        if (durationMinutes <= 0 || durationMinutes > 1440) {
            throw new IllegalArgumentException("Break duration must be from 1 to 1440 minutes");
        }
        if ((long) startOffsetMinutes + durationMinutes > 1440L) {
            throw new IllegalArgumentException("Break window must fit inside a <=24h wall-clock span");
        }
        this.shiftType = shiftType;
        this.position = position;
        this.startOffsetMinutes = startOffsetMinutes;
        this.durationMinutes = durationMinutes;
    }

    public Long getId() { return id; }
    public ShiftType getShiftType() { return shiftType; }
    public int getPosition() { return position; }
    public int getStartOffsetMinutes() { return startOffsetMinutes; }
    public int getDurationMinutes() { return durationMinutes; }
}
