package ru.daniil.shifts.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDateTime;

/** Frozen exact unpaid-break evidence for one explicit factual work interval. */
@Entity
@Table(name = "actual_work_break_windows")
public class ActualWorkBreakWindow {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actual_work_interval_id", nullable = false)
    private ActualWorkInterval actualWorkInterval;

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

    protected ActualWorkBreakWindow() {}

    public ActualWorkBreakWindow(
            ActualWorkInterval actualWorkInterval,
            int position,
            LocalDateTime sourceStartLocal,
            LocalDateTime sourceEndLocal,
            Instant startInstant,
            Instant endInstant,
            String sourceTimezone
    ) {
        if (actualWorkInterval == null) {
            throw new IllegalArgumentException("Actual work interval is required");
        }
        if (position < 0) {
            throw new IllegalArgumentException("Break position cannot be negative");
        }
        if (sourceStartLocal == null || sourceEndLocal == null
                || !sourceEndLocal.isAfter(sourceStartLocal)) {
            throw new IllegalArgumentException(
                    "Positive source-local break identity is required"
            );
        }
        this.actualWorkInterval = actualWorkInterval;
        this.position = position;
        this.sourceStartLocal = sourceStartLocal;
        this.sourceEndLocal = sourceEndLocal;
        reconstructAbsoluteIdentity(startInstant, endInstant, sourceTimezone);
    }

    public Long getId() { return id; }
    public ActualWorkInterval getActualWorkInterval() { return actualWorkInterval; }
    public int getPosition() { return position; }
    public LocalDateTime getSourceStartLocal() { return sourceStartLocal; }
    public LocalDateTime getSourceEndLocal() { return sourceEndLocal; }
    public Instant getStartInstant() { return startInstant; }
    public Instant getEndInstant() { return endInstant; }
    public String getSourceTimezone() { return sourceTimezone; }

    /**
     * Rebuild only the absolute projection after an intentional historical
     * Work Timezone correction. Source-local evidence remains the audit anchor.
     */
    public void reconstructAbsoluteIdentity(
            Instant startInstant,
            Instant endInstant,
            String sourceTimezone
    ) {
        if (startInstant == null || endInstant == null || !endInstant.isAfter(startInstant)) {
            throw new IllegalArgumentException("Positive absolute break identity is required");
        }
        if (sourceTimezone == null || sourceTimezone.isBlank()) {
            throw new IllegalArgumentException("Break source timezone is required");
        }
        this.startInstant = startInstant;
        this.endInstant = endInstant;
        this.sourceTimezone = sourceTimezone;
    }
}
