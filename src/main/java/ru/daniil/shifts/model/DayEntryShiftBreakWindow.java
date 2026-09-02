package ru.daniil.shifts.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDateTime;

@Entity
@Table(name = "day_entry_shift_break_windows")
public class DayEntryShiftBreakWindow {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "day_entry_id", nullable = false)
    private DayEntry dayEntry;

    @Column(nullable = false)
    private int position;

    @Column(name = "source_start_local", nullable = false)
    private LocalDateTime sourceStartLocal;

    @Column(name = "source_end_local", nullable = false)
    private LocalDateTime sourceEndLocal;

    @Column(name = "start_instant", nullable = false)
    private Instant startInstant;

    @Column(name = "end_instant", nullable = false)
    private Instant endInstant;

    @Column(name = "source_timezone", nullable = false, length = 80)
    private String sourceTimezone;

    protected DayEntryShiftBreakWindow() {}

    public DayEntryShiftBreakWindow(
            DayEntry dayEntry,
            int position,
            LocalDateTime sourceStartLocal,
            LocalDateTime sourceEndLocal,
            Instant startInstant,
            Instant endInstant,
            String sourceTimezone
    ) {
        if (dayEntry == null) throw new IllegalArgumentException("Day entry is required");
        if (position < 0) throw new IllegalArgumentException("Break position cannot be negative");
        if (sourceStartLocal == null || sourceEndLocal == null
                || !sourceEndLocal.isAfter(sourceStartLocal)) {
            throw new IllegalArgumentException("Positive source-local break identity is required");
        }
        if (startInstant == null || endInstant == null || !endInstant.isAfter(startInstant)) {
            throw new IllegalArgumentException("Positive absolute break identity is required");
        }
        if (sourceTimezone == null || sourceTimezone.isBlank()) {
            throw new IllegalArgumentException("Break source timezone is required");
        }
        this.dayEntry = dayEntry;
        this.position = position;
        this.sourceStartLocal = sourceStartLocal;
        this.sourceEndLocal = sourceEndLocal;
        this.startInstant = startInstant;
        this.endInstant = endInstant;
        this.sourceTimezone = sourceTimezone;
    }

    public Long getId() { return id; }
    public DayEntry getDayEntry() { return dayEntry; }
    public int getPosition() { return position; }
    public LocalDateTime getSourceStartLocal() { return sourceStartLocal; }
    public LocalDateTime getSourceEndLocal() { return sourceEndLocal; }
    public Instant getStartInstant() { return startInstant; }
    public Instant getEndInstant() { return endInstant; }
    public String getSourceTimezone() { return sourceTimezone; }
}
